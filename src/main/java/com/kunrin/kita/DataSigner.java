package com.kunrin.kita;

import com.kunrin.kita.exceptions.JsonEncodingException;

import java.time.Duration;

@FunctionalInterface
public interface DataSigner {
    /// Get a JSON Web Token signed with an asymmetric private key for which its 'data' claims
    /// is the provided `data`.
    String sign(Object data, Duration duration) throws JsonEncodingException;
}
