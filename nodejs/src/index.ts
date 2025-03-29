// Core class (for most use cases)
export { DVerify } from './DVerify';

// Advanced usage: direct access to signer and verifier
export { DverifyDataSigner } from './implementations/DverifyDataSigner';
export { DverifyDataVerifier } from './implementations/DverifyDataVerifier';

// Types exposed for typing client usage
export type { SignResponse, VerifyResponse } from './DVerify';
export type { KeyRecord } from './implementations/DverifyDataVerifier';