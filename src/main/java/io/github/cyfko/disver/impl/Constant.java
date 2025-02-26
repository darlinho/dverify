package io.github.cyfko.disver.impl;

/// Provide defaults to some constants.
abstract class ConstantDefault{
    static final String KAFKA_BOOSTRAP_SERVERS = "localhost:9092";
    static final String TOKEN_VERIFIER_TOPIC = "token-verifier-topic";
    static final long KEYS_ROTATION_MINUTES = 30L;
}

/// Defines environment variable names.
abstract class Env{
    static final String KAFKA_BOOSTRAP_SERVERS = "AST_KAFKA_BOOSTRAP_SERVERS";
    static final String KAFKA_TOKEN_VERIFIER_TOPIC = "AST_TOKEN_VERIFIER_TOPIC";
    static final String KEYS_ROTATION_MINUTES = "AST_KEYS_ROTATION_MINUTES";
}

/// A Placeholder for some constants.
public abstract class Constant {
    /// Comma-delimited list of host:port pairs to use for establishing the initial connections to the Kafka cluster.
    static final String KAFKA_BOOSTRAP_SERVERS;

    /// The Kafka topic used for producing/consuming asymmetric ublic keys.
    static final String KAFKA_TOKEN_VERIFIER_TOPIC;

    /// The interval in minutes before the current used Asymmetric keys-pair changes.
    static final long KEYS_ROTATION_MINUTES;

    static {
        final var boostrapServers = System.getenv(Env.KAFKA_BOOSTRAP_SERVERS);
        KAFKA_BOOSTRAP_SERVERS = boostrapServers != null ? boostrapServers : ConstantDefault.KAFKA_BOOSTRAP_SERVERS;

        final var topic = System.getenv(Env.KAFKA_TOKEN_VERIFIER_TOPIC);
        KAFKA_TOKEN_VERIFIER_TOPIC = topic != null ? topic: ConstantDefault.TOKEN_VERIFIER_TOPIC;

        final var rotationRate = System.getenv(Env.KEYS_ROTATION_MINUTES);
        KEYS_ROTATION_MINUTES = rotationRate != null ? Long.parseLong(rotationRate) : ConstantDefault.KEYS_ROTATION_MINUTES;
    }
}
