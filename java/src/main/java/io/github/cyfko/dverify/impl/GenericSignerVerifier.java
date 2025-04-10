package io.github.cyfko.dverify.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cyfko.dverify.Signer;
import io.github.cyfko.dverify.TokenMode;
import io.github.cyfko.dverify.Verifier;
import io.github.cyfko.dverify.Broker;
import io.github.cyfko.dverify.exceptions.DataExtractionException;
import io.github.cyfko.dverify.exceptions.JsonEncodingException;
import io.github.cyfko.dverify.impl.kafka.Constant;
import io.github.cyfko.dverify.util.JacksonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GenericSignerVerifier implements Signer, Verifier {
    private static final org.slf4j.Logger log = org. slf4j. LoggerFactory. getLogger(GenericSignerVerifier.class);
    private static final KeyFactory keyFactory;
    private long lastExecutionTime = 0;
    private final Broker broker;
    private final ScheduledExecutorService scheduler;
    private KeyPair keyPair;

    static {
        try {
            keyFactory = KeyFactory.getInstance(Constant.ASYMMETRIC_KEYPAIR_ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GenericSignerVerifier(Broker broker) {
        this.broker = broker;

        // generate the first KeyPair.
        generatedKeysPair();

        // Schedule the keys-pair generation task to run at a fixed rate.
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::generatedKeysPair, 0, Constant.KEYS_ROTATION_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String sign(Object data, Duration duration, TokenMode mode) throws JsonEncodingException {
        if (data == null || duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("data should not be null and the duration should be positive");
        }

        final String keyId = UUID.randomUUID().toString();
        String token = "";

        try {
            Instant fromNow = Instant.now();
            Date issuedAt = Date.from(fromNow);
            Date expirationDate = Date.from(fromNow.plus(duration));

            token = Jwts.builder()
                    .subject(keyId)
                    .claim("data", JacksonUtil.toJson(data))
                    .issuedAt(issuedAt)
                    .expiration(expirationDate)
                    .signWith(keyPair.getPrivate())
                    .compact();

            final String base64PublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            final String message = switch (mode){
                case TokenMode.jwt -> String.format("%s:%s:%s:%s", mode.name(), base64PublicKey, expirationDate.getTime(), "");
                case TokenMode.uuid -> String.format("%s:%s:%s:%s", mode.name(), base64PublicKey, expirationDate.getTime(), token);
            };

            broker.send(keyId, message).get(3, TimeUnit.MINUTES);
            return mode == TokenMode.jwt ? token : keyId;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Unable to propagate metadata after signing: {}", e.getMessage());
            handleTransportFailure(keyId,token,mode);
            return mode == TokenMode.jwt ? token : keyId;
        } catch (Exception e){
            throw new JsonEncodingException(e.getMessage());
        }
    }

    @Override
    public <T> T verify(String token, Class<T> clazz) throws DataExtractionException {
        try {
            String keyId = getKeyId(token);

            final var data = getClaims(keyId,token).get("data", String.class);
            return JacksonUtil.fromJson(data, clazz);
        } catch (DataExtractionException|IndexOutOfBoundsException e) {
            throw e;
        } catch (Exception e){
            throw new DataExtractionException("Failed to extract subject from JWT: " + e.getMessage());
        }
    }

    private static String getKeyId(String token) throws Exception {
        if (token.contains(".")) { // expected to be a JWT token
            String payloadBase64 = token.split("\\.")[1];
            String payloadJson = new String(Base64.getDecoder().decode(payloadBase64));

            // Parse JSON and extract the "sub" field
            JsonNode jsonNode = JacksonUtil.fromJson(payloadJson, JsonNode.class);
            String keyId = jsonNode.get("sub").asText();
            return keyId;
        } else { // expected to be the keyId itself
            return token;
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
        String[] parts = broker.get(keyId).split(":");
        try {
            byte[] decodedKey = Base64.getDecoder().decode(parts[1]);
            final PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));

            return switch (TokenMode.valueOf(parts[0])){
                case TokenMode.jwt -> Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                case TokenMode.uuid -> Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(parts[3])
                        .getPayload();
            };
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected token config encountered: " + e.getMessage());
        }
    }

    private void handleTransportFailure(String keyId, String token, TokenMode mode) {
        // TODO: try to send metadata again
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
}
