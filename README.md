# DVerify (Distributed Token Verification)
Secure & Lightweight Token Verification for Microservices

## 🚀 Overview 

DVerify is an open-source token verification library designed to simplify authentication in microservices architectures. 

In distributed systems, where services constantly communicate, ensuring authenticity and data integrity is critical. 

DVerify provides a lightweight, efficient, and developer-friendly solution to verify JSON Web Tokens (JWTs) and other token types across services.

## ✨ Features

- ✅ Lightweight & Fast – Minimal overhead for seamless integration.
- ✅ Supports Multiple Token Types – Primarily designed for JWTs, but adaptable.
- ✅ Ensures Secure Communication – Verifies token authenticity to prevent tampering.
- ✅ Scales with Your Microservices – Works across distributed service environments.
- ✅ Developer-Friendly API – Easy-to-use and integrates smoothly.

## 📦 Installation

To install DVerify, follow these steps:


### 1. Add the Dependency

For **Maven**:

```xml

<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>dverify</artifactId>
    <version>2.1.0</version>
</dependency>
```

For **Gradle**:
```gradle
implementation 'io.github.cyfko:dverify:2.1.0'
```

### 2. Environment Variables (Optional)

The application relies on the following environment variables for configuration:

| Variable Name                     | Description                               | Default Value                                    |
|------------------------------------|-------------------------------------------|--------------------------------------------------|
| `DVER_CLEANUP_INTERVAL_MINUTES`   | Interval (in minutes) for cleanup tasks  | `60`                                             |
| `DVER_KAFKA_BOOSTRAP_SERVERS`     | Kafka bootstrap servers                  | `localhost:9092`                                 |
| `DVER_TOKEN_VERIFIER_TOPIC`       | Kafka topic for token verification       | `token-verifier`                                 |
| `DVER_EMBEDDED_DATABASE_PATH`     | Path for RocksDB storage                 | `dverify_db_data` (relative to _temp_ directory) |
| `DVER_KEYS_ROTATION_MINUTES`      | Interval (in minutes) for key rotation   | `1440` (24h)                                     |

> NOTE: The application uses **[RocksDB](https://rocksdb.org/)** as the embedded database for local storage.

## 🚀 Usage

🔑 Basic Token Verification

- ### 1. Transform a data to a JWT token to secure it
  #### Signing the data

    ```java
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    
    DataSigner signer = KafkaDataSigner.of(properties);
    String jwt = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2));
    System.out.println("Generated Token: "+jwt);
    ```

  #### Verifying the JWT token
    ```java
    DataVerifier verifier = new KafkaDataVerifier(); // will use the default config
    UserData userData = verifier.verify(jwt, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```
- ### 2 Transform a data to a unique identifier to secure it but without exposing details
  #### Signing the data

    ```java
    import java.util.Properties;
    
    Properties properties = new Properties();
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    properties.setProperty(SignerConfig.GENERATED_TOKEN_CONFIG, Constant.GENERATED_TOKEN_IDENTITY);
    
    DataSigner signer = KafkaDataSigner.of(properties);
    String uniqueId = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2));
    System.out.println("Generated ID: "+uniqueId);
    ```

  #### Verifying the Identity token
    ```java
    DataVerifier verifier = new KafkaDataVerifier(); // The verifier does not have to change to accommodate to the generated token type!
    UserData userData = verifier.verify(uniqueId, UserData.class);
    System.out.println("Verified Data: " + userData.getEmail());  // output >> Verified Data: john.doe@example.com
    ```

## 🌍 Advanced Use Cases

- 1️⃣ Authentication & Authorization

Validate user identities and permissions with JWTs.
Ensure only authorized users can access specific services.


- 2️⃣ Secure API Requests Between Microservices

Verify inter-service requests by validating tokens before processing.
Prevent unauthorized access to sensitive endpoints.


- 3️⃣ Token-Based Resource Access

Enable secure document downloads outside the web application (e.g., sending download links via email with token verification).
Protect API endpoints with short-lived or refreshable tokens.


- 4️⃣ Identity Verification for Decentralized Applications

Verify self-sovereign identities (SSI) using decentralized ID tokens.
Authenticate users across blockchain-based or federated identity systems.


## 🏗 Who Can Benefit?

- 👨‍💻 Developers & Architects – Simplify token authentication across microservices.
- 🏢 Organizations – Strengthen security & ensure trusted communication.
- 🔐 Cybersecurity Professionals – Implement a robust verification mechanism.

## 🛠 Contributing

We welcome contributions from the community! Follow these steps:

1. Fork the repository
2. Create a feature branch

```shell
git checkout -b feature/your-feature
```

3. Commit your changes

```shell
git commit -m "Added new feature"
```

4. Push and create a pull request

```shell
git push origin feature/your-feature
```

## 📖 Check the CONTRIBUTING.md for detailed guidelines.

📜 License

This project is licensed under the MIT License. See the LICENSE file for more details.

## 📢 Get Involved!

💬 Have feedback or ideas? Let’s build a secure and efficient token verification system together!

📌 GitHub: https://github.com/cyfko/dverify
📧 Contact: frank.kossi@kunrin.com