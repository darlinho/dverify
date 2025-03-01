package io.github.cyfko.dverify.impl.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cyfko.dverify.DataVerifier;
import io.github.cyfko.dverify.exceptions.DataExtractionException;
import io.github.cyfko.dverify.util.JacksonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;


public class KafkaDataVerifier implements DataVerifier {
    private static final Logger log = LoggerFactory. getLogger(KafkaDataVerifier.class);
    private static final KeyFactory keyFactory;
    private final Properties properties;
    private final KafkaConsumer<String, String> consumer;
    private final RocksDB db;
    private final Options options;

    static {
        try {
            RocksDB.loadLibrary();
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties defaultKafkaProperties() {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Constant.KAFKA_BOOSTRAP_SERVERS);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.setProperty(VerifierConfig.EMBEDDED_DB_PATH_CONFIG, Constant.EMBEDDED_DATABASE_PATH);
        PropertiesUtil.addUniqueKafkaProperties(props);
        return props;
    }

    /**
     * Construct an instance of the <code>KafkaDataVerifier</code> mixing environment properties and the provided <code>Properties</code>.
     * Note that some properties will be discarded if they don't match to the way KafkaDataVerifier works.
     *
     * @apiNote @apiNote Recognized properties are those defined by {@link org.apache.kafka.clients.consumer.ConsumerConfig} and
     * {@link io.github.cyfko.dverify.impl.kafka.VerifierConfig} classes.
     * <ul>
     *    <li><code>ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG</code> <sup><small>[REQUIRED]</small></sup> as specified by Kafka</li>
     *    <li><code>VerifierConfig.EMBEDDED_DB_PATH</code> for which the associated property value should be a relative path name.</li>
     * </ul>
     *
     * @param props initial Properties to use to construct the KafkaDataVerifier.
     * @throws IllegalArgumentException if any of the <strong><small>[REQUIRED]</small></strong> key is not found in <code>props</code>.
     */
    public static KafkaDataVerifier of(Properties props){
        if (!props.containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)){
            throw new IllegalArgumentException("missing properties when trying to construct KafkaDataVerifier");
        }

        Properties usedProps = new Properties();
        usedProps.putAll(props);
        PropertiesUtil.addUniqueKafkaProperties(usedProps);
        return new KafkaDataVerifier(usedProps);
    }

    /**
     * Construct a KafkaDataVerifier with defaults properties.
     */
    public KafkaDataVerifier() {
        this(defaultKafkaProperties());
    }

    /**
     * Construct a KafkaDataVerifier with the provided `boostrapServers`.
     * @param boostrapServers the value to assign to the property `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG`.
     */
    public KafkaDataVerifier(String boostrapServers) {
        this(PropertiesUtil.of(defaultKafkaProperties(), boostrapServers));
    }

    private KafkaDataVerifier(Properties props) {

        try {
            // configure embedded database
            this.options = new Options().setCreateIfMissing(true);
            this.db = RocksDB.open(options, props.getProperty(VerifierConfig.EMBEDDED_DB_PATH_CONFIG));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Closing RocksDB...");
                closeDB();
            }));

            // configure Kafka
            props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getOrCreateUniqueGroupId());
            this.properties = props;
            this.consumer = new KafkaConsumer<>(properties);
            this.consumer.subscribe(List.of(Constant.KAFKA_TOKEN_VERIFIER_TOPIC));

        } catch (RocksDBException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Unable to initialize KafkaVerifier: Embedded Database could not be opened.");
        }
    }

    @Override
    public <T> T verify(String jwt, Class<T> clazz) throws DataExtractionException {
        String keyId = getKeyId(jwt);

        try {
            Jwt<?, Claims> parsedJwt = Jwts.parser()
                    .verifyWith(getPublicKey(keyId))
                    .build()
                    .parseSignedClaims(jwt);
            final var data = parsedJwt.getPayload().get("data", String.class);
            return JacksonUtil.fromJson(data, clazz);
        } catch (DataExtractionException e) {
            throw e;
        } catch (Exception e){
            throw new DataExtractionException("Failed to extract subject from JWT: " + e.getMessage());
        }
    }

    private String getKeyId(String jwt) {
        try {
            String payloadBase64 = jwt.split("\\.")[1];
            String payloadJson = new String(Base64.getDecoder().decode(payloadBase64));

            // Parse JSON and extract the "sub" field
            JsonNode jsonNode = JacksonUtil.fromJson(payloadJson, JsonNode.class);
            return jsonNode.get("sub").asText();
        } catch (Exception e) {
            throw new DataExtractionException("Failed to extract subject from JWT: " + e.getMessage());
        }
    }

    private PublicKey getPublicKey(String keyId) {
        String base64Key;
        try {
            byte[] bytes = db.get(keyId.getBytes());
            base64Key = bytes != null ? new String(bytes) : null;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new DataExtractionException("An unexpected error occurred when reading the embedded DB: \n" + e.getMessage());
        }

        if (base64Key == null) {
            // Read messages from the broker
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000)); // At most 5 secs to wait for.
            for (var record: records) {
                try {
                    // persist on embedded DB
                    db.put(record.key().getBytes(), record.value().getBytes());
                    if (keyId.equals(record.key())) {
                        base64Key = record.value();
                    }
                } catch (RocksDBException ex) {
                    log.error(ex.getMessage());
                    throw new DataExtractionException(ex.getMessage());
                }
            }
        }

        if (base64Key == null) {
            log.debug("Failed to find public key for keyId: {}", keyId);
            throw new DataExtractionException("Key {" + keyId + "} not found");
        }

        // Decode the Base64 encoded string into a byte array
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        try {
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeDB() {
        if (db != null) {
            db.close(); // Closes database connection
        }
        if (options != null) {
            options.close(); // Closes options
        }
    }

    private String getOrCreateUniqueGroupId() throws RocksDBException {
        byte[] idByte = db.get(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY.getBytes());
        if (idByte == null) {
            idByte = UUID.randomUUID().toString().getBytes();
            db.put(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY.getBytes(), idByte);
        }
        return new String(idByte);
    }
}
