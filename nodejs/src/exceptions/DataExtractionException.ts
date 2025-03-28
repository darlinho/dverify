export class DataExtractionException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DataExtractionException';
  }
}
