import { MemoryCache } from './services/memoryCache.js';
import { DepartureService } from './services/departureService.js';
import { StopService } from './services/stopService.js';
import { TransitousProvider } from './providers/transitousProvider.js';
const provider = new TransitousProvider({ userAgent: process.env.USER_AGENT || 'Checkit/0.2 (+https://github.com/Hamido212/checkit)', cache: new MemoryCache() });
const departures = new DepartureService(provider);
const stops = new StopService(provider);
export const DEFAULT_STOP = { id: 'de-DELFI_de:04011:13927_G', name: 'Bremen Hauptbahnhof' };
export async function handleApi(request, response) {
  const url = new URL(request.url, 'http://localhost');
  const send = (status, body) => {
    response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'access-control-allow-origin': '*' });
    response.end(JSON.stringify(body));
  };
  if (request.method !== 'GET') return send(405, { error: 'Nur GET wird unterstützt.' });
  try {
    if (url.pathname === '/api/health') return send(200, { ok: true, provider: 'transitous', version: '0.2.0' });
    if (['/api/search', '/api/stops/search'].includes(url.pathname)) {
      const q = url.searchParams.get('q')?.trim();
      if (!q || q.length > 120) return send(400, { error: 'Bitte einen Suchbegriff mit 1–120 Zeichen angeben.' });
      const results = await stops.search(q);
      return send(200, url.pathname === '/api/search' ? results : { results });
    }
    if (url.pathname === '/api/departures') {
      const stop = { id: url.searchParams.get('stopId') || DEFAULT_STOP.id, name: url.searchParams.get('stopName') || DEFAULT_STOP.name };
      if (stop.id.length > 240 || stop.name.length > 160) return send(400, { error: 'Ungültige Haltestelle.' });
      return send(200, await departures.getDepartures(stop));
    }
    return send(404, { error: 'Endpunkt nicht gefunden.' });
  } catch { return send(502, { error: 'Verkehrsdaten sind gerade nicht erreichbar. Bitte erneut versuchen.' }); }
}
