# 📦 dverify

A robust TypeScript module for signing and verifying data using JWT and ECDSA keys distributed via Kafka, with LMDB persistence and automatic key rotation.

---

## ✨ Features

- 🔐 **JWT Signing & Verification** using ES256 (ECDSA)
- 🔁 **Automatic Key Rotation**
- 📬 **Public Key Distribution** via Kafka
- 🧠 **Fast and Persistent Storage** using LMDB
- 📡 **Offline Key Replay Support** (via Kafka fromBeginning)
- ⚙️ **Environment-Based Configuration** with sane defaults

---

## 📦 Installation

```bash
npm install dverify
```

or

```bash
pnpm add dverify
```

---

## 🛠️ Usage

### Basic example (with NestJS, Express, or standalone):

```ts
import { DVerify } from 'dverify';

const dverify = new DVerify();

// Sign a payload
const { token } = await dverify.sign({ userId: 123 }, 1200);

// Verify a token
const result = await dverify.verify(token);
console.log(result.valid, result.data);
```

---

## 🧪 API

### `new DVerify()`

Creates an instance of the DVerify client with environment-based configuration.

### `sign(message: Record<string, any>, duration?: number): Promise<{ token: string }>`

Signs a JSON object and returns a JWT.

- `message`: The data you want to sign.
- `duration`: (optional) Token validity in seconds. Defaults to `1400`.

### `verify<T>(token: string): Promise<{ valid: boolean; data: T }>`

Verifies a JWT and returns its payload.

---

## ⚙️ Configuration

The module reads from your environment variables, but provides fallbacks:

| Variable                          | Description                                | Default               |
|----------------------------------|--------------------------------------------|-----------------------|
| `KAFKA_BROKER`                   | Kafka broker URL                           | `localhost:9093`      |
| `DVERIFY_KAFKA_TOPIC`            | Kafka topic for key exchange               | `public_keys_topic`   |
| `DVERIFY_DB_PATH`                | Path for LMDB storage                      | `./signer-db`         |
| `DVERIFY_KEY_ROTATION_MS`        | Key rotation interval (ms)                 | `3600000` (1h)        |
| `DVERIFY_CLEANUP_INTERVAL_MS`    | LMDB cleanup interval for expired keys     | `1800000` (30min)     |

Use a `.env` file in your consuming project:

```
KAFKA_BROKER=localhost:9092
DVERIFY_KAFKA_TOPIC=your_topic
DVERIFY_DB_PATH=./data/dverify
```

---

## 📂 Project Structure

```
src/
├── implementations/
│   ├── DverifyDataSigner.ts       // Kafka producer + key rotation
│   └── DverifyDataVerifier.ts     // Kafka consumer + JWT verification
├── interfaces/                    // Type-safe abstractions
├── config.ts                      // Environment configuration
├── Dverify.ts                     // Main public API
├── index.ts                       // Package entry point
```

---

## 📌 Requirements

- Node.js >= 16
- Kafka cluster running
- Consumer project should load `.env` before usage


---

## 🔐 Security Considerations

- Uses ES256 (ECDSA with P-256 curve)
- All public keys are stored and verified from LMDB
- Only valid keys within the expiration window are accepted

---

## 🧑‍💻 Author

**Darlinho T.** – [LinkedIn](https://www.linkedin.com/in/hyacinthe-darlin-teuma-nougosso-546521206) • [GitHub](https://github.com/darlinho)
_Contributions and feedback are welcome!_

---

## 📄 License

MIT © 2025 - Darlinho