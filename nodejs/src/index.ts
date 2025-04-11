// Core class (for most use cases)
export { DVerify } from './DVerify';

// Advanced usage: direct access to signer and verifier
export { DataSigner } from './implementations/DataSigner';
export { DataVerifier } from './implementations/DataVerifier';

// Types exposed for typing client usage
export type { SignResponse, VerifyResponse } from './DVerify';
export type { KeyRecord } from './implementations/DataVerifier';

// Custom exceptions for error handling in consumers
export {
  InvalidTokenException,
  KeyNotFoundException,
  KeyExpiredException,
  VerificationFailedException,
  ReplayInProgressException,
} from './exceptions';
