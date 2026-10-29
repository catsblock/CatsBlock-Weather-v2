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
#map{position:absolute;inset:0;background:var(--bg)}
.fc{position:absolute;left:0;top:0;pointer-events:none;transition:opacity .35s}
.fc.blur{filter:blur(7px)}
#legend{position:absolute;z-index:1000;left:50%;top:calc(var(--top) + 12px);transform:translate(-50%,-140%);opacity:0;min-width:176px;max-width:84%;padding:10px 16px 12px;border-radius:24px;background:var(--chip);color:var(--fg);box-shadow:0 4px 18px rgba(0,0,0,.22);border:1px solid var(--edge);transition:transform .5s cubic-bezier(.2,0,0,1),opacity .35s;box-sizing:border-box}
#legend.on{transform:translate(-50%,0);opacity:1}
#legend .t{font-size:14px;font-weight:600;text-align:center;white-space:nowrap}
#legend .m{font-size:12px;color:var(--sub);text-align:center;margin-top:4px}
#scale{position:absolute;z-index:1000;left:12px;top:50%;transform:translate(-170%,-50%);opacity:0;display:flex;flex-direction:column;align-items:center;gap:9px;padding:14px 10px;border-radius:28px;background:var(--chip);color:var(--fg);box-shadow:0 4px 18px rgba(0,0,0,.28);border:1px solid var(--edge);transition:transform .5s cubic-bezier(.2,0,0,1),opacity .35s}
#scale.on{transform:translate(0,-50%);opacity:1}
#scale .bar{width:14px;height:200px;border-radius:7px;background:var(--grad)}
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
.cy .sw{position:absolute;inset:0;filter:drop-shadow(0 0 10px var(--c)) drop-shadow(0 3px 6px rgba(0,0,0,.35))}
.cy.s .sw{transform:scaleX(-1)}
.cy svg{width:100%;height:100%;overflow:visible;animation:spin var(--d) linear infinite;animation-direction:reverse;will-change:transform}
.cy:after{content:"";position:absolute;inset:-8px;border-radius:50%;border:1.5px solid var(--c);opacity:0;animation:cp 3s ease-out infinite}
.cy .tint{fill:var(--c);fill-opacity:.24}
.cy .rg{fill:none;stroke:var(--c);stroke-width:1.6;stroke-opacity:.6}
.cn{position:absolute;left:50%;top:100%;transform:translate(-50%,6px);white-space:nowrap;display:flex;align-items:center;gap:5px;padding:3px 9px 3px 6px;border-radius:12px;background:var(--chip);color:var(--fg);font-size:11px;font-weight:700;box-shadow:0 2px 8px rgba(0,0,0,.28);border:1px solid var(--edge)}
.cn i{width:8px;height:8px;border-radius:50%;background:var(--c);box-shadow:0 0 6px var(--c)}
@keyframes spin{to{transform:rotate(360deg)}}
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
var map = L.map('map', {zoomControl:false, attributionControl:false, minZoom:2, maxZoom:12, zoomSnap:0.25, worldCopyJump:true}).setView([0, 0], 4);
L.control.attribution({position:'bottomright', prefix:false}).addTo(map)
  .addAttribution('© OpenStreetMap contributors · Open-Meteo · RainViewer · NASA GIBS · NASA FIRMS · NASA EONET');

var state = {canvas:false, dark:false, layer:'none', title:'', err:'', f:false, lat:0, lon:0, seq:0, busy:false, labels:{}};
var base = null, overlay = null, pin = null, grid = null, gridAt = 0, gridZoom = 0, timer = null, off = null;
var cache = {storms:null, stormsAt:0, fires:null, firesAt:0};
var legend = document.getElementById('legend');
var scale = document.getElementById('scale');
var EONET = 'https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=500&category=';

var RAMPS = {
  temp: [[-30,[122,48,176]],[-15,[48,86,226]],[0,[34,196,226]],[10,[72,198,104]],[20,[250,222,52]],[30,[250,140,28]],[40,[226,52,40]],[50,[150,24,60]]],
  wind: [[0,[42,111,219]],[15,[38,198,218]],[30,[102,187,106]],[50,[253,216,53]],[75,[251,140,0]],[100,[211,47,47]],[140,[142,36,170]]],
  pressure: [[980,[123,50,148]],[995,[59,111,212]],[1005,[79,195,247]],[1013,[224,224,224]],[1022,[255,183,77]],[1030,[244,81,30]],[1045,[183,28,28]]]
};

var CATS = [
  {k:'td', kt:0, c:[79,195,247], s:44, d:9},
  {k:'ts', kt:34, c:[102,187,106], s:52, d:7},
  {k:'c1', kt:64, c:[253,216,53], s:60, d:5.5},
  {k:'c2', kt:83, c:[255,179,0], s:68, d:4.5},
  {k:'c3', kt:96, c:[251,140,0], s:76, d:3.7},
  {k:'c4', kt:113, c:[229,57,53], s:86, d:3},
  {k:'c5', kt:137, c:[213,0,249], s:96, d:2.4}
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

function isGrid(id) { return id === 'temp' || id === 'clouds' || id === 'wind' || id === 'pressure'; }

function fmtTemp(c) { return state.f ? Math.round(c * 9 / 5 + 32) + '°' : Math.round(c) + '°'; }

function setLegend(extra) {
  var id = state.layer;
  if (id === 'none') { legend.className = ''; scale.className = ''; return; }
  var grad = null, lo = '', hi = '';
  if (id === 'temp') { grad = gradCss(RAMPS.temp); lo = fmtTemp(-30); hi = fmtTemp(50); }
  else if (id === 'wind') { grad = gradCss(RAMPS.wind); lo = '0'; hi = '140 km/h'; }
  else if (id === 'pressure') { grad = gradCss(RAMPS.pressure); lo = '980'; hi = '1045 hPa'; }
  else if (id === 'clouds') { grad = 'linear-gradient(90deg,rgba(150,160,180,0),rgba(235,240,250,.98))'; lo = '0%'; hi = '100%'; }
  else if (id === 'precip') { grad = 'linear-gradient(90deg,#8fd3f4,#2f80ed,#27ae60,#f2c94c,#eb5757,#9b51e0)'; lo = '·'; hi = '···'; }
  else if (id === 'sst') { grad = 'linear-gradient(90deg,#2b6cb0,#e6eef7,#e53e3e)'; lo = '-3°'; hi = '+3°'; }
  else if (id === 'fires') { grad = 'linear-gradient(90deg,#ffb300,#ff5722,#d50000)'; lo = '·'; hi = '···'; }
  else if (id === 'cyclones') { grad = gradCss(CAT_STOPS); lo = 'TD'; hi = 'C5'; }
  var html = '<div class="t">' + esc(state.title) + '</div>';
  if (extra) html += '<div class="m">' + esc(extra) + '</div>';
  legend.innerHTML = html;
  legend.className = 'on';
  if (grad) {
    scale.innerHTML = '<span>' + hi + '</span><div class="bar" style="--grad:' + grad.replace('90deg', '0deg') + '"></div><span>' + lo + '</span>';
    scale.className = 'on' + (state.busy ? ' busy' : '');
  } else {
    scale.className = '';
  }
}

var tileOk = false;
map.createPane('basepane').style.zIndex = 190;
var fieldPane = map.createPane('fieldpane');
fieldPane.style.zIndex = 250;
fieldPane.style.pointerEvents = 'none';
map.createPane('trackpane').style.zIndex = 420;
map.createPane('stormpane').style.zIndex = 640;

function makeBase() {
  if (base) map.removeLayer(base);
  base = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, pane:'basepane'});
  base.on('tileload', function () { tileOk = true; hideNote(); });
  base.addTo(map);
  document.getElementById('map').classList.toggle('dim', state.dark);
}

function setTheme(dark) {
  state.dark = dark;
  document.body.className = dark ? 'dark' : 'light';
  makeBase();
  if (overlay && overlay.refresh) overlay.refresh();
}

function setInsets(top, bottom) {
  document.documentElement.style.setProperty('--top', top + 'px');
  document.documentElement.style.setProperty('--bottom', bottom + 'px');
}

function setCity(lat, lon, fly) {
  state.lat = lat; state.lon = lon;
  if (!pin) pin = L.marker([lat, lon], {icon:L.divIcon({className:'', html:'<div class="pin"></div>', iconSize:[22, 22], iconAnchor:[11, 11]}), interactive:false, keyboard:false}).addTo(map);
  else pin.setLatLng([lat, lon]);
  if (fly) map.flyTo([lat, lon], Math.max(map.getZoom(), 6), {duration:1.4});
  else map.setView([lat, lon], 6, {animate:false});
}

function locate() { map.flyTo([state.lat, state.lon], Math.max(map.getZoom(), 7), {duration:1.2}); }

function gibs(layer, level, offset, opacity) {
  var d = new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10);
  return L.tileLayer('https://gibs.earthdata.nasa.gov/wmts/epsg3857/all/' + layer + '/default/' + d + '/GoogleMapsCompatible_Level' + level + '/{z}/{y}/{x}.png', {maxNativeZoom:level, opacity:opacity, tileSize:256});
}

function getJson(url) {
  return fetch(url).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); });
}

var FieldLayer = L.Layer.extend({
  onAdd: function (m) {
    this._fill = L.DomUtil.create('canvas', 'fc');
    this._fill.style.opacity = 0;
    m.getPane('fieldpane').appendChild(this._fill);
    this._hide = this._hide.bind(this);
    this.refresh = this.refresh.bind(this);
    m.on('moveend zoomend resize', this.refresh);
    m.on('zoomstart', this._hide);
    this.refresh();
  },
  onRemove: function (m) {
    L.DomUtil.remove(this._fill);
    m.off('moveend zoomend resize', this.refresh);
    m.off('zoomstart', this._hide);
    if (this._raf) cancelAnimationFrame(this._raf);
    this._map = null;
  },
  _hide: function () {
    this._fill.style.opacity = 0;
  },
  refresh: function () {
    var self = this;
    if (!self._map || self._raf) return;
    self._raf = requestAnimationFrame(function () { self._raf = 0; self._paint(); });
  },
  _paint: function () {
    var m = this._map;
    if (!m) return;
    var size = m.getSize(), cv = this._fill;
    L.DomUtil.setPosition(cv, m.containerPointToLayerPoint([0, 0]));
    if (cv.width !== size.x || cv.height !== size.y) { cv.width = size.x; cv.height = size.y; }
    var ctx = cv.getContext('2d');
    ctx.clearRect(0, 0, size.x, size.y);
    var id = state.layer;
    if (!grid || !isGrid(id)) return;
    var arr = id === 'temp' ? grid.t : (id === 'clouds' ? grid.c : (id === 'wind' ? grid.ws : grid.p));
    var step = 5, sw = Math.ceil(size.x / step), sh = Math.ceil(size.y / step);
    if (!this._small) this._small = document.createElement('canvas');
    this._small.width = sw; this._small.height = sh;
    var sctx = this._small.getContext('2d'), img = sctx.createImageData(sw, sh);
    for (var y = 0; y < sh; y++) {
      for (var x = 0; x < sw; x++) {
        var ll = m.containerPointToLatLng([x * step + step / 2, y * step + step / 2]);
        var col = colorFor(id, sample(arr, ll.lat, lonFor(ll))), o = (y * sw + x) * 4;
        img.data[o] = col[0]; img.data[o + 1] = col[1]; img.data[o + 2] = col[2]; img.data[o + 3] = col[3];
      }
    }
    sctx.putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(this._small, 0, 0, sw * step, sh * step);
    cv.style.opacity = 1;
  }
});

function clearOverlay() {
  if (off) { off(); off = null; }
  if (overlay) { map.removeLayer(overlay); overlay = null; }
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

function spiral(off, turns, r0, r1) {
  var pts = [], n = 28;
  for (var i = 0; i <= n; i++) {
    var t = i / n, r = r0 + (r1 - r0) * t, a = off + t * turns * 2 * Math.PI;
    pts.push((50 + r * Math.cos(a)).toFixed(1) + ' ' + (50 + r * Math.sin(a)).toFixed(1));
  }
  return 'M' + pts.join('L');
}

var ARMS = '';
for (var ai = 0; ai < 5; ai++) {
  var ad = spiral(ai * 2 * Math.PI / 5, 0.8, 10, 47);
  ARMS += '<path d="' + ad + '" fill="none" stroke="#fff" stroke-width="9" stroke-linecap="round" opacity=".38" filter="url(#cb)"/><path d="' + ad + '" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".9" filter="url(#cs)"/>';
}

var CY_SVG = '<svg viewBox="0 0 100 100"><defs><radialGradient id="cg"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset=".45" stop-color="#eef3fa" stop-opacity=".6"/><stop offset="1" stop-color="#dfe8f5" stop-opacity="0"/></radialGradient><filter id="cb" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3"/></filter><filter id="cs" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation=".8"/></filter></defs><circle class="tint" cx="50" cy="50" r="48"/><circle cx="50" cy="50" r="48" fill="url(#cg)"/>' + ARMS + '<circle class="rg" cx="50" cy="50" r="47"/><circle cx="50" cy="50" r="11" fill="none" stroke="#fff" stroke-width="3" opacity=".8" filter="url(#cs)"/><circle cx="50" cy="50" r="8" fill="#0b1220" fill-opacity=".85" filter="url(#cs)"/></svg>';

function stormIcon(cat, north, name) {
  var html = '<div class="cy ' + (north ? 'n' : 's') + '" style="--c:' + rgb(cat.c) + ';--s:' + cat.s + 'px;--d:' + cat.d + 's"><div class="sw">' + CY_SVG + '</div><span class="cn"><i></i>' + esc(name) + ' · ' + cat.k.toUpperCase() + '</span></div>';
  return L.divIcon({className:'cyw', html:html, iconSize:[cat.s, cat.s], iconAnchor:[cat.s / 2, cat.s / 2]});
}

function buildStorms(events) {
  var group = L.layerGroup(), halo = L.layerGroup(), lines = L.layerGroup(), marks = L.layerGroup(), count = 0;
  var haloColor = state.dark ? 'rgba(255,255,255,.22)' : 'rgba(0,0,0,.22)';
  events.forEach(function (ev) {
    if (!/cyclone|hurricane|typhoon|tropical|depression/i.test(ev.title || '')) return;
    var geo = (ev.geometry || []).filter(function (g) { return g.type === 'Point' && g.coordinates && g.coordinates.length >= 2; });
    geo.sort(function (a, b) { return new Date(a.date) - new Date(b.date); });
    var pts = geo.map(function (g) { return {lat:g.coordinates[1], lon:g.coordinates[0], kt:toKt(g.magnitudeValue, g.magnitudeUnit)}; });
    if (!pts.length) return;
    for (var i = 1; i < pts.length; i++) {
      while (pts[i].lon - pts[i - 1].lon > 180) pts[i].lon -= 360;
      while (pts[i - 1].lon - pts[i].lon > 180) pts[i].lon += 360;
    }
    if (pts.length > 1) {
      var path = smooth(pts);
      for (var j = 0; j < path.length - 1; j++) {
        var a = path[j], b = path[j + 1], kt = (a.kt + b.kt) / 2;
        var w = 2.5 + Math.min(1, kt / 140) * 6.5;
        var ll = [[a.lat, a.lon], [b.lat, b.lon]];
        halo.addLayer(L.polyline(ll, {pane:'trackpane', color:haloColor, weight:w + 3, lineCap:'round', lineJoin:'round', interactive:false}));
        lines.addLayer(L.polyline(ll, {pane:'trackpane', color:rgb(ramp(CAT_STOPS, kt)), weight:w, opacity:0.95, lineCap:'round', lineJoin:'round', interactive:false}));
      }
    }
    var last = pts[pts.length - 1], cat = catOf(last.kt);
    var m = L.marker([last.lat, last.lon], {icon:stormIcon(cat, last.lat >= 0, ev.title), pane:'stormpane', keyboard:false});
    var info = '<b>' + esc(ev.title) + '</b><br>' + esc(lbl(cat.k, cat.k.toUpperCase()));
    if (last.kt > 0) info += '<br>' + esc(lbl('winds', 'Max winds')) + ': ' + Math.round(last.kt * 1.852) + ' km/h (' + Math.round(last.kt) + ' kt)';
    m.bindPopup(info, {closeButton:false, offset:[0, -cat.s / 3]});
    marks.addLayer(m);
    count++;
  });
  group.addLayer(halo); group.addLayer(lines); group.addLayer(marks);
  return {group:group, count:count};
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
    overlay = built.group.addTo(map);
    state.busy = false;
    setLegend(built.count ? '' : lbl('none', 'No active storms right now'));
  }).catch(function () {
    if (seq === state.seq) { state.busy = false; setLegend(state.err); }
  });
}

function parseFires(events) {
  var out = [];
  events.forEach(function (ev) {
    (ev.geometry || []).forEach(function (g) {
      if (g.type === 'Point' && g.coordinates && g.coordinates.length >= 2) {
        out.push([Math.floor(g.coordinates[1] / 0.25) * 0.25, Math.floor(g.coordinates[0] / 0.25) * 0.25, 1, 0]);
      }
    });
  });
  return out;
}

var HZ = ['url(#hz1)', 'url(#hz2)', 'url(#hz3)'];
var HC = ['#ffb300', '#ff6d00', '#e53935'];

function renderFires(cells, group) {
  group.clearLayers();
  var z = map.getZoom(), cs = z <= 3.5 ? 4 : (z <= 4.5 ? 2 : (z <= 5.5 ? 1 : (z <= 6.5 ? 0.5 : 0.25)));
  var b = map.getBounds().pad(0.2), m = {};
  for (var i = 0; i < cells.length; i++) {
    var c = cells[i];
    if (c[0] > b.getNorth() || c[0] + 0.25 < b.getSouth() || c[1] > b.getEast() || c[1] + 0.25 < b.getWest()) continue;
    var k = Math.floor(c[0] / cs) + ':' + Math.floor(c[1] / cs), e = m[k];
    if (!e) m[k] = {lat:Math.floor(c[0] / cs) * cs, lon:Math.floor(c[1] / cs) * cs, n:c[2], frp:c[3]};
    else { e.n += c[2]; if (c[3] > e.frp) e.frp = c[3]; }
  }
  Object.keys(m).forEach(function (k) {
    var e = m[k], lv = (e.n >= 12 || e.frp >= 150) ? 2 : ((e.n >= 3 || e.frp >= 40) ? 1 : 0);
    var r = L.rectangle([[e.lat, e.lon], [e.lat + cs, e.lon + cs]], {pane:'trackpane', color:HC[lv], weight:1.6, opacity:0.95, fillColor:HZ[lv], fillOpacity:1, lineJoin:'round'});
    var info = '<b>' + esc(lbl('fire', 'Wildfire')) + '</b><br>' + esc(lbl('hotspots', 'Hotspots')) + ': ' + e.n;
    if (e.frp > 0) info += '<br>FRP: ' + Math.round(e.frp) + ' MW';
    r.bindPopup(info, {closeButton:false});
    group.addLayer(r);
  });
}

function loadFires(seq) {
  var group = L.layerGroup();
  overlay = group.addTo(map);
  state.busy = true; setLegend('');
  var cells = [];
  var redo = function () { renderFires(cells, group); };
  map.on('moveend zoomend', redo);
  off = function () { map.off('moveend zoomend', redo); };
  var fresh = cache.fires && Date.now() - cache.firesAt < 600000;
  var p = fresh ? Promise.resolve(cache.fires) : fetch('__fires.json').then(function (r) {
    if (!r.ok) throw new Error('http');
    return r.json();
  }).catch(function () {
    return getJson(EONET + 'wildfires').then(function (d) { return parseFires(d.events || []); });
  }).then(function (c) { cache.fires = c; cache.firesAt = Date.now(); return c; });
  p.then(function (c) {
    if (seq !== state.seq) return;
    cells = c;
    redo();
  }).catch(function () {}).then(function () {
    if (seq === state.seq) { state.busy = false; setLegend(''); }
  });
}

function setLayer(id, title, err, f, labels) {
  state.layer = id; state.title = title; state.err = err; state.f = f; state.busy = false; state.canvas = false;
  if (labels) state.labels = typeof labels === 'string' ? JSON.parse(labels) : labels;
  var seq = ++state.seq;
  clearOverlay();
  if (id === 'precip') {
    state.busy = true;
    fetch('https://api.rainviewer.com/public/weather-maps.json').then(function (r) { return r.json(); }).then(function (d) {
      if (seq !== state.seq) return;
      var past = d.radar.past, last = past[past.length - 1];
      overlay = L.tileLayer(d.host + last.path + '/256/{z}/{x}/{y}/2/1_1.png', {maxNativeZoom:7, opacity:0.82, tileSize:256}).addTo(map);
      overlay.setZIndex(5);
      state.busy = false; setLegend('');
    }).catch(function () { if (seq === state.seq) { state.busy = false; setLegend(state.err); } });
  } else if (id === 'cyclones') {
    loadStorms(seq);
  } else if (id === 'sst') {
    overlay = gibs('GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies_v4.1_STD', 7, 3, 0.8).addTo(map);
    overlay.setZIndex(4);
  } else if (id === 'fires') {
    loadFires(seq);
    return;
  } else if (isGrid(id)) {
    state.canvas = true;
    overlay = new FieldLayer().addTo(map);
    loadGrid(true);
  }
  setLegend('');
}

var COLS = 12, ROWS = 9;

function wrapLon(x) { return ((x + 540) % 360) - 180; }

function loadGrid(force) {
  if (!isGrid(state.layer) || !state.canvas) return;
  var seq = state.seq;
  var b = map.getBounds().pad(0.3);
  var s = Math.max(-80, b.getSouth()), n = Math.min(80, b.getNorth()), w = b.getWest(), e = b.getEast();
  if (e - w > 340) { w = -170; e = 170; }
  var lats = [], lons = [];
  for (var r = 0; r < ROWS; r++) {
    for (var c = 0; c < COLS; c++) {
      lats.push((s + (n - s) * r / (ROWS - 1)).toFixed(3));
      lons.push(wrapLon(w + (e - w) * c / (COLS - 1)).toFixed(3));
    }
  }
  state.busy = true; setLegend('');
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lats.join(',') + '&longitude=' + lons.join(',') +
    '&current=temperature_2m,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m&wind_speed_unit=kmh&timezone=UTC';
  fetch(url).then(function (r) { return r.json(); }).then(function (d) {
    if (seq !== state.seq) return;
    if (!Array.isArray(d)) d = [d];
    var g = {s:s, n:n, w:w, e:e, t:[], c:[], p:[], ws:[], wd:[]};
    for (var i = 0; i < d.length; i++) {
      var cur = d[i].current;
      g.t.push(cur.temperature_2m); g.c.push(cur.cloud_cover); g.p.push(cur.pressure_msl);
      g.ws.push(cur.wind_speed_10m); g.wd.push(cur.wind_direction_10m);
    }
    grid = g; gridAt = Date.now(); gridZoom = map.getZoom();
    state.busy = false; setLegend('');
    if (overlay && overlay.refresh) overlay.refresh();
  }).catch(function () {
    if (seq === state.seq) { state.busy = false; setLegend(state.err); }
  });
}

function sample(arr, lat, lon) {
  var fx = (lon - grid.w) / (grid.e - grid.w) * (COLS - 1);
  var fy = (lat - grid.s) / (grid.n - grid.s) * (ROWS - 1);
  fx = Math.min(COLS - 1.001, Math.max(0, fx)); fy = Math.min(ROWS - 1.001, Math.max(0, fy));
  var x0 = Math.floor(fx), y0 = Math.floor(fy), tx = fx - x0, ty = fy - y0;
  tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
  var a = arr[y0 * COLS + x0], b = arr[y0 * COLS + x0 + 1], c = arr[(y0 + 1) * COLS + x0], d = arr[(y0 + 1) * COLS + x0 + 1];
  return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
}

function lonFor(latlng) {
  var x = latlng.lng;
  var mid = (grid.w + grid.e) / 2;
  while (x - mid > 180) x -= 360;
  while (mid - x > 180) x += 360;
  return x;
}

function colorFor(id, v) {
  if (id === 'clouds') {
    var a = Math.min(1, v / 100) * 0.85, b = state.dark ? [236, 242, 252] : [255, 255, 255];
    return [b[0], b[1], b[2], Math.round(a * 255)];
  }
  var key = id === 'temp' ? 'temp' : (id === 'wind' ? 'wind' : 'pressure');
  var c = ramp(RAMPS[key], v);
  return [Math.round(c[0]), Math.round(c[1]), Math.round(c[2]), 184];
}

map.on('moveend', function () {
  if (!isGrid(state.layer) || !state.canvas) return;
  clearTimeout(timer);
  timer = setTimeout(function () {
    var inside = grid && map.getBounds().getSouth() >= grid.s && map.getBounds().getNorth() <= grid.n &&
      map.getBounds().getWest() >= grid.w && map.getBounds().getEast() <= grid.e;
    var fresh = Date.now() - gridAt < 600000 && Math.abs(map.getZoom() - gridZoom) < 1.2;
    if (!(inside && fresh)) loadGrid(false);
  }, 700);
});

window.MapApi = {setTheme:setTheme, setLayer:setLayer, setCity:setCity, setInsets:setInsets, locate:locate};

if (window.ResizeObserver) new ResizeObserver(function () { map.invalidateSize(); }).observe(document.getElementById('map'));
window.addEventListener('resize', function () { map.invalidateSize(); });
[0, 200, 800, 2000].forEach(function (d) { setTimeout(function () { map.invalidateSize(); }, d); });

state.err = INIT.err;
state.labels = INIT.labels || {};
setTheme(INIT.dark);
setInsets(INIT.top, INIT.bottom);
if (INIT.hasCity) setCity(INIT.lat, INIT.lon, false);
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

    fun page(init: String): String = html.replace("__INIT__", init)
}
