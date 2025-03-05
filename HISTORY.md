# DVerify Change Log

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