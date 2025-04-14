# DVerify Change Log

## 4.2.0 (10/04/2025)
### Feature
* Adding `Revoker` interface to handle revoking generated tokens either by providing the token itself or its tracking ID.

### Behavior Changes
* Making `GenericSignerVerifier` implements the `Revoking` interface.
* Making `DatabaseBroker` delete revoked tokens.

## 4.1.0 (10/04/2025)
### Public API Changes
* Adding `trackingId` to the interface `Signer.sign()` method to allow tracking the generated token for subsequent 
* revocation before expiration.

## 3.0.0 (10/04/2025)
### Public API Changes
* Renaming interface `DataSigner` -> `Signer` and adding third parameter on the `Signer.sign()` method.
* Renaming interface `DataVerifier` -> `Verifier`.
* Adding `Broker` interface to handle metadata propagation and retrieval according to the design.
* Removing `KafkaDataSigner` and `KafkaDataVerifier` implementation classes.
* Adding `GenericSignerVerifier` class to handle token generation by signing a data and token verification by extracting
  the data when possible. That class serve as a useful implementation `Signer` and `Verifier` interface and rely on an 
  implementation of `Broker` interface as its unique constructor parameter.

## 2.2.0 (05/03/2025)
### Behavior Changes
* Relying on scheduled task to automatically remove expired entries on the embedded database.

## 2.1.0 (03/03/2025)
### Behavior Changes
* Obtain UUID tokens instead of JWT by constructing a KafkaDataSigner with the property name `SignerConfig.GENERATED_TOKEN_CONFIG` mapped to the value `Constant.GENERATED_TOKEN_IDENTITY`.

## 2.0.0 (01/03/2025)
### Public API Changes
* Moving main package to `io.github.cyfko.dverify` Package

### Behavior Changes
* Using `RocksDB` for persistence instead of relying on the cache mechanism. It prevents from reading Kafka messages from the beginning when the cache is evicting some entries.
* Autocommit offsets when consumming kafka messages. Rely on `RocksDB` as the first lookup mechanism.

### Bug Fixes
* Some bugs fixed.

## 1.1.0 (26/02/2025)
### Public API Changes
* Moving main package to `io.github.cyfko.disver` Package

### Bug Fixes
* Fix some bugs in `KafkaDataVerifier`.

## 1.0.0 (25/02/2025)
Initial Release