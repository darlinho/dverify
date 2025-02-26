package io.github.cyfko.disver.impl;

import io.github.cyfko.disver.DataSigner;
import io.github.cyfko.disver.exceptions.JsonEncodingException;
import io.github.cyfko.disver.util.JacksonUtil;
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

    private static Properties initProperties() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Constant.KAFKA_BOOSTRAP_SERVERS);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        return properties;
    }

    /// Construct a KafkaDataSigner with environment properties and defaults ones.
    public KafkaDataSigner() {
        this(initProperties());
    }

    /// Construct a KafkaDataSigner with environment properties (and defaults ones) using the provided `boostrapServers`.
    /// @param boostrapServers the value to assign to the property `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG`.
    public KafkaDataSigner(String boostrapServers) {
        this(PropertiesUtil.of(initProperties(), boostrapServers));
    }

    private KafkaDataSigner(Properties props) {
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
            propagatePublicKey(publicKeyId);
            return token;
        } catch (Exception e){
            throw new JsonEncodingException(e.getMessage());
        }
    }

    private void generatedKeysPair() {
        long currentTime = System.currentTimeMillis();

        // Check if the last execution was within the last KEYS_ROTATION_RATE_MINUTES
        if (currentTime - lastExecutionTime >= Constant.KEYS_ROTATION_MINUTES * 60 * 1000 || lastExecutionTime == 0) {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                keyPair = keyPairGenerator.generateKeyPair();
            } catch (Exception e) {
                log.error("Error generating keys-pair: {}", e.getMessage());
            } finally {
                lastExecutionTime = currentTime;
            }
        }
    }

    private void propagatePublicKey(String publicKeyId) {
        String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        producer.send(new ProducerRecord<>(Constant.KAFKA_TOKEN_VERIFIER_TOPIC, publicKeyId, encodedPublicKey), (metadata, exception) -> {
            if (exception == null) {
                log.debug("Public key sent to Kafka successfully with offset: {}", metadata.offset());
            } else {
                log.error("Error sending the public key to kafka: {}", metadata.offset());
            }
        });
    }
}
