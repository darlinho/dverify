# Tokens Verifier

A lightweight library that simplifies token verification in microservices architectures, ensuring secure token propagation and validation.

## Motivation

In a microservices environment, each service must ensure the authenticity and integrity of data received from others. **JWT (JSON Web Tokens)** are commonly used for secure data transmission between microservices. However, verifying these tokens reliably introduces several challenges:

- **Authentication & Authorization**: JWT Bearer tokens validate user identity and permissions.
- **Secure Resource Access**: Tokens facilitate secure downloads outside the web app, such as email-based download links.
- **Password Reset Links**: Secure links embedded in emails for resetting passwords.

A **centralized** microservice responsible for both **issuing and verifying tokens** might seem like an intuitive solution. However, this approach introduces **scalability concerns**:

- It creates a **bottleneck** as all other services must make HTTP requests to validate tokens.
- High **availability** issues arise if a single instance is responsible for verification.
- Even if availability is addressed, **resilience** remains a concern—how do we ensure seamless token verification if the central service fails?

## Design

To avoid the limitations of a centralized **Key Management System (KMS)**, our approach ensures:

- **Decentralized Signing & Verification**: Any microservice can act as a **token signer** and/or a **token verifier**.
- **Public Key Propagation**: Tokens are signed with an **asymmetric key pair**, and the **public key** is propagated for verification.
- **High Availability & Resilience**: Public keys are distributed efficiently using a **message broker (Kafka)**, ensuring high performance and reliability.

### Security Consideration

The system using this library must secure its **Kafka broker infrastructure** to prevent unauthorized access.

## API Overview

This library provides a simple, intuitive API through two primary interfaces:

### Signing Tokens (`DataSigner`)
```java
@FunctionalInterface
public interface DataSigner {
  /**
   * Generates a JWT signed with an asymmetric private key.
   * The `data` parameter is embedded within the token.
   *
   * @param data The object to be included in the JWT payload.
   * @param duration The validity duration of the token.
   * @return A signed JWT as a string.
   * @throws JsonEncodingException If JSON encoding fails.
   */
  String sign(Object data, Duration duration) throws JsonEncodingException;
}
```

### Verifying Tokens (`DataVerifier`)
```java
@FunctionalInterface
public interface DataVerifier {
  /**
   * Validates a JWT and extracts its payload as an object.
   *
   * @param token The JWT to be verified.
   * @param clazz The expected type of the extracted payload.
   * @return The extracted payload object.
   * @throws DataExtractionException If the token is invalid or expired.
   */
  <T> T verify(String token, Class<T> clazz) throws DataExtractionException;
}
```

## Kafka-Based Implementation

The library includes ready-to-use **Kafka-backed implementations** of these interfaces:

- **`KafkaDataSigner`** → Handles **JWT signing** and publishes the corresponding **public key**.
- **`KafkaDataVerifier`** → Listens for public key updates and verifies tokens using the latest available keys.

Each Kafka-based implementation requires **Kafka Properties** to be passed to the constructor.

### Environment Variables

The following environment variables allow customization of the Kafka-based implementation:

- **`TOKEN_VERIFIER_TOPIC`**: The Kafka topic for token verification messages (default: `token-verifier-topic`).
- **`KEYS_ROTATION_RATE_MINUTES`**: The interval in minutes for rotating public keys (default: `30`).

These implementations ensure **scalability, high availability, and decentralized verification** in a microservices ecosystem.

## Getting Started

### 1. Add the Dependency

For **Maven**:

```xml

<dependency>
    <groupId>com.kunrin.assent</groupId>
    <artifactId>tokens-verifier</artifactId>
    <version>1.0.0</version>
</dependency>
```

For **Gradle**:
```gradle
implementation 'com.kunrin.assent:tokens-verifier:1.0.0'
```

### 2. Configure Kafka Properties

Ensure Kafka is configured in **`application.properties`**:
```properties
kafka.bootstrap-servers=localhost:9092
kafka.public-key-topic=tokens-public-keys
```

### 3. Usage Example

#### Signing a Token
```java
DataSigner signer = new KafkaDataSigner(kafkaProperties);
String jwt = signer.sign(new UserData("john.doe@example.com"), Duration.ofHours(2));
System.out.println("Generated Token: " + jwt);
```

#### Verifying a Token
```java
DataVerifier verifier = new KafkaDataVerifier(kafkaProperties);
UserData userData = verifier.verify(jwt, UserData.class);
System.out.println("Verified Data: " + userData.getEmail());
```

### 4. Running Kafka Locally (For Testing)

Use **Docker** to start a local Kafka instance:
```sh
docker run -d --name kafka -p 9092:9092 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
           -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 wurstmeister/kafka
```

### 5. Secure Your Kafka Infrastructure

For production environments, **ensure**:

- ✅ Proper **authentication & authorization** (e.g., **SASL, TLS**)
- ✅ Restricted **topic access permissions**
- ✅ Secure **message retention policies**

