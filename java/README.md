# 📦 dverify

A Java implementation for signing and verifying data encoded in either JWT or UUID, secured with ECDSA keys distributed via Kafka. It relies on **[RocksDB](https://rocksdb.org/)** for persistence and automatic key rotation, ensuring ultra-fast verification and robust security.

---

## ✨ Features

- 🔐 **JWT Signing & Verification** using ES256 (ECDSA)
- 🔁 **Automatic Key Rotation**
- 📬 **Public Key Distribution** via Kafka
- 🧠 **Fast and Persistent Storage** using **[RocksDB](https://rocksdb.org/)**
- ⚙️ **Environment-Based Configuration** with defaults.

---

## 📦 Installation

To install DVerify, follow these steps:

### 1. Add the Dependency

For **Maven**:

```xml

<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>dverify</artifactId>
    <version>3.0.3</version>
</dependency>
```

For **Gradle**:
```gradle
implementation 'io.github.cyfko:dverify:3.0.3'
```

### 2. Environment Variables (Optional)

The application relies on the following environment variables for configuration:

| Variable Name                     | Description                             | Default Value                                    |
|-----------------------------------|-----------------------------------------|--------------------------------------------------|
| `DVER_KAFKA_BOOSTRAP_SERVERS`     | Kafka bootstrap servers                 | `localhost:9092`                                 |
| `DVER_TOKEN_VERIFIER_TOPIC`       | Kafka topic for token verification      | `token-verifier`                                 |
| `DVER_EMBEDDED_DATABASE_PATH`     | Path for RocksDB storage                | `dverify_db_data` (relative to _temp_ directory) |
| `DVER_KEYS_ROTATION_MINUTES`      | Interval (in minutes) for key rotation  | `1440` (24h)                                     |

> NOTE: The `KafkaBrokerAdapter` implementation of Broker uses **[RocksDB](https://rocksdb.org/)** as the embedded database for local storage.

## 🚀 Usage

### 🔧 Using `GenericSignerVerifier` with a Kafka-based `Broker`

- #### Data signature in the form of transparent token (jwt)

    ```java
    import io.github.cyfko.dverify.TokenMode;
    import io.github.cyfko.dverify.impl.GenericSignerVerifier;
    import io.github.cyfko.dverify.impl.kafka.KafkaBrokerAdapter;
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    
    Signer signer = new GenericSignerVerifier(KafkaBrokerAdapter.of(properties));
    String jwt = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2), TokenMode.jwt);
    System.out.println("Generated Token: " + jwt); // output >> Generated Token: <JWT>
    ```

  #### Verifying the token and extracting the data
    ```java
    import io.github.cyfko.dverify.TokenMode;Verifier verifier = new GenericSignerVerifier(KafkaBrokerAdapter()); // KafkaBrokerAdapter constructed with default properties
    UserData userData = verifier.verify(jwt, UserData.class, TokenMode.jwt);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```
- #### Data signature in the form of an opaque token (uuid)

    ```java
    import io.github.cyfko.dverify.TokenMode;
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    
    Signer signer = new GenericSignerVerifier(KafkaBrokerAdapter.of(properties));
    String token = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2), TokenMode.uuid);
    System.out.println("Generated Token: " + token); // output >> Generated Token: <UUID>
    ```

  #### Verifying the token and extracting the data
    ```java
    Verifier verifier = new GenericSignerVerifier(KafkaBrokerAdapter());
    UserData userData = verifier.verify(token, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```

### 🔧 Using `GenericSignerVerifier` with a Custom `Broker` (Database-backed)

If you prefer not to use Kafka or want to integrate `dverify` into a monolithic or database-cluster-based architecture, you can implement a custom `Broker` backed by a relational database like MySQL or PostgreSQL.

This is particularly useful for:

- Lightweight deployments without Kafka or messaging systems.
- Architectures using **sharded database clusters** (e.g., by tenant, region, or service).
- Centralized monoliths storing verification messages directly in a trusted SQL store.

#### Example: `DatabaseBroker` Implementation

```java
public class DatabaseBroker implements Broker {

    private final DataSource dataSource;

    public DatabaseBroker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletableFuture<Void> send(String key, String message) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO broker_messages (message_key, message_value, created_at) VALUES (?, ?, NOW())")) {
                    stmt.setString(1, key);
                    stmt.setString(2, message);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to store message in DB", e);
            }
        });
    }

    @Override
    public String get(String keyId) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT message_value FROM broker_messages WHERE message_key = ? ORDER BY created_at DESC LIMIT 1")) {
                stmt.setString(1, keyId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("message_value");
                } else {
                    throw new DataExtractionException("Message not found for key: " + keyId);
                }
            }
        } catch (SQLException e) {
            throw new DataExtractionException("Failed to retrieve message from DB", e);
        }
    }
}
```

Using `DatabaseBroker` Implementation

```java
import io.github.cyfko.dverify.TokenMode;

DataSource ds = ... // Configure your JDBC DataSource (HikariCP, etc.)
Broker broker = new DatabaseBroker(ds);

GenericSignerVerifier signerVerifier = GenericSignerVerifier(broker);

// Use as usual
String jwt = signerVerifier.sign("service #1", Duration.ofHours(15), TokenMode.jwt);
String serviceName1 = signerVerifier.verify(jwt, String.class); // Expected: serviceName1.equals("service #1")

String uuid = signerVerifier.sign("service #2", Duration.ofHours(15), TokenMode.uuid);
String serviceName2 = signerVerifier.verify(uuid, String.class); // Expected: serviceName2.equals("service #2")
```

---

## 📌 Requirements

- Java >= 21
- Kafka cluster running

---

## 🔐 Security Considerations

- Uses ES256 (ECDSA with P-256 curve)
- All public keys are stored and verified from **[RocksDB](https://rocksdb.org/)**
- Only valid keys within the expiration window are accepted
