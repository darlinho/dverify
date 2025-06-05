package io.github.cyfko.dverify;

import io.github.cyfko.dverify.exceptions.DataExtractionException;

import java.util.concurrent.CompletableFuture;

/**
 * Interface representing a lightweight broker responsible for propagating metadata messages
 * along with cryptographic key identifiers used in token signing.
 * <p>
 * Implementations of this interface act as a communication mechanism between services
 * in a distributed system, enabling one component to broadcast a signed metadata message
 * (typically including a {@code keyId}) and others to retrieve it later by key.
 * </p>
 *
 * <p>
 * This abstraction is useful in scenarios where ephemeral public keys are distributed
 * alongside verifiable context metadata, such as in distributed token verification schemes.
 * </p>
 *
 * @author Frank KOSSI
 * @since 3.0.0
 */
public interface Broker {

    /**
     * Asynchronously publishes a metadata message associated with a given key.
     *
     * <p>
     * The message typically includes a public key identifier and related signing metadata.
     * Calling this method should make the data available for subsequent {@link #get(String)} calls.
     * </p>
     *
     * @param key     a unique key under which the message should be published (e.g. {@code keyId})
     * @param message the metadata message to broadcast; must not be {@code null}
     * @return a {@link CompletableFuture} that completes when the message has been sent
     */
    CompletableFuture<Void> send(String key, String message);

    /**
     * Retrieves the metadata message associated with the given key.
     *
     * @param key the key for which the metadata is being requested
     * @return the metadata message previously sent via {@link #send(String, String)}
     * @throws DataExtractionException if the key is unknown, expired, or the message is otherwise unavailable
     */
    String get(String key) throws DataExtractionException;
}

