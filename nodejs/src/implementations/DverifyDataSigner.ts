import { Kafka, Producer } from 'kafkajs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { open } from 'lmdb';
import { DataSigner } from '../types/DataSigner';
import { JsonEncodingException } from '../exceptions/JsonEncodingException';
import { config } from '../config';

/**
 * Represents a key record stored in LMDB.
 * - publicKey: The public key in PEM format.
 * - expiration: Expiration timestamp (in seconds).
 */
interface KeyRecord {
  publicKey: string;
  expiration: number;
}

/**
 * Dverify is responsible for signing data, storing the corresponding public key
 * in LMDB, and propagating it through Kafka for distributed verification.
 */
export class DverifyDataSigner implements DataSigner {
  private readonly producer?: Producer;          // Kafka producer for publishing public keys
  private db?: any;                     // LMDB database instance for storing keys
  private keyPair!: crypto.KeyPairKeyObjectResult; // Current key pair (rotated periodically)
  private readonly kafkaTopic: string; // Kafka topic where public keys are propagated
  private lastKeyRotation: number = 0; // Timestamp of the last key rotation
  private readonly rotationIntervalMs: number; // Key rotation interval in milliseconds

  /**
   * Initializes the Dverify.
   * @param broker - Kafka broker address (e.g., "localhost:9092").
   * @param kafkaTopic - Kafka topic where public keys are published.
   * @param dbPath - Path for LMDB storage.
   * @param rotationIntervalMs - Interval in milliseconds for key rotation.
   */
  constructor(
    broker: string = config.broker,
    kafkaTopic: string = config.kafkaTopic,
    dbPath: string = config.dbPath,
    rotationIntervalMs: number = config.keyRotationIntervalMs
  ) {
    const kafka = new Kafka({ clientId: 'dverify-signer', brokers: [broker] });
    this.producer = kafka.producer();
    this.kafkaTopic = kafkaTopic;
    this.rotationIntervalMs = rotationIntervalMs;

    // Open LMDB database with compression enabled
    this.db = open({ path: dbPath, compression: true });

    // Initialize Kafka producer and connect
    (async () => {
      await this.initProducer();
    })();

    // Generate initial key pair and start periodic key rotation
    this.rotateKeys();
    setInterval(() => this.rotateKeys(), this.rotationIntervalMs);
  }

  /**
   * Initializes and connects the Kafka producer.
   */
  private async initProducer(): Promise<void> {
    try {
      if (this.producer) {
        await this.producer.connect();
      }
      console.log('[Dverify] Kafka producer connected.');
    } catch (error) {
      console.error('[Dverify] Error connecting Kafka producer:', error);
    }
  }

  /**
   * Rotates the signing key pair at a fixed interval.
   * Ensures security by regularly changing cryptographic keys.
   */
  private rotateKeys(): void {
    const now = Date.now();
    if (now - this.lastKeyRotation >= this.rotationIntervalMs || this.lastKeyRotation === 0) {
      this.keyPair = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
      this.lastKeyRotation = now;
      console.log('[Dverify] New key pair generated (rotation).');
    }
  }

  /**
   * Publishes a newly generated public key to Kafka.
   * @param keyId - Unique identifier of the key.
   * @param publicKey - Public key in PEM format.
   * @param expiration - Expiration timestamp (in seconds).
   */
  private async propagatePublicKey(keyId: string, publicKey: string, expiration: number): Promise<void> {
    const message = JSON.stringify({ keyId, publicKey, expiration });
    try {
      if (this.producer) {
        await this.producer.send({
          topic: this.kafkaTopic,
          messages: [{key: keyId, value: message}],
        });
      }
      console.log(`[KafkaDataSigner] Public key propagated to Kafka (keyId=${keyId}).`);
    } catch (error) {
      console.error('[Dverify] Error propagating public key:', error);
    }
  }

  /**
   * Signs an object and returns a JWT.
   * Stores the corresponding public key in LMDB and propagates it via Kafka.
   * @param data - The data to be signed.
   * @param durationSeconds - Expiration time in seconds.
   * @returns The signed JWT.
   * @throws JsonEncodingException if an error occurs during token generation.
   */
  async sign<T>(data: T, durationSeconds: number): Promise<string> {
    try {
      // Generate a unique identifier for the key
      const keyId = crypto.randomUUID();
      // Compute the expiration timestamp
      const expiration = Math.floor(Date.now() / 1000) + durationSeconds;
      // Retrieve private and public keys
      const privateKey = this.keyPair.privateKey;
      const publicKeyPem = this.keyPair.publicKey.export({ type: 'spki', format: 'pem' }).toString();

      // Generate the JWT token
      const token = jwt.sign({ data, keyId }, privateKey, {
        algorithm: 'ES256',
        expiresIn: durationSeconds,
      });

      // Store the public key in LMDB for local verification
      const keyRecord: KeyRecord = { publicKey: publicKeyPem, expiration };
      await this.db.put(keyId, keyRecord);

      // Propagate the public key to other services via Kafka
      await this.propagatePublicKey(keyId, publicKeyPem, expiration);

      console.log(`[KafkaDataSigner] Token signed, keyId=${keyId}, expires at ${new Date(expiration * 1000)}`);
      return token;
    } catch (error: any) {
      throw new JsonEncodingException(error.message);
    }
  }
}
