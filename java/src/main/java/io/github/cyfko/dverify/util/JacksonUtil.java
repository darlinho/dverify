package io.github.cyfko.dverify.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for working with JSON using Jackson.
 */
public class JacksonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts an object to JSON.
     *
     * @param obj The object to be converted.
     * @return The JSON string.
     * @throws JsonProcessingException If there is an error during conversion.
     */
    public static String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Deserialize JSON string into an object.
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
