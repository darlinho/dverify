package io.github.cyfko.dverify;

import io.github.cyfko.dverify.exceptions.JsonEncodingException;

import java.time.Duration;

/**
 * Functional interface for generating signed tokens using an ephemeral asymmetric private key.
 * <p>
 * Implementations of this interface are responsible for serializing the provided payload object
 * and generating a signed token that remains valid for a specified duration. The resulting token
 * may or may not conform to the JWT format, depending on the selected {@link TokenMode}.
 * </p>
 *
 * <p>
 * This interface is primarily intended for use in distributed systems where services must issue
 * short-lived, cryptographically verifiable tokens without relying on shared secrets.
 * </p>
 *
 * @author Frank KOSSI
 * @since 3.0.0
 */
@FunctionalInterface
public interface Signer {

    /**
     * Generates a signed token that references the given payload for the specified duration.
     *
     * <p>
     * The payload is serialized and embedded in a cryptographically signed structure.
     * The resulting token is intended to be short-lived and may be encoded as a JWT or
     * in another form depending on the {@link TokenMode}.
     * </p>
     *
     * @param data     the object to be signed; must not be {@code null}
     * @param duration the duration for which the token should remain valid; must be positive
     * @param mode     the desired encoding mode for the token (e.g., {@code jwt}, {@code uuid})
     * @return a signed token as a string
     * @throws JsonEncodingException if the payload cannot be encoded or the duration is invalid
     */
    String sign(Object data, Duration duration, TokenMode mode) throws JsonEncodingException;
}

