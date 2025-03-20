package io.github.cyfko.dverify;

import io.github.cyfko.dverify.exceptions.DataExtractionException;

@FunctionalInterface
public interface DataVerifier {
    /**
     * Validates a JWT and extracts its payload as an object.
     *
     * @param token The JWT to be verified.
     * @param clazz The expected type of the extracted payload.
     * @return The extracted payload object.
     * @throws DataExtractionException If the token is invalid or expired.
     */
    <T> T verify(String token, Class<T> clazz) throws DataExtractionException;
}
