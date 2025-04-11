export class InvalidTokenException extends Error {
  constructor(message = 'Invalid or malformed token.') {
    super(message);
    this.name = 'InvalidTokenException';
  }
}

export class KeyNotFoundException extends Error {
  constructor(keyId: string) {
    super(`Public key not found after replay (keyId=${keyId}).`);
    this.name = 'KeyNotFoundException';
  }
}

export class KeyExpiredException extends Error {
  constructor(keyId: string) {
    super(`Public key expired (keyId=${keyId}).`);
    this.name = 'KeyExpiredException';
  }
}

export class ReplayInProgressException extends Error {
  constructor() {
    super('Replay already in progress.');
    this.name = 'ReplayInProgressException';
  }
}

export class VerificationFailedException extends Error {
  constructor(reason: string) {
    super(`Cryptographic verification failed: ${reason}`);
    this.name = 'VerificationFailedException';
  }
}

export class JsonEncodingException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JsonEncodingException';
  }
}
