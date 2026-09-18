export class StopService {
  constructor(provider) {
    this.provider = provider;
  }

  search(query) {
    return this.provider.searchStops(query);
  }
}
