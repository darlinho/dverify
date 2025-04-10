package io.github.cyfko.dverify;

import io.github.cyfko.dverify.exceptions.DataExtractionException;

import java.util.concurrent.CompletableFuture;

public interface Broker {
    CompletableFuture<Void> send(String key, String message);
    String get(String key) throws DataExtractionException;
}
