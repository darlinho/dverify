package com.kunrin.assent;

import com.kunrin.assent.exceptions.JsonEncodingException;

import java.time.Duration;

@FunctionalInterface
public interface DataSigner {
    /**
     * Generates a JWT signed with an asymmetric private key.
     * The not-null `data` parameter is embedded within the token.
     *
     * @param data The object to be included in the JWT payload.
     * @param duration The validity duration of the token.
     * @return A signed JWT as a string.
     * @throws JsonEncodingException If encoding `data` to the JSON format fails or if the Duration is not in the future.
     */
    String sign(Object data, Duration duration) throws JsonEncodingException;
}
