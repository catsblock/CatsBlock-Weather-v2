package com.catsblock.weather.ui

internal object MapPage {

    const val BASE_URL = "https://github.com/catsblock/CatsBlock-Weather-v2/"

    private val html: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" onerror="this.onerror=null;this.href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css'">
<style>
:root{--top:0px;--bottom:0px}
body.light{--bg:#eef0f5;--fg:#1b1d24;--chip:rgba(255,255,255,.92);--sub:#5b6070;--edge:rgba(0,0,0,.08)}
body.dark{--bg:#14161c;--fg:#e9ebf2;--chip:rgba(32,35,44,.92);--sub:#a5abbb;--edge:rgba(255,255,255,.1)}
html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:var(--bg);font-family:sans-serif;-webkit-tap-highlight-color:transparent}
#map{position:absolute;inset:0;background:var(--bg);overflow:hidden;contain:layout paint}
.leaflet-marker-pane,.leaflet-marker-icon{overflow:visible}
html.round,html.round body{background:transparent}
html.round #map{border-radius:4px;overflow:hidden;isolation:isolate}
.enso-label{background:var(--chip);border:1px solid var(--edge);border-radius:2px;color:var(--fg);box-shadow:none;font:600 11px sans-serif;padding:3px 5px}.fc{position:absolute;left:0;top:0;pointer-events:none;transition:opacity .35s;max-width:none;max-height:none}
.fc.blur{filter:blur(7px)}
#legend{position:absolute;z-index:1000;left:50%;top:calc(var(--top) + 12px);transform:translate(-50%,-140%);opacity:0;min-width:176px;max-width:84%;padding:10px 16px 12px;border-radius:4px;background:var(--chip);color:var(--fg);box-shadow:none;border:1px solid var(--edge);transition:transform .5s cubic-bezier(.2,0,0,1),opacity .35s;box-sizing:border-box}
#legend.on{transform:translate(-50%,0);opacity:1}
#legend .t{font-size:14px;font-weight:600;text-align:center;white-space:nowrap}
#legend .m{font-size:12px;color:var(--sub);text-align:center;margin-top:4px}
#scale{position:absolute;z-index:1000;left:12px;top:50%;transform:translate(-170%,-50%);opacity:0;display:flex;flex-direction:column;align-items:center;gap:9px;padding:14px 10px;border-radius:4px;background:var(--chip);color:var(--fg);box-shadow:none;border:1px solid var(--edge);transition:transform .5s cubic-bezier(.2,0,0,1),opacity .35s}
#scale.on{transform:translate(0,-50%);opacity:1}
#scale .bar{width:14px;height:200px;border-radius:2px;background:var(--grad)}
#scale span{font-size:11px;font-weight:700;white-space:nowrap}
#scale.busy .bar{animation:bp .9s ease-in-out infinite alternate}
@keyframes bp{to{opacity:.35}}
.leaflet-bottom{margin-bottom:var(--bottom)}
.leaflet-control-attribution{background:var(--chip)!important;color:var(--sub)!important;font-size:10px;border-radius:10px 0 0 0;padding:2px 8px}
#note{position:absolute;z-index:1100;left:50%;top:50%;transform:translate(-50%,-50%);display:none;padding:12px 20px;border-radius:20px;background:var(--chip);color:var(--fg);font-size:14px;font-weight:600;box-shadow:0 4px 18px rgba(0,0,0,.25);text-align:center;max-width:80%}
.dim .leaflet-basepane-pane{filter:invert(1) hue-rotate(180deg) brightness(.82) contrast(.92)}
.pin{width:22px;height:22px;border-radius:50%;background:#2979ff;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.4);position:relative}
.pin:after{content:"";position:absolute;inset:-12px;border-radius:50%;background:rgba(41,121,255,.28);animation:pp 2s ease-out infinite}
@keyframes pp{0%{transform:scale(.4);opacity:1}100%{transform:scale(1.5);opacity:0}}
.leaflet-popup-content-wrapper{background:var(--chip);color:var(--fg);border-radius:16px;box-shadow:0 4px 18px rgba(0,0,0,.3)}
.leaflet-popup-tip{background:var(--chip)}
.leaflet-popup-content{margin:10px 14px;font-size:13px;line-height:1.45}
.cyw{background:none;border:0}
.cy{position:relative;width:var(--s);height:var(--s)}
.cy .bd{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:calc(var(--s) * .21);line-height:1;letter-spacing:-.02em;text-shadow:0 1px 2px rgba(0,0,0,.6)}
.cy svg{position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;filter:drop-shadow(0 2px 5px rgba(0,0,0,.45));animation:cs 16s linear infinite}
.cy.cys svg{animation-direction:reverse}
@keyframes cs{from{transform:rotate(0deg)}to{transform:rotate(-360deg)}}
.cy:after{content:"";position:absolute;inset:-6px;border-radius:50%;border:1.5px solid var(--c);opacity:0;animation:cp 3s ease-out infinite}
.cn{position:absolute;left:50%;top:100%;transform:translate(-50%,8px);white-space:nowrap;max-width:60vw;overflow:hidden;text-overflow:ellipsis;pointer-events:none;display:flex;align-items:center;gap:5px;padding:3px 9px 3px 6px;border-radius:12px;background:var(--chip);color:var(--fg);font-size:11px;font-weight:700;box-shadow:0 2px 8px rgba(0,0,0,.28);border:1px solid var(--edge)}
.cn i{width:8px;height:8px;border-radius:50%;background:var(--c);box-shadow:0 0 6px var(--c)}
@keyframes cp{0%{transform:scale(.8);opacity:.7}100%{transform:scale(1.6);opacity:0}}
</style>
</head>
<body class="light">
<div id="map"></div>
<div id="legend"></div>
<div id="scale"></div>
<div id="note"></div>
<svg width="0" height="0" style="position:absolute"><defs><pattern id="hz1" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="7" stroke="#ffb300" stroke-width="2.2"/></pattern><pattern id="hz2" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="7" stroke="#ff6d00" stroke-width="2.4"/></pattern><pattern id="hz3" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="6" stroke="#e53935" stroke-width="2.6"/></pattern></defs></svg>
<script>
var INIT = __INIT__;
(function () {
var noteEl = document.getElementById('note');
function note(t) { noteEl.textContent = t; noteEl.style.display = 'block'; }
function hideNote() { noteEl.style.display = 'none'; }
function loadScript(urls, i, ok, bad) {
  var el = document.createElement('script');
  el.src = urls[i];
  el.onload = ok;
  el.onerror = function () { if (i + 1 < urls.length) loadScript(urls, i + 1, ok, bad); else bad(); };
  document.head.appendChild(el);
}
window.onerror = function (m) { console.log('MapError ' + m); };
function start() {
var map = L.map('map', {zoomControl:false, attributionControl:false, minZoom:INIT.minZoom || 2, maxZoom:12, zoomSnap:0.25, worldCopyJump:true, maxBounds:L.latLngBounds([-85.0511, -100000], [85.0511, 100000]), maxBoundsViscosity:1.0, bounceAtZoomLimits:false}).setView([0, 0], 4);
L.control.attribution({position:'bottomright', prefix:false}).addTo(map)
  .addAttribution('© OpenStreetMap contributors · Open-Meteo · RainViewer · NASA GIBS · NASA FIRMS · NASA EONET · NIFC · Alert-Hub.org · US Drought Monitor · Natural Earth');

var state = {canvas:false, dark:false, layer:'none', title:'', err:'', f:false, lat:0, lon:0, time:Date.now() / 1000, seq:0, busy:false, labels:{}};
var base = null, overlay = null, pin = null, grid = null, gridAt = 0, gridZoom = 0, timer = null, off = null;
var cache = {storms:null, stormsAt:0, fires:null, firesAt:0};
var legend = document.getElementById('legend');
var scale = document.getElementById('scale');
var EONET = 'https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=500&category=';

var RAMPS = {
  temp: [[-30,[122,48,176]],[-15,[48,86,226]],[0,[34,196,226]],[10,[72,198,104]],[20,[250,222,52]],[30,[250,140,28]],[40,[226,52,40]],[50,[150,24,60]]],
  wind: [[0,[42,111,219]],[15,[38,198,218]],[30,[102,187,106]],[50,[253,216,53]],[75,[251,140,0]],[100,[211,47,47]],[140,[142,36,170]]],
  pressure: [[980,[123,50,148]],[995,[59,111,212]],[1005,[79,195,247]],[1013,[224,224,224]],[1022,[255,183,77]],[1030,[244,81,30]],[1045,[183,28,28]]],
  humidity: [[0,[235,245,255]],[20,[128,203,196]],[40,[38,166,154]],[60,[30,136,229]],[80,[21,101,192]],[100,[13,71,161]]],
  dew: [[-20,[110,60,170]],[-5,[60,100,210]],[5,[60,190,210]],[12,[90,200,110]],[18,[240,220,70]],[22,[250,150,40]],[26,[230,60,50]],[30,[150,20,60]]],
  uv: [[0,[67,160,71]],[3,[250,220,52]],[6,[251,140,0]],[8,[229,57,53]],[11,[142,36,170]],[14,[90,20,130]]],
  aqi: [[0,[0,228,0]],[50,[200,220,0]],[100,[255,126,0]],[150,[255,0,0]],[200,[153,0,76]],[300,[126,0,35]]],
  sea: [[-2,[33,102,172]],[-1,[103,169,207]],[0,[247,247,247]],[1,[253,174,97]],[2,[215,48,39]]]
};

var CATS = [
  {k:'td', kt:0, c:[79,195,247]},
  {k:'ts', kt:34, c:[102,187,106]},
  {k:'c1', kt:64, c:[253,216,53]},
  {k:'c2', kt:83, c:[255,179,0]},
  {k:'c3', kt:96, c:[251,140,0]},
  {k:'c4', kt:113, c:[229,57,53]},
  {k:'c5', kt:137, c:[213,0,249]}
];
var CAT_STOPS = CATS.map(function (c) { return [c.kt, c.c]; });

function ramp(stops, v) {
  if (v <= stops[0][0]) return stops[0][1];
  for (var i = 1; i < stops.length; i++) {
    if (v <= stops[i][0]) {
      var a = stops[i - 1], b = stops[i], t = (v - a[0]) / (b[0] - a[0]);
      return [a[1][0] + (b[1][0] - a[1][0]) * t, a[1][1] + (b[1][1] - a[1][1]) * t, a[1][2] + (b[1][2] - a[1][2]) * t];
    }
  }
  return stops[stops.length - 1][1];
}

function rgb(c) { return 'rgb(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ')'; }

function rgba(c, a) { return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',' + a + ')'; }

function gradCss(stops) {
  var lo = stops[0][0], hi = stops[stops.length - 1][0], out = [];
  for (var i = 0; i < stops.length; i++) {
    var c = stops[i][1];
    out.push('rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ') ' + Math.round((stops[i][0] - lo) / (hi - lo) * 100) + '%');
  }
  return 'linear-gradient(90deg,' + out.join(',') + ')';
}

function esc(s) {
  return String(s).replace(/[&<>"]/g, function (c) { return {'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;'}[c]; });
}

function lbl(k, d) { return state.labels && state.labels[k] ? state.labels[k] : d; }

function isGrid(id) { return !!GRID_KEY[id]; }

function fmtTemp(c) { return state.f ? Math.round(c * 9 / 5 + 32) + '°' : Math.round(c) + '°'; }

function setLegend(extra) {
  var id = state.layer;
  if (id === 'none') { legend.className = ''; scale.className = ''; return; }
  var grad = null, lo = '', hi = '';
  if (id === 'temp') { grad = gradCss(RAMPS.temp); lo = fmtTemp(-30); hi = fmtTemp(50); }
  else if (id === 'wind') { grad = gradCss(RAMPS.wind); lo = '0'; hi = '140 km/h'; }
  else if (id === 'pressure') { grad = gradCss(RAMPS.pressure); lo = '980'; hi = '1045 hPa'; }
  else if (id === 'humidity') { grad = gradCss(RAMPS.humidity); lo = '0%'; hi = '100%'; }
  else if (id === 'clouds') { grad = 'linear-gradient(90deg,rgba(150,160,180,0),rgba(235,240,250,.98))'; lo = '0%'; hi = '100%'; }
  else if (id === 'precip') {
    var stops = state.labels.radarStops || [[15,57330],[35,2653183],[45,8803800],[55,15555503],[65,16777215]];
    grad = gradCss(stops.map(function (s) { var c = s[1]; return [s[0],[(c >> 16) & 255,(c >> 8) & 255,c & 255]]; }));
    lo = '15 dBZ'; hi = '65+ dBZ';
  }
  else if (id === 'sst') { grad = 'linear-gradient(90deg,#2b6cb0,#e6eef7,#e53e3e)'; lo = '-3°'; hi = '+3°'; }
  else if (id === 'fires') { grad = 'linear-gradient(90deg,#ffb300,#ff5722,#d50000)'; lo = '·'; hi = '···'; }
  else if (id === 'cyclones') { grad = gradCss(CAT_STOPS); lo = 'TD'; hi = 'C5'; }
  else if (id === 'dew') { grad = gradCss(RAMPS.dew); lo = fmtTemp(-20); hi = fmtTemp(30); }
  else if (id === 'uv') { grad = gradCss(RAMPS.uv); lo = '0'; hi = '11+'; }
  else if (id === 'aqi') { grad = gradCss(RAMPS.aqi); lo = '0'; hi = '300+ AQI'; }
  else if (id === 'sea') { grad = gradCss(RAMPS.sea); lo = '-2 m'; hi = '+2 m'; }
  else if (id === 'alerts') { grad = hard([LEVEL_COLORS.warning, LEVEL_COLORS.alert, LEVEL_COLORS.danger]); lo = lbl('lvWarn', 'Warning'); hi = lbl('lvDanger', 'Danger'); }
  else if (id === 'drought') { grad = hard(DRY.map(rgb)); lo = lbl('d0', 'Slightly dry'); hi = lbl('d4', 'Extreme dry'); }
  var html = '<div class="t">' + esc(state.title) + '</div>';
  if (extra) html += '<div class="m">' + esc(extra).replace(/\n/g, '<br>') + '</div>';
  legend.innerHTML = html;
  legend.className = 'on';
  if (grad) {
    scale.innerHTML = '<span>' + hi + '</span><div class="bar" style="--grad:' + grad.replace('90deg', '0deg') + '"></div><span>' + lo + '</span>';
    scale.className = 'on' + (state.busy ? ' busy' : '');
  } else {
    scale.className = '';
  }
}

var tileOk = false, tileErrors = 0, fallbackOn = false;
var OSM_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
var FALLBACK_URL = 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png';
map.createPane('basepane').style.zIndex = 190;
var fieldPane = map.createPane('fieldpane');
fieldPane.style.zIndex = 250;
fieldPane.style.pointerEvents = 'none';
map.createPane('trackpane').style.zIndex = 420;
map.createPane('stormpane').style.zIndex = 640;

function makeBase() {
  if (base) map.removeLayer(base);
  base = L.tileLayer(fallbackOn ? FALLBACK_URL : OSM_URL, {maxZoom:19, pane:'basepane', subdomains:'abcd'});
  base.on('tileload', function () { tileOk = true; hideNote(); });
  base.on('tileerror', function () {
    tileErrors++;
    if (!tileOk && !fallbackOn && tileErrors >= 2) { fallbackOn = true; makeBase(); }
  });
  base.addTo(map);
  document.getElementById('map').classList.toggle('dim', state.dark);
}

function setTheme(dark, palette) {
  state.dark = dark;
  document.body.className = dark ? 'dark' : 'light';
  if (palette) {
    if (palette.bg) document.body.style.setProperty('--bg', palette.bg);
    if (palette.fg) document.body.style.setProperty('--fg', palette.fg);
    if (palette.chip) document.body.style.setProperty('--chip', palette.chip);
    if (palette.sub) document.body.style.setProperty('--sub', palette.sub);
    if (palette.edge) document.body.style.setProperty('--edge', palette.edge);
  }
  makeBase();
  if (overlay && overlay.refresh) overlay.refresh();
}

function setInsets(top, bottom) {
  document.documentElement.style.setProperty('--top', top + 'px');
  document.documentElement.style.setProperty('--bottom', bottom + 'px');
}

function setCity(lat, lon, fly) {
  state.lat = lat; state.lon = lon;
  if (!pin) pin = L.marker([lat, lon], {icon:L.divIcon({className:'', html:'<div class="pin"></div>', iconSize:[22, 22], iconAnchor:[11, 11]}), interactive:true, keyboard:false}).addTo(map);
  else pin.setLatLng([lat, lon]);
  pin.off('click');
  pin.on('click', function () { openDetail('municipality', {lat:state.lat, lon:state.lon}); });
  if (fly) map.flyTo([lat, lon], Math.max(map.getZoom(), 6), {duration:1.4});
  else map.setView([lat, lon], 6, {animate:false});
}

function locate() { map.flyTo([state.lat, state.lon], Math.max(map.getZoom(), 7), {duration:1.2}); }

function gibsOn(layer, level, day, opacity) {
  return L.tileLayer('https://gibs.earthdata.nasa.gov/wmts/epsg3857/all/' + layer + '/default/' + day + '/GoogleMapsCompatible_Level' + level + '/{z}/{y}/{x}.png', {maxNativeZoom:level, opacity:opacity, tileSize:256});
}

function gibs(layer, level, offset, opacity) {
  return gibsOn(layer, level, new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10), opacity);
}

var ENSO_REGIONS = {
  type:'FeatureCollection',
  name:'NOAA Climate Prediction Center ENSO monitoring regions',
  features:[
    {type:'Feature', properties:{id:'nino12', name:'Niño 1+2', latitude:'10°S–0°', longitude:'90°W–80°W', source:'NOAA PSL'}, geometry:{type:'Polygon', coordinates:[[[-90,-10],[-80,-10],[-80,0],[-90,0],[-90,-10]]]}},
    {type:'Feature', properties:{id:'nino3', name:'Niño 3', latitude:'5°S–5°N', longitude:'150°W–90°W', source:'NOAA PSL'}, geometry:{type:'Polygon', coordinates:[[[-150,-5],[-90,-5],[-90,5],[-150,5],[-150,-5]]]}},
    {type:'Feature', properties:{id:'nino34', name:'Niño 3.4', latitude:'5°S–5°N', longitude:'170°W–120°W', source:'NOAA PSL'}, geometry:{type:'Polygon', coordinates:[[[-170,-5],[-120,-5],[-120,5],[-170,5],[-170,-5]]]}},
    {type:'Feature', properties:{id:'nino4', name:'Niño 4', latitude:'5°S–5°N', longitude:'160°E–150°W', source:'NOAA PSL'}, geometry:{type:'MultiPolygon', coordinates:[[[[160,-5],[180,-5],[180,5],[160,5],[160,-5]]],[[[-180,-5],[-150,-5],[-150,5],[-180,5],[-180,-5]]]]}}
  ]
};

function addEnsoRegions() {
  var regions = L.geoJSON(ENSO_REGIONS, {
    pane:'trackpane',
    style:function () { return {color:state.dark ? '#f2f4f8' : '#303640', weight:1.2, opacity:0.82, fillColor:'#f6ad35', fillOpacity:0.055, dashArray:'4 4', interactive:false}; },
    onEachFeature:function (feature, layer) {
      var p = feature.properties;
      layer.bindTooltip(p.name, {permanent:false, direction:'center', className:'enso-label'});
      layer.bindPopup('<b>' + esc(p.name) + '</b><br>' + esc(p.latitude) + '<br>' + esc(p.longitude) + '<br>' + esc(lbl('source', 'Source')) + ': ' + esc(p.source), {closeButton:false});
    }
  });
  regions.addTo(map);
  extra = regions;
}

function loadSstLayer(seq) {
  var layerName = 'GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies_v4.1_STD';
  var offsets = [3, 4, 5, 7, 10], attempt = 0, active = null, fallbackTimer = null;
  addEnsoRegions();
  function useFrame() {
    if (seq !== state.seq) return;
    if (fallbackTimer) { clearTimeout(fallbackTimer); fallbackTimer = null; }
    if (attempt >= offsets.length) { state.busy = false; setLegend(state.err); return; }
    if (active) map.removeLayer(active);
    var candidate = gibs(layerName, 7, offsets[attempt], 0.82);
    active = candidate;
    var loaded = false;
    candidate.on('tileload', function () {
      if (seq !== state.seq || active !== candidate) return;
      loaded = true;
      state.busy = false;
      setLegend('');
    });
    candidate.on('tileerror', function () {
      if (seq !== state.seq || active !== candidate || loaded) return;
    });
    candidate.addTo(map);
    candidate.setZIndex(4);
    overlay = candidate;
    fallbackTimer = setTimeout(function () {
      if (seq !== state.seq || active !== candidate || loaded) return;
      attempt++;
      useFrame();
    }, 9000);
  }
  useFrame();
}

function fetchJsonWithTimeout(url, timeoutMs) {
  var controller = new AbortController();
  var timer = setTimeout(function () { controller.abort(); }, timeoutMs || 15000);
  return Promise.resolve().then(function () { return fetch(url, {signal:controller.signal}); }).then(function (response) {
    if (!response.ok) throw new Error('HTTP ' + response.status);
    return response.json();
  }).then(function (data) {
    clearTimeout(timer);
    return data;
  }, function (error) {
    clearTimeout(timer);
    throw error;
  });
}

function getJson(url) {
  return fetchJsonWithTimeout(url, 15000);
}

function fitCanvas(cv, m) {
  var s = m.getSize(), pad = Math.max(32, Math.min(256, Math.round(Math.min(s.x, s.y) * 0.25)));
  var w = s.x + pad * 2, h = s.y + pad * 2;
  var dpr = Math.min(2, Math.max(1, window.devicePixelRatio || 1));
  L.DomUtil.setPosition(cv, m.containerPointToLayerPoint([-pad, -pad]));
  cv.style.width = w + 'px';
  cv.style.height = h + 'px';
  if (cv.width !== Math.round(w * dpr) || cv.height !== Math.round(h * dpr)) {
    cv.width = Math.round(w * dpr);
    cv.height = Math.round(h * dpr);
  }
  var ctx = cv.getContext('2d', {alpha:true, desynchronized:true});
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  ctx.translate(pad, pad);
  return {ctx:ctx, pad:pad, x:s.x, y:s.y};
}

var FieldLayer = L.Layer.extend({
  onAdd: function (m) {
    this._fill = L.DomUtil.create('canvas', 'fc');
    this._fill.style.opacity = 0;
    m.getPane('fieldpane').appendChild(this._fill);
    this._flow = L.DomUtil.create('canvas', 'fc');
    this._flow.style.opacity = 0;
    m.getPane('fieldpane').appendChild(this._flow);
    this._hide = this._hide.bind(this);
    this.refresh = this.refresh.bind(this);
    m.on('moveend zoomend resize', this.refresh);
    m.on('zoomstart', this._hide);
    this.refresh();
  },
  onRemove: function (m) {
    this._flowStop();
    L.DomUtil.remove(this._flow);
    L.DomUtil.remove(this._fill);
    m.off('moveend zoomend resize', this.refresh);
    m.off('zoomstart', this._hide);
    if (this._raf) cancelAnimationFrame(this._raf);
    this._map = null;
  },
  _hide: function () {
    this._fill.style.opacity = 0;
    this._flowStop();
  },
  refresh: function () {
    var self = this;
    if (!self._map || self._raf) return;
    self._raf = requestAnimationFrame(function () { self._raf = 0; self._paint(); });
  },
  _paint: function () {
    var m = this._map;
    if (!m) return;
    var cv = this._fill, fit = fitCanvas(cv, m), ctx = fit.ctx, pad = fit.pad, size = {x:fit.x, y:fit.y, pad:pad};
    var id = state.layer;
    if (id !== 'wind' || !grid) this._flowStop();
    if (!grid || !isGrid(id)) return;
    var needsLand = id === 'sea' || id === 'drought';
    if (needsLand && !land) { cv.style.opacity = 0; return; }
    var arr = grid[GRID_KEY[id]];
    if (!arr) return;
    var step = id === 'drought' ? 4 : 5, sw = Math.ceil((size.x + pad * 2) / step), sh = Math.ceil((size.y + pad * 2) / step);
    if (!this._small) this._small = document.createElement('canvas');
    this._small.width = sw; this._small.height = sh;
    var sctx = this._small.getContext('2d'), img = sctx.createImageData(sw, sh);
    for (var y = 0; y < sh; y++) {
      for (var x = 0; x < sw; x++) {
        var ll = m.containerPointToLatLng([x * step + step / 2 - pad, y * step + step / 2 - pad]);
        var col = colorFor(id, sample(arr, ll.lat, lonFor(ll)), ll), o = (y * sw + x) * 4;
        img.data[o] = col[0]; img.data[o + 1] = col[1]; img.data[o + 2] = col[2]; img.data[o + 3] = col[3];
      }
    }
    sctx.putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = id !== 'drought';
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(this._small, -pad, -pad, sw * step, sh * step);
    if (id === 'sea') maskLand(ctx, false, size);
    else if (id === 'drought') maskLand(ctx, true, size);
    cv.style.opacity = 1;
    if (id === 'wind') this._flowStart(m, size);
  },
  _flowStop: function () {
    if (this._flowRaf) { cancelAnimationFrame(this._flowRaf); this._flowRaf = 0; }
    this._flowOn = false;
    if (this._flow) this._flow.style.opacity = 0;
  },
  _flowStart: function (m, size) {
    this._flowStop();
    ensureWindVectors(grid);
    var self = this, cv = this._flow, fit = fitCanvas(cv, m), pad = fit.pad;
    var W = size.x + pad * 2, H = size.y + pad * 2, C = 24;
    var cols = Math.ceil(W / C) + 1, rows = Math.ceil(H / C) + 1;
    var k = 0.9 * Math.max(0.6, Math.min(1.8, 1 + 0.12 * (m.getZoom() - 5)));
    var fu = new Float32Array(cols * rows), fv = new Float32Array(cols * rows);
    for (var gy = 0; gy < rows; gy++) {
      for (var gx = 0; gx < cols; gx++) {
        var ll = m.containerPointToLatLng([gx * C - pad, gy * C - pad]);
        var lon = lonFor(ll), u = sample(grid.u, ll.lat, lon), v = sample(grid.v, ll.lat, lon), o = gy * cols + gx;
        if (isNaN(u) || isNaN(v)) { fu[o] = NaN; fv[o] = NaN; } else { fu[o] = u * k; fv[o] = -v * k; }
      }
    }
    var count = Math.max(300, Math.min(1400, Math.round(W * H / 1100)));
    if (!this._parts || this._parts.length !== count) {
      this._parts = [];
      for (var i = 0; i < count; i++) this._parts.push({x:0, y:0, age:0, life:1});
      this._partsReady = false;
    }
    var parts = this._parts;
    function field(x, y, out) {
      var fx = Math.min(cols - 1.001, Math.max(0, x / C)), fy = Math.min(rows - 1.001, Math.max(0, y / C));
      var x0 = Math.floor(fx), y0 = Math.floor(fy), tx = fx - x0, ty = fy - y0;
      var a = y0 * cols + x0, b = a + 1, c = a + cols, d = c + 1;
      var w0 = (1 - tx) * (1 - ty), w1 = tx * (1 - ty), w2 = (1 - tx) * ty, w3 = tx * ty;
      out.u = fu[a] * w0 + fu[b] * w1 + fu[c] * w2 + fu[d] * w3;
      out.v = fv[a] * w0 + fv[b] * w1 + fv[c] * w2 + fv[d] * w3;
    }
    var vel = {u:0, v:0};
    function spawn(p, stagger) {
      for (var t = 0; t < 4; t++) {
        p.x = Math.random() * W; p.y = Math.random() * H;
        field(p.x, p.y, vel);
        if (!isNaN(vel.u)) break;
      }
      p.life = 1.4 + Math.random() * 2.2;
      p.age = stagger ? Math.random() * p.life : 0;
    }
    if (!this._partsReady) { for (var n = 0; n < parts.length; n++) spawn(parts[n], true); this._partsReady = true; }
    var dpr = Math.min(2, Math.max(1, window.devicePixelRatio || 1)), ctx = cv.getContext('2d'), last = 0;
    var ink = state.dark ? '255,255,255' : '14,28,58';
    this._flowOn = true;
    cv.style.opacity = 1;
    function frame(ts) {
      if (!self._flowOn) return;
      self._flowRaf = requestAnimationFrame(frame);
      var dt = last ? Math.min(0.05, (ts - last) / 1000) : 0.016;
      last = ts;
      ctx.setTransform(dpr, 0, 0, dpr, pad * dpr, pad * dpr);
      ctx.clearRect(-pad, -pad, W, H);
      ctx.lineCap = 'round';
      ctx.lineWidth = 1.5;
      for (var n = 0; n < parts.length; n++) {
        var p = parts[n];
        p.age += dt;
        if (p.age > p.life || p.x < 0 || p.y < 0 || p.x > W || p.y > H) { spawn(p, false); continue; }
        field(p.x, p.y, vel);
        if (isNaN(vel.u)) { spawn(p, false); continue; }
        var u = vel.u, v = vel.v, kmh = Math.sqrt(u * u + v * v) / k;
        p.x += u * dt; p.y += v * dt;
        var fade = Math.min(1, p.age / 0.4, (p.life - p.age) / 0.5);
        var alpha = fade * (0.3 + Math.min(0.6, kmh / 90));
        ctx.strokeStyle = 'rgba(' + ink + ',' + alpha.toFixed(3) + ')';
        ctx.beginPath();
        ctx.moveTo(p.x - u * 0.55, p.y - v * 0.55);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
      }
    }
    this._flowRaf = requestAnimationFrame(frame);
  }
});

function ensureWindVectors(g) {
  if (g.u && g.v) return;
  g.u = []; g.v = [];
  for (var i = 0; i < g.ws.length; i++) {
    var sp = g.ws[i], dr = g.wd[i];
    if (sp === null || sp === undefined || dr === null || dr === undefined || isNaN(sp) || isNaN(dr)) { g.u.push(null); g.v.push(null); continue; }
    var r = dr * Math.PI / 180;
    g.u.push(-sp * Math.sin(r));
    g.v.push(-sp * Math.cos(r));
  }
}

var ZoneLayer = L.Layer.extend({
  initialize: function (paint) { this._paintFn = paint; },
  onAdd: function (m) {
    this._cv = L.DomUtil.create('canvas', 'fc');
    this._cv.style.opacity = 0;
    m.getPane('fieldpane').appendChild(this._cv);
    this._hide = this._hide.bind(this);
    this.refresh = this.refresh.bind(this);
    m.on('moveend zoomend resize', this.refresh);
    m.on('zoomstart', this._hide);
    this.refresh();
  },
  onRemove: function (m) {
    L.DomUtil.remove(this._cv);
    m.off('moveend zoomend resize', this.refresh);
    m.off('zoomstart', this._hide);
    if (this._raf) cancelAnimationFrame(this._raf);
    this._map = null;
  },
  _hide: function () {
    this._cv.style.opacity = 0;
  },
  refresh: function () {
    var self = this;
    if (!self._map || self._raf) return;
    self._raf = requestAnimationFrame(function () {
      self._raf = 0;
      var m = self._map;
      if (!m) return;
      var cv = self._cv, fit = fitCanvas(cv, m);
      self._paintFn(fit.ctx, m, {x:fit.x, y:fit.y, pad:fit.pad});
      cv.style.opacity = 1;
    });
  }
});

function clearOverlay() {
  gridToken++;
  if (gridCtl) { gridCtl.abort(); gridCtl = null; }
  if (off) { off(); off = null; }
  if (overlay) { map.removeLayer(overlay); overlay = null; }
  if (extra) { map.removeLayer(extra); extra = null; }
  state.usdm = false;
  grid = null;
}

function toKt(v, u) {
  if (v === null || v === undefined || isNaN(v)) return 0;
  u = String(u || '').toLowerCase();
  if (u.indexOf('mph') >= 0) return v * 0.868976;
  if (u.indexOf('km') >= 0) return v / 1.852;
  if (u.indexOf('m/s') >= 0 || u === 'ms') return v * 1.94384;
  return v;
}

function catOf(kt) {
  var r = CATS[0];
  for (var i = 0; i < CATS.length; i++) if (kt >= CATS[i].kt) r = CATS[i];
  return r;
}

function cr(p0, p1, p2, p3, t) {
  var t2 = t * t, t3 = t2 * t;
  function f(a, b, c, d) { return 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3); }
  return {lat:f(p0.lat, p1.lat, p2.lat, p3.lat), lon:f(p0.lon, p1.lon, p2.lon, p3.lon), kt:p1.kt + (p2.kt - p1.kt) * t};
}

function smooth(pts) {
  var out = [];
  for (var i = 0; i < pts.length - 1; i++) {
    var p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
    for (var s = 0; s < 8; s++) out.push(cr(p0, p1, p2, p3, s / 8));
  }
  out.push(pts[pts.length - 1]);
  return out;
}

function badgeSize(kt) { return Math.round(44 + Math.min(1, Math.max(0, kt) / 140) * 20); }

var CY_BODY = 'M85.52,50C85.52,69.62 69.62,85.52 50,85.52C30.38,85.52 14.48,69.62 14.48,50C14.48,30.38 30.38,14.48 50,14.48C69.62,14.48 85.52,30.38 85.52,50ZM41.71,15.46C53.24,8.85 70.89,10.55 86.98,19.4C77.01,16.29 68.42,15.21 62.56,16.77ZM68.56,19.71C81.39,23.19 92.67,36.87 97.79,54.52C92.94,45.26 87.62,38.43 82.37,35.38ZM84.54,41.71C91.15,53.24 89.45,70.89 80.6,86.98C83.71,77.01 84.79,68.42 83.23,62.56ZM80.29,68.56C76.81,81.39 63.13,92.67 45.48,97.79C54.74,92.94 61.57,87.62 64.62,82.37ZM58.29,84.54C46.76,91.15 29.11,89.45 13.02,80.6C22.99,83.71 31.58,84.79 37.44,83.23ZM31.44,80.29C18.61,76.81 7.33,63.13 2.21,45.48C7.06,54.74 12.38,61.57 17.63,64.62ZM15.46,58.29C8.85,46.76 10.55,29.11 19.4,13.02C16.29,22.99 15.21,31.58 16.77,37.44ZM19.71,31.44C23.19,18.61 36.87,7.33 54.52,2.21C45.26,7.06 38.43,12.38 35.38,17.63Z';

function inkFor(c) { return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255 > 0.6 ? '#10141c' : '#ffffff'; }

function stormIcon(cat, kt, name, lat) {
  var c = ramp(CAT_STOPS, kt), s = badgeSize(kt);
  var svg = '<svg viewBox="0 0 100 100" aria-hidden="true"><path d="' + CY_BODY + '" fill="' + rgb(c) + '"/><circle cx="50" cy="50" r="16.5" fill="#10141c"/></svg>';
  var html = '<div class="cy' + (lat < 0 ? ' cys' : '') + '" style="--c:' + rgb(c) + ';--s:' + s + 'px">' + svg + '<div class="bd">' + esc(cat.k.toUpperCase()) + '</div><span class="cn"><i></i>' + esc(name) + '</span></div>';
  return L.divIcon({className:'cyw', html:html, iconSize:[s, s], iconAnchor:[s / 2, s / 2]});
}

function buildStorms(events) {
  var group = L.layerGroup(), halo = L.layerGroup(), lines = L.layerGroup(), marks = L.layerGroup(), count = 0, zones = [];
  var haloColor = state.dark ? 'rgba(255,255,255,.22)' : 'rgba(0,0,0,.22)';
  events.forEach(function (ev) {
    if (!/cyclone|hurricane|typhoon|tropical|depression/i.test(ev.title || '')) return;
    var geo = (ev.geometry || []).filter(function (g) {
      return g.type === 'Point' && g.coordinates && g.coordinates.length >= 2;
    });
    geo.sort(function (a, b) { return new Date(a.date) - new Date(b.date); });
    var pts = geo.map(function (g) { return {lat:g.coordinates[1], lon:g.coordinates[0], kt:toKt(g.magnitudeValue, g.magnitudeUnit)}; });
    if (!pts.length) return;
    for (var i = 1; i < pts.length; i++) {
      while (pts[i].lon - pts[i - 1].lon > 180) pts[i].lon -= 360;
      while (pts[i - 1].lon - pts[i].lon > 180) pts[i].lon += 360;
    }
    var trail = pts.length > 1 ? smooth(pts) : pts.slice();
    trail.forEach(function (q, idx) {
      var tip = idx === trail.length - 1;
      if (tip || idx % 2 === 0) zones.push({lat:q.lat, lon:q.lon, kt:q.kt, now:tip});
    });
    if (pts.length > 1) {
      var path = trail;
      for (var j = 0; j < path.length - 1; j++) {
        var a = path[j], b = path[j + 1], kt = (a.kt + b.kt) / 2;
        var w = 2.5 + Math.min(1, kt / 140) * 6.5;
        var ll = [[a.lat, a.lon], [b.lat, b.lon]];
        halo.addLayer(L.polyline(ll, {pane:'trackpane', color:haloColor, weight:w + 3, lineCap:'round', lineJoin:'round', interactive:false}));
        lines.addLayer(L.polyline(ll, {pane:'trackpane', color:rgb(ramp(CAT_STOPS, kt)), weight:w, opacity:0.95, lineCap:'round', lineJoin:'round', interactive:false}));
      }
    }
    var last = pts[pts.length - 1], cat = catOf(last.kt);
    var m = L.marker([last.lat, last.lon], {icon:stormIcon(cat, last.kt, ev.title, last.lat), pane:'stormpane', keyboard:false});
    var info = '<b>' + esc(ev.title) + '</b><br>' + esc(lbl(cat.k, cat.k.toUpperCase()));
    if (last.kt > 0) info += '<br>' + esc(lbl('winds', 'Max winds')) + ': ' + Math.round(last.kt * 1.852) + ' km/h (' + Math.round(last.kt) + ' kt)';
    info += '<br>' + esc(lbl('stReach', 'Storm winds reach')) + ': ' + Math.round(stormRadii(last.kt).outer) + ' km';
    m.bindPopup(info, {closeButton:false, offset:[0, -badgeSize(last.kt) / 3]});
    marks.addLayer(m);
    count++;
  });
  group.addLayer(halo); group.addLayer(lines); group.addLayer(marks);
  return {group:group, count:count, zones:zones};
}

function loadStorms(seq) {
  state.busy = true; setLegend('');
  var fresh = cache.storms && Date.now() - cache.stormsAt < 600000;
  var p = fresh ? Promise.resolve(cache.storms) : getJson(EONET + 'severeStorms').then(function (d) {
    cache.storms = d.events || []; cache.stormsAt = Date.now(); return cache.storms;
  });
  p.then(function (events) {
    if (seq !== state.seq) return;
    var built = buildStorms(events);
    var zone = new ZoneLayer(function (ctx, m, size) { paintStorms(ctx, m, size, built.zones); });
    overlay = L.layerGroup([zone, built.group]).addTo(map);
    state.busy = false;
    setLegend(built.count ? lbl('stKey', 'Blue: active now · Gray: already passed') : lbl('none', 'No active storms right now'));
  }).catch(function () {
    if (seq === state.seq) { state.busy = false; setLegend(state.err); }
  });
}

__LAYER_SCRIPTS__

function startLayer(id) {
  state.busy = false; state.canvas = false;
  var seq = ++state.seq;
  clearOverlay();
  if (id === 'precip') {
    state.busy = true;
    fetchJsonWithTimeout('https://api.rainviewer.com/public/weather-maps.json', 15000).then(function (d) {
      if (seq !== state.seq) return;
      var radar = d.radar || {}, frames = (radar.past || []).map(function (f) { f.observed = true; return f; }).concat((radar.nowcast || []).map(function (f) { f.observed = false; return f; }));
      var frame = frames.reduce(function (best, f) { return !best || Math.abs(f.time - state.time) < Math.abs(best.time - state.time) ? f : best; }, null);
      if (!frame) throw new Error('none');
      overlay = rainTileLayer(d.host, frame.path).addTo(map);
      overlay.setZIndex(5);
      state.busy = false; setLegend('');
    }).catch(function () { if (seq === state.seq) { state.busy = false; setLegend(state.err); } });
  } else if (id === 'cyclones') {
    loadStorms(seq);
  } else if (id === 'sst') {
    loadSstLayer(seq);
  } else if (id === 'fires') {
    loadFires(seq);
    return;
  } else if (id === 'alerts') {
    loadAlerts(seq);
    return;
  } else if (id === 'sea' || id === 'drought') {
    state.canvas = true;
    overlay = new FieldLayer().addTo(map);
    loadGrid(true);
    landThenRefresh(seq);
    if (id === 'drought') loadUsdm(seq);
  } else if (isGrid(id)) {
    state.canvas = true;
    overlay = new FieldLayer().addTo(map);
    loadGrid(true);
  }
  setLegend('');
}

function setLayer(id, title, err, f, labels) {
  state.layer = id; state.title = title; state.err = err; state.f = f;
  if (labels) state.labels = typeof labels === 'string' ? JSON.parse(labels) : labels;
  startLayer(id);
}

var COLS = 37, ROWS = 16;

function wrapLon(x) { return ((x + 540) % 360) - 180; }

function lonFor(latlng) {
  var x = latlng.lng;
  var mid = (grid.w + grid.e) / 2;
  while (x - mid > 180) x -= 360;
  while (mid - x > 180) x += 360;
  return x;
}

map.on('click', function (event) {
  if (!(window.MapBridge && window.MapBridge.detail)) return;
  if (['precip','temp','clouds','wind','pressure','humidity','dew','uv','aqi','sea'].indexOf(state.layer) < 0) return;
  openDetail('weather-point', {lat:event.latlng.lat, lon:wrapLon(event.latlng.lng), layer:state.layer, time:Math.floor(state.time * 1000)});
});

window.MapApi = {setTheme:setTheme, setLayer:setLayer, setTime:setTime, setCity:setCity, setInsets:setInsets, locate:locate};

var sizeRaf = 0;
function syncSize() {
  if (sizeRaf) return;
  sizeRaf = requestAnimationFrame(function () { sizeRaf = 0; map.invalidateSize({animate:false}); });
}
if (window.ResizeObserver) new ResizeObserver(syncSize).observe(document.getElementById('map'));
else window.addEventListener('resize', syncSize);
[0, 400].forEach(function (d) { setTimeout(syncSize, d); });

state.err = INIT.err;
state.labels = INIT.labels || {};
setTheme(INIT.dark, INIT.palette || null);
setInsets(INIT.top, INIT.bottom);
if (INIT.round) document.documentElement.className = 'round';
if (INIT.hasCity) setCity(INIT.lat, INIT.lon, false);
if (INIT.zoom) map.setView([INIT.centerLat, INIT.centerLon], INIT.zoom, {animate:false});
setLayer(INIT.layer, INIT.title, INIT.err, INIT.f, INIT.labels);

setTimeout(function () { if (!tileOk) note(INIT.err); }, 8000);
}
loadScript([
  'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js',
  'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
], 0, start, function () { note(INIT.err); });
})();
</script>
</body>
</html>
""".trimIndent()

    fun page(init: String): String = html.replace("__LAYER_SCRIPTS__", MapLayerScripts.source).replace("__INIT__", init)
}
