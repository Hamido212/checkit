const escape = s => String(s ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const time = value => new Date(value).toLocaleTimeString('de-DE',{timeZone:'Europe/Berlin',hour:'2-digit',minute:'2-digit'});
let last;
const remaining = value => Math.max(0,Math.ceil((Date.parse(value)-Date.now())/60000)) + ' <small>min</small>';
function renderRows(){if(!last)return;document.querySelector('#demo-rows').innerHTML=last.departures.filter(d=>Date.parse(d.time.realtime||d.time.scheduled)>Date.now()-60000).slice(0,4).map(d=>`<div class="demo-row"><span class="demo-line">${escape(d.line.label)}</span><span class="demo-destination">${escape(d.destination)}</span><span class="demo-time">${d.cancelled?'AUS':remaining(d.time.realtime||d.time.scheduled)}</span></div>`).join('')||'<p class="loading-copy">Aktuell keine Abfahrten.</p>'}
async function update(){
  try{
    const response=await fetch('/api/departures',{signal:AbortSignal.timeout(15000)});
    if(!response.ok)throw Error();
    last=await response.json();
    document.querySelector('#demo-station').textContent=last.station.name;
    document.querySelector('#demo-status').textContent=`${last.departures.some(d=>d.realtime)?'ECHTZEIT':'FAHRPLAN'} · STAND ${time(last.generatedAt)}`;
    renderRows();
    document.querySelector('#demo-clock').textContent=time(last.generatedAt);
  }catch{document.querySelector('#demo-status').textContent=last?'VERALTET · BITTE DATENSTAND BEACHTEN':'OFFLINE · GERADE KEINE VERKEHRSDATEN';if(!last)document.querySelector('#demo-rows').innerHTML='<p class="loading-copy">Die Datenquelle ist gerade nicht erreichbar.<br>Wir versuchen es automatisch erneut.</p>';}
}
update();setInterval(()=>{if(!document.hidden)update()},60000);
fetch('/downloads.json').then(r=>r.json()).then(downloads=>{for(const [platform,url]of Object.entries(downloads)){if(url && (url.startsWith('/downloads/')||url.startsWith('https://github.com/Hamido212/checkit/releases/')))document.querySelector(`[data-download="${platform}"]`)?.setAttribute('href',url)}}).catch(()=>{});
if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});

setInterval(renderRows,1000);
