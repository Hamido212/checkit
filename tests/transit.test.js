import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeDeparture, normalizeDepartures } from '../server/normalizers/departureNormalizer.js';
import { TransitousProvider } from '../server/providers/transitousProvider.js';
import { MemoryCache } from '../server/services/memoryCache.js';
import { server } from '../server.js';
test('delay, cancellation, planned data and colors survive normalization',()=>{
 const result=normalizeDeparture({routeShortName:'6',routeColor:'bad;style',realTime:false,tripCancelled:true,place:{scheduledDeparture:'2026-09-17T10:00:00Z',departure:'2026-09-17T10:07:00Z'}});
 assert.equal(result.time.delayMinutes,7);assert.equal(result.realtime,false);assert.equal(result.cancelled,true);assert.equal(result.line.color,null);
});
test('invalid departures removed; upcoming sorted by actual time',()=>{
 const result=normalizeDepartures({stopTimes:[{place:{}},{place:{departure:'2026-09-17T12:00:00Z'}},{place:{departure:'2026-09-17T11:00:00Z'}}]}, {id:'stop',name:'Stop'});
 assert.equal(result.departures.length,2);assert.equal(result.departures[0].time.realtime,'2026-09-17T11:00:00Z');
});
test('provider caches requests and does not fabricate data when upstream fails',async()=>{
 let calls=0;const provider=new TransitousProvider({userAgent:'test',cache:new MemoryCache(),fetchImpl:async()=>{calls++;return {ok:true,json:async()=>({stopTimes:[]})}}});
 await provider.getDepartures('bremen');await provider.getDepartures('bremen');assert.equal(calls,1);
 const failing=new TransitousProvider({cache:new MemoryCache(),fetchImpl:async()=>({ok:false,status:503})});await assert.rejects(()=>failing.getDepartures('a'),/503/);
});
test('HTTP API validates queries, methods, missing routes and serves website',async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
 try {assert.equal((await fetch(base+'/api/stops/search')).status,400);assert.equal((await fetch(base+'/api/health',{method:'POST'})).status,405);assert.equal((await fetch(base+'/api/unknown')).status,404);assert.equal((await fetch(base+'/api/health')).status,200);assert.match(await(await fetch(base+'/')).text(),/Deine Haltestelle/);assert.equal((await fetch(base+'/%2e%2e%2fpackage.json')).status,404);}finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
