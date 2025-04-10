package io.github.cyfko.dverify.impl.kafka;

import io.github.cyfko.dverify.Broker;
import io.github.cyfko.dverify.exceptions.DataExtractionException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KafkaBrokerAdapter implements Broker {
    private static final org.slf4j.Logger log = org. slf4j. LoggerFactory. getLogger(KafkaBrokerAdapter.class);
    private final KafkaProducer<String,String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final RocksDB db;
    private final Options options;
    private final ScheduledExecutorService scheduler;
    private Properties properties;

    static {
        try {
            RocksDB.loadLibrary();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties defaultKafkaProperties() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Constant.KAFKA_BOOSTRAP_SERVERS);
        PropertiesUtil.addUniqueKafkaProperties(properties);
        return properties;
    }

    /**
     * Construct an instance of the <code>KafkaTransporter</code>, mixing environment properties and the provided
     * <code>Properties</code>.
     * Note that some properties will be discarded if they don't match to the way KafkaTransporter works.
     *
     * @implNote Recognized properties are those defined by {@link org.apache.kafka.clients.producer.ProducerConfig} and
     * {@link io.github.cyfko.dverify.impl.kafka.SignerConfig} classes.
     * <ul>
     *    <li><code>{@link org.apache.kafka.clients.producer.ProducerConfig}.BOOTSTRAP_SERVERS_CONFIG</code> <sup><small>[REQUIRED]</small></sup> as specified by Kafka</li>
     * </ul>
     *
     * @param props initial Properties to use to construct the KafkaTransporter.
     * @throws IllegalArgumentException if any of the <strong><small>[REQUIRED]</small></strong> key is not found in <code>props</code>.
     */
    public static KafkaBrokerAdapter of(Properties props){
        if (!props.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)){
            throw new IllegalArgumentException("missing properties when trying to construct KafkaDataSigner");
        }

        Properties usedProps = new Properties();
        usedProps.putAll(props);
        PropertiesUtil.addUniqueKafkaProperties(usedProps);
        return new KafkaBrokerAdapter(usedProps);
    }

    private KafkaBrokerAdapter(Properties props) {
        try {
            // configure embedded database
            this.options = new Options().setCreateIfMissing(true);
            this.db = RocksDB.open(options, props.getProperty(VerifierConfig.EMBEDDED_DB_PATH_CONFIG));

            // configure Kafka
            props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getOrCreateUniqueGroupId());
            this.properties = props;
            this.producer = new KafkaProducer<>(properties);
            this.consumer = new KafkaConsumer<>(properties);
            this.consumer.subscribe(List.of(Constant.KAFKA_TOKEN_VERIFIER_TOPIC));

            // Add a shutdown hook to gracefully close the embedded database
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Closing RocksDB...");
                closeDB();
            }));

            // Initialize async work.
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduleAsyncWork();

        } catch (RocksDBException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Unable to initialize KafkaVerifier: Embedded Database could not be opened.");
        }
    }

    /**
     * Construct a KafkaTransporter using the provided `boostrapServers`.
     * @param boostrapServers the value to assign to the property `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG`.
     */
    public KafkaBrokerAdapter(String boostrapServers) {
        this(PropertiesUtil.of(defaultKafkaProperties(), boostrapServers));
    }

    /**
     * Construct a KafkaDataSigner with defaults properties.
     */
    public KafkaBrokerAdapter() {
        this(defaultKafkaProperties());
    }

    @Override
    public CompletableFuture<Void> send(String key, String message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        producer.send(new ProducerRecord<>(properties.getProperty(SignerConfig.BROKER_TOPIC_CONFIG), key, message), (metadata, exception) -> {
            if (exception == null) {
                log.debug("Public key sent to Kafka successfully with offset: {}", metadata.offset());
                future.complete(null);
            } else {
                log.error("Error sending the public key to kafka: {}", metadata.offset());
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public String get(String keyId) {
        try {
            byte[] bytes = db.get(keyId.getBytes());
            final String messageValue = bytes != null ? new String(bytes) : null;
            if (messageValue == null) {
                log.debug("Failed to find public key for keyId: {}", keyId);
                throw new DataExtractionException("Key {" + keyId + "} not found");
            }
            return messageValue;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new DataExtractionException("An unexpected error occurred when reading the embedded DB: \n" + e.getMessage());
        }
    }

    /**
     * Starts a background thread that periodically deletes expired entries based on a custom predicate.
     */
    private void scheduleAsyncWork() {
        scheduler.scheduleAtFixedRate(() -> {
            removeOldEmbeddedDatabaseEntries(db);
            saveKafkaMessagesOnEmbeddedDatabase(consumer, db);
        }, 0, 1, TimeUnit.MINUTES);
    }

    private String getOrCreateUniqueGroupId() throws RocksDBException {
        byte[] idByte = db.get(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY.getBytes());
        if (idByte == null) {
            idByte = UUID.randomUUID().toString().getBytes();
            db.put(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY.getBytes(), idByte);
        }
        return new String(idByte);
    }

    private void closeDB() {
        if (db != null) {
            db.close(); // Closes database connection
        }
        if (options != null) {
            options.close(); // Closes options
        }
    }

    private static void saveKafkaMessagesOnEmbeddedDatabase(KafkaConsumer<String,String> consumer, RocksDB db) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10)); // At most 10 seconds to wait for.
        for (var record: records) {
            try {
                // persist on embedded DB
                db.put(record.key().getBytes(), record.value().getBytes());
            } catch (RocksDBException ex) {
                log.error(ex.getMessage());
            }
        }
    }

    private static void removeOldEmbeddedDatabaseEntries(RocksDB db) {
        try (RocksIterator iterator = db.newIterator()) {
            long now = System.currentTimeMillis();

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                String value = new String(iterator.value(), StandardCharsets.UTF_8);
                if (key.equals(ConstantDefault.UNIQUE_BROKER_GROUP_ID_KEY)) continue;

                // Extract timestamp from stored value
                long timestamp = Long.parseLong(value.split(":")[2]);

                // Custom predicate: Delete if expired AND value contains "value1"
                if ((now - timestamp) > 0) {
                    db.delete(iterator.key());
                    log.info("Deleted key {} after expiration.", key);
                }
            }
        } catch (RocksDBException e) {
            e.printStackTrace();
        } catch (Exception e) {
            log.error("Error when running cleanup task: \n{}.", e.getMessage());
        }
    }
}
