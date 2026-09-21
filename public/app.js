const DEFAULT_STOP={id:'de-DELFI_de:04011:13927_G',name:'Bremen Hauptbahnhof'};
const $=id=>document.getElementById(id);
const escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const read=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}};
const save=(key,value)=>{try{localStorage.setItem(key,JSON.stringify(value))}catch{}};
let selectedStop=read('selected-stop',DEFAULT_STOP);
if(!selectedStop?.id||!selectedStop?.name)selectedStop=DEFAULT_STOP;
let latestData=null,stale=false,inFlight=null,searchRequest=null,nextRefreshAt=Date.now();
const formatTime=v=>new Intl.DateTimeFormat('de-DE',{timeZone:'Europe/Berlin',hour:'2-digit',minute:'2-digit'}).format(new Date(v));

/* ---------- Favoriten ---------- */
let favorites=read('checkit-favorites',null);
if(!Array.isArray(favorites)||!favorites.length){favorites=[{id:selectedStop.id,name:selectedStop.name}];save('checkit-favorites',favorites);}
const isFavorite=id=>favorites.some(f=>f.id===id);
function addFavorite(stop){if(!isFavorite(stop.id)){favorites=[...favorites,{id:stop.id,name:stop.name}];save('checkit-favorites',favorites);}renderFavorites();}
function removeFavorite(id){favorites=favorites.filter(f=>f.id!==id);save('checkit-favorites',favorites);renderFavorites();}
function selectStop(stop){selectedStop={id:stop.id,name:stop.name};save('selected-stop',selectedStop);latestData=read('snapshot:'+selectedStop.id,null);stale=!!latestData;renderFavorites();refresh();}
function renderFavorites(){
 const list=$('favorites-list');
 list.innerHTML=favorites.map(f=>`<div class="favorite-item${f.id===selectedStop.id?' active':''}"><button class="favorite-name" type="button" data-id="${escapeHtml(f.id)}">${escapeHtml(f.name)}</button><button class="favorite-remove" type="button" data-id="${escapeHtml(f.id)}" aria-label="Favorit entfernen">✕</button></div>`).join('')||'<div class="empty" style="padding:12px">Noch keine Favoriten gespeichert.</div>';
 list.querySelectorAll('.favorite-name').forEach(b=>b.addEventListener('click',()=>{const f=favorites.find(x=>x.id===b.dataset.id);if(!f)return;$('favorites-panel').classList.add('hidden');selectStop(f);}));
 list.querySelectorAll('.favorite-remove').forEach(b=>b.addEventListener('click',()=>removeFavorite(b.dataset.id)));
 const add=$('favorite-add');
 add.disabled=isFavorite(selectedStop.id);
 add.textContent=isFavorite(selectedStop.id)?'✓ Als Favorit gespeichert':'＋ Aktuelle Haltestelle speichern';
}

/* ---------- Abfahrts-Alarm ---------- */
let alarms=read('checkit-alarms',[]);
if(!Array.isArray(alarms))alarms=[];
alarms=alarms.filter(a=>a&&a.fireAt>Date.now());
save('checkit-alarms',alarms);
const alarmTimers=new Map();
const alarmFor=id=>alarms.find(a=>a.id===id);
const persistAlarms=()=>save('checkit-alarms',alarms);
function clearAlarmTimer(id){const t=alarmTimers.get(id);if(t){clearTimeout(t);alarmTimers.delete(id);}}
function armAlarmTimer(alarm){clearAlarmTimer(alarm.id);const delay=alarm.fireAt-Date.now();if(delay<=0){fireAlarm(alarm.id);return;}alarmTimers.set(alarm.id,setTimeout(()=>fireAlarm(alarm.id),delay));}
function scheduleAlarm(dep,minutesBefore){
 const depTime=Date.parse(dep.time.realtime||dep.time.scheduled);
 const fireAt=depTime-minutesBefore*60000;
 if(!(fireAt>Date.now()+20000)){$('status').textContent='Dafür ist es zu spät – die Abfahrt steht kurz bevor.';return;}
 cancelAlarm(dep.id,true);
 const alarm={id:dep.id,line:dep.line.label,destination:dep.destination,stopId:selectedStop.id,stopName:selectedStop.name,fireAt,minutesBefore};
 alarms.push(alarm);persistAlarms();armAlarmTimer(alarm);render();
 $('status').textContent=`⏰ Erinnerung aktiv: ${alarm.line} nach ${alarm.destination}, ${minutesBefore} Min. vorher.`;
}
function cancelAlarm(id,silent){clearAlarmTimer(id);alarms=alarms.filter(a=>a.id!==id);persistAlarms();if(!silent)render();}
function fireAlarm(id){
 const alarm=alarmFor(id);if(!alarm)return;
 cancelAlarm(id,true);render();
 const body=`${alarm.line} nach ${alarm.destination} fährt in ${alarm.minutesBefore} Minuten ab · ${alarm.stopName}`;
 if('Notification'in window&&Notification.permission==='granted'){try{new Notification('Checkit ⏰',{body});}catch{}}
 $('status').textContent='⏰ '+body;
}
let popoverDep=null;
function openAlarmPopover(dep,anchor){
 popoverDep=dep;
 const pop=$('alarm-popover');
 const r=anchor.getBoundingClientRect();
 pop.classList.remove('hidden');
 const pw=pop.offsetWidth;
 pop.style.left=Math.max(8,Math.min(window.innerWidth-pw-8,r.right-pw))+'px';
 pop.style.top=(r.bottom+6)+'px';
}
document.addEventListener('click',e=>{
 const pop=$('alarm-popover');
 if(!pop.classList.contains('hidden')&&!e.target.closest('#alarm-popover')&&!e.target.closest('.alarm-btn'))pop.classList.add('hidden');
});
$('alarm-popover').querySelectorAll('button').forEach(b=>b.addEventListener('click',async()=>{
 $('alarm-popover').classList.add('hidden');
 const dep=popoverDep;if(!dep)return;
 if('Notification'in window&&Notification.permission==='default'){try{await Notification.requestPermission();}catch{}}
 if('Notification'in window&&Notification.permission==='denied'){$('status').textContent='Bitte Benachrichtigungen für diese Seite erlauben, sonst bleibt die Erinnerung stumm.';}
 scheduleAlarm(dep,Number(b.dataset.minutes));
}));
$('departures').addEventListener('click',e=>{
 const btn=e.target.closest('.alarm-btn');if(!btn)return;
 const dep=(latestData?.departures||[]).find(d=>d.id===btn.dataset.id);if(!dep)return;
 if(alarmFor(dep.id)){cancelAlarm(dep.id);$('status').textContent='⏰ Erinnerung gelöscht.';}
 else openAlarmPopover(dep,btn);
});

/* ---------- Board ---------- */
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
  const hasAlarm=!!alarmFor(d.id);
  return `<article class="departure-row${d.cancelled?' cancelled':''}"><div class="line-cell"><span class="line-badge" style="--line-color:${color}">${escapeHtml(d.line.label)}</span><span class="mode">${escapeHtml(d.transportMode)}</span></div><div class="destination-cell"><strong title="${escapeHtml(d.destination)}">${escapeHtml(d.destination)}</strong><small>${status}</small></div><div class="platform-cell">${escapeHtml(d.stop.platform||'—')}</div><div class="departure-cell"><strong>${d.cancelled?'—':minutes+' min'}</strong><small>${formatTime(date)}</small><button class="alarm-btn${hasAlarm?' active':''}" type="button" data-id="${escapeHtml(d.id)}" aria-label="Abfahrts-Erinnerung" title="${hasAlarm?'Erinnerung aktiv – tippen zum Löschen':'An diese Abfahrt erinnern lassen'}">⏰</button></div></article>`;
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
$('search-toggle').addEventListener('click',()=>{$('search-form').classList.toggle('hidden');$('favorites-panel').classList.add('hidden');if(!$('search-form').classList.contains('hidden'))$('search-input').focus()});
$('favorites-toggle').addEventListener('click',()=>{$('favorites-panel').classList.toggle('hidden');$('search-form').classList.add('hidden');renderFavorites();});
$('favorite-add').addEventListener('click',()=>addFavorite(selectedStop));
$('search-form').addEventListener('submit',async e=>{
 e.preventDefault();const q=$('search-input').value.trim();if(!q)return;
 searchRequest?.abort();const request=new AbortController();searchRequest=request;
 const results=$('search-results');results.textContent='Suche …';
 try{
  const response=await fetch('/api/stops/search?'+new URLSearchParams({q}),{signal:request.signal});const payload=await response.json();if(!response.ok)throw Error(payload.error);
  if(searchRequest!==request)return;
  results.innerHTML=payload.results.map(s=>`<button class="search-result" type="button" data-id="${escapeHtml(s.id)}" data-name="${escapeHtml(s.name)}">${escapeHtml(s.name)}<small>${escapeHtml(s.locality)}</small></button>`).join('')||'Keine Haltestelle gefunden. Anderen Namen versuchen.';
  results.querySelectorAll('button').forEach(b=>b.addEventListener('click',()=>{selectStop({id:b.dataset.id,name:b.dataset.name});$('search-form').classList.add('hidden');results.replaceChildren();}));
 }catch(error){if(searchRequest===request)results.textContent='Suche fehlgeschlagen. Bitte erneut versuchen.';}
});
$('refresh').addEventListener('click',refresh);
latestData=read('snapshot:'+selectedStop.id,null);if(latestData){stale=true;render()}
alarms.forEach(armAlarmTimer);
renderFavorites();
refresh();setInterval(()=>{if(!document.hidden&&!inFlight)refresh()},60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh()});
setInterval(()=>{const now=new Date();$('clock').textContent=formatTime(now);$('date').textContent=now.toLocaleDateString('de-DE',{timeZone:'Europe/Berlin',weekday:'short',day:'2-digit',month:'short'});$('next-refresh').textContent=`Aktualisierung in ${Math.max(0,Math.ceil((nextRefreshAt-Date.now())/1000))} s`;render()},1000);
if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});
