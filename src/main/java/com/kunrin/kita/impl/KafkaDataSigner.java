package com.kunrin.kita.impl;

import com.kunrin.kita.DataSigner;
import com.kunrin.kita.exceptions.JsonEncodingException;
import com.kunrin.kita.util.JacksonUtil;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.producer.KafkaProducer;
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
    private static final String TOKEN_VERIFIER_TOPIC;
    private static final long KEYS_ROTATION_RATE_MINUTES;
    private static final org.slf4j.Logger log = org. slf4j. LoggerFactory. getLogger(KafkaDataSigner.class);

    static {
        var topic = System.getenv("TOKEN_VERIFIER_TOPIC");
        TOKEN_VERIFIER_TOPIC = topic != null ? topic:"token-verifier-topic";

        var rotationRate = System.getenv("KEYS_ROTATION_RATE_MINUTES");
        if (rotationRate != null) {
            KEYS_ROTATION_RATE_MINUTES = Long.parseLong(rotationRate);
        } else {
            KEYS_ROTATION_RATE_MINUTES = 30L;
        }
    }

    private final ScheduledExecutorService scheduler;
    private final KafkaProducer<String,String> producer;
    private long lastExecutionTime = 0;
    private KeyPair keyPair;


    public KafkaDataSigner(final Properties props) {
        // overwrite these two properties
        props.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.producer = new KafkaProducer<>(props);

        // Schedule the task to run every minute
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::generatedKeysPair, 0, KEYS_ROTATION_RATE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String sign(Object data, Duration duration) throws JsonEncodingException {
        final String publicKeyId = UUID.randomUUID().toString();

        // Calculate issued time and expiration time
        Instant fromNow = Instant.now();
        Date issuedAt = Date.from(fromNow);
        Date expirationDate = Date.from(fromNow.plus(duration));

        try {
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
        if (currentTime - lastExecutionTime >= KEYS_ROTATION_RATE_MINUTES * 60 * 1000 || lastExecutionTime == 0) {
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
        producer.send(new ProducerRecord<>(TOKEN_VERIFIER_TOPIC, publicKeyId, encodedPublicKey));
    }
}
