package io.github.cyfko.dverify.impl.kafka;

public abstract class SignerConfig {
    /**
     * The value that should be associated to the property have to meet the Kafka topic name restriction (if any).
     */
    public static final String BROKER_TOPIC_CONFIG = "broker.topic";
}
