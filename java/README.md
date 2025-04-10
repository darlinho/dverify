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
    <version>3.0.2</version>
</dependency>
```

For **Gradle**:
```gradle
implementation 'io.github.cyfko:dverify:3.0.2'
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

🔑 Basic Token Verification

- ### 1. Transform a data to a JWT token to secure it
  #### Signing the data

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

  #### Verifying the JWT token
    ```java
    import io.github.cyfko.dverify.TokenMode;Verifier verifier = new GenericSignerVerifier(KafkaBrokerAdapter()); // KafkaBrokerAdapter constructed with default properties
    UserData userData = verifier.verify(jwt, UserData.class, TokenMode.jwt);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```
- ### 2 Transform a data to a unique identifier to secure it but without exposing details
  #### Signing the data

    ```java
    import io.github.cyfko.dverify.TokenMode;
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    
    Signer signer = new GenericSignerVerifier(KafkaBrokerAdapter.of(properties));
    String token = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2), TokenMode.uuid);
    System.out.println("Generated Token: " + token); // output >> Generated Token: <UUID>
    ```

  #### Verifying the Identity token
    ```java
    Verifier verifier = new GenericSignerVerifier(KafkaBrokerAdapter());
    UserData userData = verifier.verify(token, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
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
