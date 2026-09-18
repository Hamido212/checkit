const DEFAULT_STOP={id:'de-DELFI_de:04011:13927_G',name:'Bremen Hauptbahnhof'};
const $=id=>document.getElementById(id);
const escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const read=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}};
const save=(key,value)=>{try{localStorage.setItem(key,JSON.stringify(value))}catch{}};
let selectedStop=read('selected-stop',DEFAULT_STOP);
if(!selectedStop?.id||!selectedStop?.name)selectedStop=DEFAULT_STOP;
let latestData=null,stale=false,inFlight=null,searchRequest=null,nextRefreshAt=Date.now();
const formatTime=v=>new Intl.DateTimeFormat('de-DE',{timeZone:'Europe/Berlin',hour:'2-digit',minute:'2-digit'}).format(new Date(v));
function render(){
 if(!latestData)return;
 $('station-name').textContent=selectedStop.name;
 $('updated-label').textContent=`Datenstand ${formatTime(latestData.generatedAt)}`;
 const aged=Date.now()-Date.parse(latestData.generatedAt)>120000;
 $('source-label').textContent=stale||aged?'VERALTET':latestData.departures.some(d=>d.realtime)?'ECHTZEIT':'FAHRPLAN';
 $('live-dot').classList.toggle('offline',stale||aged);
 const items=latestData.departures.filter(d=>Date.parse(d.time.realtime||d.time.scheduled)>Date.now()-60000).slice(0,8);
 $('departures').innerHTML=items.map(d=>{
  const date=d.time.realtime||d.time.scheduled;
  const minutes=Math.max(0,Math.ceil((Date.parse(date)-Date.now())/60000));
  const color=/^#[0-9a-f]{6}$/i.test(d.line.color||'')?d.line.color:'#5f666e';
  const status=d.cancelled?'<span class="danger">FÄLLT AUS</span>':d.time.delayMinutes>0?`<span class="delay">+${d.time.delayMinutes} min · ${d.realtime?'Echtzeit':'Fahrplan'}</span>`:`<span class="on-time">${d.realtime?'● Echtzeit':'Fahrplan'}</span>`;
  return `<article class="departure-row${d.cancelled?' cancelled':''}"><div class="line-cell"><span class="line-badge" style="--line-color:${color}">${escapeHtml(d.line.label)}</span><span class="mode">${escapeHtml(d.transportMode)}</span></div><div class="destination-cell"><strong title="${escapeHtml(d.destination)}">${escapeHtml(d.destination)}</strong><small>${status}</small></div><div class="platform-cell">${escapeHtml(d.stop.platform||'—')}</div><div class="departure-cell"><strong>${d.cancelled?'—':minutes+' min'}</strong><small>${formatTime(date)}</small></div></article>`;
 }).join('')||'<div class="empty">Keine aktuellen Abfahrten im Datenstand. Bitte aktualisieren.</div>';
}
async function refresh(){
 inFlight?.abort();const request=new AbortController();inFlight=request;
 const stop={...selectedStop};
 $('refresh').classList.add('spinning');$('station-name').textContent=stop.name;
 if(!latestData){$('source-label').textContent='VERBINDE';$('departures').innerHTML='<div class="empty">Abfahrten werden geladen …</div>';}
 const timeout=setTimeout(()=>request.abort(),15000);
 try{
  const response=await fetch('/api/departures?'+new URLSearchParams({stopId:stop.id,stopName:stop.name}),{signal:request.signal});
  const data=await response.json();if(!response.ok)throw Error(data.error);
  if(inFlight!==request)return;
  latestData=data;stale=false;save('snapshot:'+stop.id,data);
  $('status').textContent='Nächste Abfahrten';render();
 }catch(error){
  if(inFlight!==request)return;
  stale=true;$('status').textContent=latestData?'Aktualisierung gestört · letzter Stand':'Verkehrsdaten sind momentan nicht erreichbar.';
  if(latestData)render();else{$('source-label').textContent='OFFLINE';$('live-dot').classList.add('offline');$('departures').innerHTML='<div class="empty">Bitte Verbindung prüfen und erneut aktualisieren.</div>';}
 }finally{clearTimeout(timeout);if(inFlight===request){inFlight=null;nextRefreshAt=Date.now()+60000;$('refresh').classList.remove('spinning');}}
}
$('search-toggle').addEventListener('click',()=>{$('search-form').classList.toggle('hidden');if(!$('search-form').classList.contains('hidden'))$('search-input').focus()});
$('search-form').addEventListener('submit',async e=>{
 e.preventDefault();const q=$('search-input').value.trim();if(!q)return;
 searchRequest?.abort();const request=new AbortController();searchRequest=request;
 const results=$('search-results');results.textContent='Suche …';
 try{
  const response=await fetch('/api/stops/search?'+new URLSearchParams({q}),{signal:request.signal});const payload=await response.json();if(!response.ok)throw Error(payload.error);
  if(searchRequest!==request)return;
  results.innerHTML=payload.results.map(s=>`<button class="search-result" type="button" data-id="${escapeHtml(s.id)}" data-name="${escapeHtml(s.name)}">${escapeHtml(s.name)}<small>${escapeHtml(s.locality)}</small></button>`).join('')||'Keine Haltestelle gefunden. Anderen Namen versuchen.';
  results.querySelectorAll('button').forEach(b=>b.addEventListener('click',()=>{selectedStop={id:b.dataset.id,name:b.dataset.name};save('selected-stop',selectedStop);latestData=read('snapshot:'+selectedStop.id,null);stale=!!latestData;$('search-form').classList.add('hidden');results.replaceChildren();refresh()}));
 }catch(error){if(searchRequest===request)results.textContent='Suche fehlgeschlagen. Bitte erneut versuchen.';}
});
$('refresh').addEventListener('click',refresh);
latestData=read('snapshot:'+selectedStop.id,null);if(latestData){stale=true;render()}
refresh();setInterval(()=>{if(!document.hidden&&!inFlight)refresh()},60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh()});
setInterval(()=>{const now=new Date();$('clock').textContent=formatTime(now);$('date').textContent=now.toLocaleDateString('de-DE',{timeZone:'Europe/Berlin',weekday:'short',day:'2-digit',month:'short'});$('next-refresh').textContent=`Aktualisierung in ${Math.max(0,Math.ceil((nextRefreshAt-Date.now())/1000))} s`;render()},1000);
if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});

