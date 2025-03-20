package io.github.cyfko.dverify.impl.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cyfko.dverify.DataVerifier;
import io.github.cyfko.dverify.exceptions.DataExtractionException;
import io.github.cyfko.dverify.util.JacksonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class KafkaDataVerifier implements DataVerifier {
    private static final Logger log = LoggerFactory. getLogger(KafkaDataVerifier.class);
    private static final KeyFactory keyFactory;
    private final Properties properties;
    private final KafkaConsumer<String, String> consumer;
    private final RocksDB db;
    private final Options options;
    private final ScheduledExecutorService scheduler;

    static {
        try {
            RocksDB.loadLibrary();
            keyFactory = KeyFactory.getInstance(Constant.ASYMMETRIC_KEYPAIR_ALGORITHM);
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
     * @implNote Recognized properties are those defined by {@link org.apache.kafka.clients.consumer.ConsumerConfig} and
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

            // initialize clean task
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            startCustomCleanup(db);

        } catch (RocksDBException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Unable to initialize KafkaVerifier: Embedded Database could not be opened.");
        }
    }

    @Override
    public <T> T verify(String token, Class<T> clazz) throws DataExtractionException {
        String keyId = getKeyId(token);
        try {
            final var data = getClaims(keyId,token).get("data", String.class);
            return JacksonUtil.fromJson(data, clazz);
        } catch (DataExtractionException e) {
            throw e;
        } catch (Exception e){
            throw new DataExtractionException("Failed to extract subject from JWT: " + e.getMessage());
        }
    }

    private String getKeyId(String token) {
        try {
            String payloadBase64 = token.split("\\.")[1];
            String payloadJson = new String(Base64.getDecoder().decode(payloadBase64));

            // Parse JSON and extract the "sub" field
            JsonNode jsonNode = JacksonUtil.fromJson(payloadJson, JsonNode.class);
            return jsonNode.get("sub").asText();
        } catch (IndexOutOfBoundsException e) {
            return token; // The token is not a JWT, maybe it's a UUID
        } catch (Exception e) {
            throw new DataExtractionException("Failed to extract subject from JWT: " + e.getMessage());
        }
    }

    /**
     * Remember: the Kafka event message should be a message where the key is <strong>publicKeyId</strong> and the value is a {@link java.lang.String} that
     * strictly follows the convention: <code>[token config]</code> <code>:</code> <code>[Base64 RSA public key]</code> <code>:</code> <code>[Expiry date seconds]</code> <code>:</code> <code>[Base64 variant]</code>
     * @param keyId The RSA public key used to verify the token.
     * @param token The token embedding the desired data.
     * @return A token to be used to refer to the desired data. It depends on the value attached to the property {@link io.github.cyfko.dverify.impl.kafka.SignerConfig }<code>.GENERATED_TOKEN_CONFIG</code> .
     */
    private Claims getClaims(String keyId, String token) {
        String[] parts = getEventMessageValue(keyId).split(":");
        try {
            byte[] decodedKey = Base64.getDecoder().decode(parts[1]);
            final PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));

            return switch (parts[0]){
                case Constant.GENERATED_TOKEN_JWT -> Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                case Constant.GENERATED_TOKEN_IDENTITY -> Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(parts[3])
                        .getPayload();

                default -> throw new IllegalStateException("Unexpected value token config encountered: " + token);
            };
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private String getEventMessageValue(String keyId) {
        String messageValue;
        try {
            byte[] bytes = db.get(keyId.getBytes());
            messageValue = bytes != null ? new String(bytes) : null;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new DataExtractionException("An unexpected error occurred when reading the embedded DB: \n" + e.getMessage());
        }

        if (messageValue == null) {
            // Read messages from the broker
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000)); // At most 5 secs to wait for.
            for (var record: records) {
                try {
                    // persist on embedded DB
                    db.put(record.key().getBytes(), record.value().getBytes());
                    if (keyId.equals(record.key())) {
                        messageValue = record.value();
                    }
                } catch (RocksDBException ex) {
                    log.error(ex.getMessage());
                    throw new DataExtractionException(ex.getMessage());
                }
            }
        }

        if (messageValue == null) {
            log.debug("Failed to find public key for keyId: {}", keyId);
            throw new DataExtractionException("Key {" + keyId + "} not found");
        }
        return messageValue;
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

    /**
     * Starts a background thread that periodically deletes expired entries based on a custom predicate.
     */
    private void startCustomCleanup(RocksDB db) {
        final long INTERVAL_MINUTES = Long.parseLong(properties.getProperty(VerifierConfig.CLEANUP_INTERVAL_CONFIG));
        scheduler.scheduleAtFixedRate(() -> {
            try (RocksIterator iterator = db.newIterator()) {
                long now = System.currentTimeMillis();

                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    String key = new String(iterator.key(), StandardCharsets.UTF_8);
                    String value = new String(iterator.value(), StandardCharsets.UTF_8);
                    if (key.equals(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY)) continue;

                    // Extract timestamp from stored value
                    long timestamp = Long.parseLong(value.split(":")[2]);

                    // Custom predicate: Delete if expired AND value contains "value1"
                    if ((now - timestamp) > 0) {
                        db.delete(iterator.key());
                        log.info("Deleted key {} after expiration.", key);
                    }
                }
            } catch (RocksDBException e) {
                e.printStackTrace();
            } catch (Exception e) {
                log.error("Error when running cleanup task: \n{}.", e.getMessage());
            }
        }, 0, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
}
