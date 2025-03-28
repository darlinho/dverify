export interface DataSigner {
  /**
   * Signe les données avec une durée d'expiration (en secondes)
   * @param data Données à signer
   * @param duration Durée de validité du token (en secondes)
   * @returns Un JWT signé
   */
  sign(data: any, duration: number): Promise<string>;
}
