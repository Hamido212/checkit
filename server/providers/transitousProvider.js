import { TransitProvider } from "./transitProvider.js";

const API = "https://api.transitous.org/api";

function boolean(value) {
  return value === true || value === "true";
}

export class TransitousProvider extends TransitProvider {
  constructor({ userAgent, fetchImpl = fetch, cache }) {
    super();
    this.userAgent = userAgent;
    this.fetchImpl = fetchImpl;
    this.cache = cache;
  }

  async request(path, ttl) {
    const cached = this.cache.get(path);
    if (cached) return cached;
    const response = await this.fetchImpl(`${API}${path}`, {
      signal: AbortSignal.timeout(12000),
      headers: { "user-agent": this.userAgent, accept: "application/json" }
    });
    if (!response.ok) throw new Error(`Transitous antwortete mit HTTP ${response.status}`);
    const data = await response.json();
    this.cache.set(path, data, ttl);
    return data;
  }

  async searchStops(query) {
    const data = await this.request(
      `/v1/geocode?text=${encodeURIComponent(query)}&type=STOP`,
      120_000
    );
    return (Array.isArray(data) ? data : []).filter((stop) => stop?.id && stop?.name)
      .slice(0, 8)
      .map((stop) => ({
        id: stop.id,
        name: stop.name,
        locality: stop.areas?.find((area) => area.adminLevel === 6)?.name || "",
        lat: stop.lat,
        lon: stop.lon
      }));
  }

  async getDepartures(stopId) {
    const data = await this.request(
      `/v6/stoptimes?stopId=${encodeURIComponent(stopId)}&n=12&language=de&withAlerts=true`,
      45_000
    );
    return {
      stop: data.stop,
      stopTimes: data.stopTimes || []
    };
  }
}

export { boolean };
