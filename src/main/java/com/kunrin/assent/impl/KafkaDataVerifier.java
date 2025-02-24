package com.kunrin.assent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kunrin.assent.DataVerifier;
import com.kunrin.assent.exceptions.DataExtractionException;
import com.kunrin.assent.util.JacksonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class KafkaDataVerifier implements DataVerifier {
    private static final org.slf4j.Logger log = org. slf4j. LoggerFactory. getLogger(KafkaDataVerifier.class);
    private static final String TOKEN_VERIFIER_TOPIC;
    private static final KeyFactory keyFactory;

    static {
        var topic = System.getenv("TOKEN_VERIFIER_TOPIC");
        TOKEN_VERIFIER_TOPIC = topic != null ? topic : Constant.TOKEN_VERIFIER_TOPIC_DEFAULT;

        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final KafkaConsumer<String, String> consumer;
    private final Cache<String, String> cache;

    public KafkaDataVerifier(Properties props) {
        // Configure kafka consumer.
        this.consumer = new KafkaConsumer<>(overwriteProps(props));
        consumer.subscribe(List.of(TOKEN_VERIFIER_TOPIC));

        // configure cache
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS) // Records expire after 12 hours
                .maximumSize(1000) // Limit cache size to avoid memory overload
                .build();
    }

    private static Properties overwriteProps(Properties props) {
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("group.id", UUID.randomUUID().toString());
        props.setProperty("auto.offset.reset", "earliest");
        return props;
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
        } catch (Exception e) {
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
        String base64Key = cache.getIfPresent(keyId);
        if (base64Key == null) { // consumes kafka messages
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000)); // At most 5 secs to wait for.
            records.forEach(record -> {
                cache.put(record.key(), record.value());
            });

            base64Key = cache.getIfPresent(keyId);
            if (base64Key == null) {
                log.debug("Failed to find public key for keyId: {} from -> {}", keyId, cache);
                throw new DataExtractionException("Key not found");
            }
        }

        // Decode the Base64 encoded string into a byte array
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        try {
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }
}
