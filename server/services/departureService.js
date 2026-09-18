import { normalizeDepartures } from "../normalizers/departureNormalizer.js";

export class DepartureService {
  constructor(provider) {
    this.provider = provider;
  }

  async getDepartures(stop) {
    const raw = await this.provider.getDepartures(stop.id);
    return normalizeDepartures(raw, {
      id: stop.id,
      name: stop.name || raw.stop?.name || "Unbekannte Haltestelle"
    });
  }
}
