import dotenv from 'dotenv';
dotenv.config();

export const config = {
  broker: process.env.KAFKA_BROKER || 'localhost:9093',
  kafkaTopic: process.env.DVERIFY_KAFKA_TOPIC || 'public_keys_topic',
  dbPath: process.env.DVERIFY_DB_PATH || './signer-db',
  keyRotationIntervalMs: Number(process.env.DVERIFY_KEY_ROTATION_MS) || 3600000,
  cleanupIntervalMs: Number(process.env.DVERIFY_CLEANUP_INTERVAL_MS) || 1800000,
};
