import { TransitProvider } from "./transitProvider.js";

export class VbnProvider extends TransitProvider {
  async searchStops() {
    throw new Error("Der VBN-Provider ist für eine spätere Bremen-Version vorbereitet.");
  }

  async getDepartures() {
    throw new Error("Der VBN-Provider ist für eine spätere Bremen-Version vorbereitet.");
  }
}
