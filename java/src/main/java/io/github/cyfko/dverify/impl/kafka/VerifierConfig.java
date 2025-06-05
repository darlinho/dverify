package io.github.cyfko.dverify.impl.kafka;

/**
 * Defines the configuration keys for the DataVerifier.
 */
public abstract class VerifierConfig {
    /**
     * The value that should be associated to this property name must meet the directory path naming restriction (if any).
     */
    public static final String EMBEDDED_DB_PATH_CONFIG = "dverify.embedded.db";

    /**
     * The value that should be associated to this property name must meet the Kafka topic name restriction (if any).
     */
    public static final String BROKER_TOPIC_CONFIG = "dverify.broker.topic";
}
