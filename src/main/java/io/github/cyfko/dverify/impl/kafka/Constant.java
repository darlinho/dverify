package io.github.cyfko.dverify.impl.kafka;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.util.UUID;

/// Provide defaults to some constants.
abstract class ConstantDefault{
    static final String KAFKA_BOOSTRAP_SERVERS = "localhost:9092";
    static final String TOKEN_VERIFIER_TOPIC = "token-verifier-topic";
    static final long KEYS_ROTATION_MINUTES = 30L;
    static final String EMBEDDED_DATABASE_PATH = "dverify_db_data";
    static final String UNIQUE_BROKER_GROUP_ID_KEY = "dverify_db";
}

/// Defines environment variable names.
abstract class Env{
    static final String KAFKA_BOOSTRAP_SERVERS = "DVER_KAFKA_BOOSTRAP_SERVERS";
    static final String KAFKA_TOKEN_VERIFIER_TOPIC = "DVER_TOKEN_VERIFIER_TOPIC";
    static final String EMBEDDED_DATABASE_PATH = "DVER_EMBEDDED_DATABASE_PATH";
    static final String KEYS_ROTATION_MINUTES = "DVER_KEYS_ROTATION_MINUTES";
}

/**
 * A Placeholder for some constants.
 */
public abstract class Constant {

    /**
     * Comma-delimited list of host:port pairs to use for establishing the initial connections to the Kafka cluster.
     */
    static final String KAFKA_BOOSTRAP_SERVERS;

    /**
     * The Kafka topic used for producing/consuming asymmetric public keys.
     */
    static final String KAFKA_TOKEN_VERIFIER_TOPIC;

    /**
     * The interval in minutes before the current used Asymmetric keys-pair changes.
     */
    static final long KEYS_ROTATION_MINUTES;

    /**
     * The Path to the embedded database directory.
     */
    static final String EMBEDDED_DATABASE_PATH;

    static {
        final var boostrapServers = System.getenv(Env.KAFKA_BOOSTRAP_SERVERS);
        KAFKA_BOOSTRAP_SERVERS = boostrapServers != null ? boostrapServers : ConstantDefault.KAFKA_BOOSTRAP_SERVERS;

        final var topic = System.getenv(Env.KAFKA_TOKEN_VERIFIER_TOPIC);
        KAFKA_TOKEN_VERIFIER_TOPIC = topic != null ? topic: ConstantDefault.TOKEN_VERIFIER_TOPIC;

        final var rotationRate = System.getenv(Env.KEYS_ROTATION_MINUTES);
        KEYS_ROTATION_MINUTES = rotationRate != null ? Long.parseLong(rotationRate) : ConstantDefault.KEYS_ROTATION_MINUTES;

        final var dbPath = System.getenv(Env.EMBEDDED_DATABASE_PATH);
        EMBEDDED_DATABASE_PATH = dbPath != null ? dbPath : ConstantDefault.EMBEDDED_DATABASE_PATH;
    }
}
