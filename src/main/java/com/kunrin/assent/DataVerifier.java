package com.kunrin.assent;

import com.kunrin.assent.exceptions.DataExtractionException;

@FunctionalInterface
public interface DataVerifier {
    /// Retrieve the JSON Web Token's data claims field as an object after successfully verify it.
    /// @throws DataExtractionException if either the token is not a valid JWT token of it is expired.
    <T> T verify(String token, Class<T> clazz) throws DataExtractionException;
}
