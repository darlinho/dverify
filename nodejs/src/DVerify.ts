import { DverifyDataSigner } from './implementations/DverifyDataSigner';
import { DverifyDataVerifier } from './implementations/DverifyDataVerifier';

export interface SignResponse {
  token: string;
}

export interface VerifyResponse<T = any> {
  valid: boolean;
  data: T;
}

export class DVerify {
  private readonly signer: DverifyDataSigner;
  private readonly verifier: DverifyDataVerifier;

  constructor() {
    this.signer = new DverifyDataSigner();
    this.verifier = new DverifyDataVerifier();
  }

  /**
   * Sign a JSON object and return the generated JWT.
   * @param message - Data to sign.
   * @param duration - Token duration in seconds (default: 1400 seconds).
   * @returns The signed JWT.
   */
  async sign(message: Record<string, any>, duration: number = 1400): Promise<SignResponse> {
    try {
      const token = await this.signer.sign(message, duration);
      return { token };
    } catch (error: any) {
      throw new Error(`Error signing message: ${error.message}`);
    }
  }

  /**
   * Verify a previously signed JWT and return the result.
   * @param token - JWT to verify.
   * @returns Result of the verification (valid + data).
   */
  async verify<T = any>(token: string): Promise<VerifyResponse<T>> {
    try {
      const data = await this.verifier.verify<T>(token);
      return {
        valid: true,
        data,
      };
    } catch (error: any) {
      return {
        valid: false,
        data: null as any,
      };
    }
  }
}
