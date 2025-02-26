package io.github.cyfko.disver.impl;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

public abstract class PropertiesUtil {

    /// Overwrite the `ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG` and `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG` values with
    /// the provided `boostrapServers`.
    /// @param boostrapServers A comma-separated list of Kafka boostrap servers.
    /// @return the updated Properties.
    static Properties of(Properties props, String boostrapServers){
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, boostrapServers);
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, boostrapServers);
        return props;
    }
}
