export interface DataVerifier {
  verify<T>(token: string): Promise<T>;
}
