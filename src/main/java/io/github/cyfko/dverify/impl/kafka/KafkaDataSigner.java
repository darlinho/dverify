package io.github.cyfko.dverify.impl.kafka;

import io.github.cyfko.dverify.DataSigner;
import io.github.cyfko.dverify.exceptions.JsonEncodingException;
import io.github.cyfko.dverify.util.JacksonUtil;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class KafkaDataSigner implements DataSigner {
    private static final org.slf4j.Logger log = org. slf4j. LoggerFactory. getLogger(KafkaDataSigner.class);
    private final ScheduledExecutorService scheduler;
    private final KafkaProducer<String,String> producer;
    private long lastExecutionTime = 0;
    private KeyPair keyPair;
    private Properties properties;

    private static Properties defaultKafkaProperties() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Constant.KAFKA_BOOSTRAP_SERVERS);
        PropertiesUtil.addUniqueKafkaProperties(properties);
        return properties;
    }

    /**
     * Construct an instance of the <code>KafkaDataSigner</code> mixing environment properties and the provided
     * <code>Properties</code>.
     * Note that some properties will be discarded if they don't match to the way KafkaDataSigner works.
     *
     * @implNote Recognized properties are those defined by {@link org.apache.kafka.clients.producer.ProducerConfig} and
     * {@link io.github.cyfko.dverify.impl.kafka.SignerConfig} classes.
     * <ul>
     *    <li><code>{@link org.apache.kafka.clients.producer.ProducerConfig}.BOOTSTRAP_SERVERS_CONFIG</code> <sup><small>[REQUIRED]</small></sup> as specified by Kafka</li>
     * </ul>
     *
     * @param props initial Properties to use to construct the KafkaDataSigner.
     * @throws IllegalArgumentException if any of the <strong><small>[REQUIRED]</small></strong> key is not found in <code>props</code>.
     */
    public static KafkaDataSigner of(Properties props){
        if (!props.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)){
            throw new IllegalArgumentException("missing properties when trying to construct KafkaDataSigner");
        }

        Properties usedProps = new Properties();
        usedProps.putAll(props);
        PropertiesUtil.addUniqueKafkaProperties(usedProps);
        return new KafkaDataSigner(usedProps);
    }

    /**
     * Construct a KafkaDataSigner with defaults properties.
     */
    public KafkaDataSigner() {
        this(defaultKafkaProperties());
    }

    /**
     * Construct a KafkaDataSigner using the provided `boostrapServers`.
     * @param boostrapServers the value to assign to the property `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG`.
     */
    public KafkaDataSigner(String boostrapServers) {
        this(PropertiesUtil.of(defaultKafkaProperties(), boostrapServers));
    }

    private KafkaDataSigner(Properties props) {
        this.properties = props;
        this.producer = new KafkaProducer<>(props);

        // generate the first KeyPair
        generatedKeysPair();

        // Schedule the task to run every minute
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::generatedKeysPair, 0, Constant.KEYS_ROTATION_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String sign(Object data, Duration duration) throws JsonEncodingException {
        if (data == null || duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("data should not be null and the duration should be positive");
        }

        // Calculate issued time and expiration time
        Instant fromNow = Instant.now();
        Date issuedAt = Date.from(fromNow);
        Date expirationDate = Date.from(fromNow.plus(duration));

        try {
            final String publicKeyId = UUID.randomUUID().toString();
            final String token = Jwts.builder()
                    .subject(publicKeyId)
                    .claim("data", JacksonUtil.toJson(data))
                    .issuedAt(issuedAt)
                    .expiration(expirationDate)
                    .signWith(keyPair.getPrivate())
                    .compact();
            return propagatePublicKey(publicKeyId, token);
        } catch (Exception e){
            throw new JsonEncodingException(e.getMessage());
        }
    }

    private void generatedKeysPair() {
        long currentTime = System.currentTimeMillis();

        // Check if the last execution was within the last KEYS_ROTATION_RATE_MINUTES
        if (currentTime - lastExecutionTime >= Constant.KEYS_ROTATION_MINUTES * 60 * 1000 || lastExecutionTime == 0) {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Constant.ASYMMETRIC_KEYPAIR_ALGORITHM);

                keyPair = keyPairGenerator.generateKeyPair();
            } catch (Exception e) {
                log.error("Error generating keys-pair: {}", e.getMessage());
            } finally {
                lastExecutionTime = currentTime;
            }
        }
    }

    /**
     * Send the Kafka event message for which the key is <strong>publicKeyId</strong> and the value is a {@link java.lang.String} that
     * strictly follows the convention:  <code>[token config]</code> <code>:</code> <code>[Base64 RSA public key]</code> <code>:</code> <code>[Base64 variant]</code>
     * @param publicKeyId The RSA public key used to verify the token.
     * @param jwt The JWT token embedding the desired data.
     * @return A token to be used to refer to the desired data. It depends on the value attached to the property {@link io.github.cyfko.dverify.impl.kafka.SignerConfig }<code>.GENERATED_TOKEN_CONFIG</code> .
     */
    private String propagatePublicKey(String publicKeyId, String jwt) {
        final String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        final String tokenConfig = properties.getProperty(SignerConfig.GENERATED_TOKEN_CONFIG);

        String message = switch (tokenConfig){
            case Constant.GENERATED_TOKEN_JWT -> String.format("%s:%s:%s", tokenConfig, encodedPublicKey, "");
            case Constant.GENERATED_TOKEN_IDENTITY -> String.format("%s:%s:%s", tokenConfig, encodedPublicKey, jwt);
            default -> throw new IllegalStateException("Unexpected value: " + tokenConfig);
        };

        producer.send(new ProducerRecord<>(properties.getProperty(SignerConfig.BROKER_TOPIC_CONFIG), publicKeyId, message), (metadata, exception) -> {
            if (exception == null) {
                log.debug("Public key sent to Kafka successfully with offset: {}", metadata.offset());
            } else {
                log.error("Error sending the public key to kafka: {}", metadata.offset());
            }
        });

        return switch (tokenConfig){
            case Constant.GENERATED_TOKEN_JWT -> jwt;
            case Constant.GENERATED_TOKEN_IDENTITY -> publicKeyId;
            default -> throw new IllegalStateException("Unexpected value: " + tokenConfig);
        };
    }
}
