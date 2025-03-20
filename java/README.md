JAVA IMPLEMENTATION
===================

# 📦 Installation

To install DVerify, follow these steps:

## 1. Add the Dependency

For **Maven**:

```xml

<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>dverify</artifactId>
    <version>2.2.1</version>
</dependency>
```

For **Gradle**:
```gradle
implementation 'io.github.cyfko:dverify:2.2.1'
```

## 2. Environment Variables (Optional)

The application relies on the following environment variables for configuration:

| Variable Name                     | Description                               | Default Value                                    |
|------------------------------------|-------------------------------------------|--------------------------------------------------|
| `DVER_CLEANUP_INTERVAL_MINUTES`   | Interval (in minutes) for cleanup tasks  | `60`                                             |
| `DVER_KAFKA_BOOSTRAP_SERVERS`     | Kafka bootstrap servers                  | `localhost:9092`                                 |
| `DVER_TOKEN_VERIFIER_TOPIC`       | Kafka topic for token verification       | `token-verifier`                                 |
| `DVER_EMBEDDED_DATABASE_PATH`     | Path for RocksDB storage                 | `dverify_db_data` (relative to _temp_ directory) |
| `DVER_KEYS_ROTATION_MINUTES`      | Interval (in minutes) for key rotation   | `1440` (24h)                                     |

> NOTE: The Java implementation uses **[RocksDB](https://rocksdb.org/)** as the embedded database for local storage.

# 🚀 Usage

🔑 Basic Token Verification

- ## 1. Transform a data to a JWT token to secure it
  ### Signing the data

    ```java
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    
    DataSigner signer = KafkaDataSigner.of(properties);
    String jwt = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2));
    System.out.println("Generated Token: "+jwt);
    ```

  ### Verifying the JWT token
    ```java
    DataVerifier verifier = new KafkaDataVerifier(); // will use the default config
    UserData userData = verifier.verify(jwt, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```
- ## 2 Transform a data to a unique identifier to secure it but without exposing details
  ### Signing the data

    ```java
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    properties.setProperty(SignerConfig.GENERATED_TOKEN_CONFIG, Constant.GENERATED_TOKEN_IDENTITY);
    
    DataSigner signer = KafkaDataSigner.of(properties);
    String uniqueId = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2));
    System.out.println("Generated ID: "+uniqueId);
    ```

  ### Verifying the Identity token
    ```java
    DataVerifier verifier = new KafkaDataVerifier(); // The verifier does not have to change to accommodate to the generated token type!
    UserData userData = verifier.verify(uniqueId, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```