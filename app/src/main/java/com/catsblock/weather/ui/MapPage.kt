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
#legend .bar{height:8px;border-radius:4px;margin:8px 0 4px;background:var(--grad)}
#legend .r{display:flex;justify-content:space-between;font-size:11px;color:var(--sub)}
#legend .m{font-size:12px;color:var(--sub);text-align:center;margin-top:4px}
#legend.busy .bar{background-size:200% 100%;animation:sh 1.1s linear infinite;opacity:.55}
@keyframes sh{0%{background-position:0 0}100%{background-position:-200% 0}}
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
.cy svg{position:absolute;inset:0;width:100%;height:100%;overflow:visible;animation:spin var(--d) linear infinite;filter:drop-shadow(0 0 6px var(--c)) drop-shadow(0 2px 3px rgba(0,0,0,.45))}
.cy.n svg{animation-direction:reverse}
.cy:after{content:"";position:absolute;inset:-6px;border-radius:50%;border:2px solid var(--c);opacity:0;animation:cp 2.6s ease-out infinite}
.cy .ring{fill:var(--c);fill-opacity:.16;stroke:var(--c);stroke-opacity:.4;stroke-width:2}
.cy .arms path{fill:var(--c);stroke:rgba(255,255,255,.8);stroke-width:1.5;stroke-linejoin:round}
.cy .eye{fill:rgba(10,12,18,.85);stroke:#fff;stroke-width:3}
.cn{position:absolute;left:50%;top:100%;transform:translate(-50%,2px);white-space:nowrap;font-size:11px;font-weight:700;color:var(--fg);text-shadow:0 0 3px var(--bg),0 0 3px var(--bg),0 0 5px var(--bg)}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes cp{0%{transform:scale(.7);opacity:.8}100%{transform:scale(1.5);opacity:0}}
.fl{background:none;border:0}
.fl div{width:100%;height:100%;transform-origin:50% 100%;animation:fk var(--k) ease-in-out infinite;filter:drop-shadow(0 0 5px rgba(255,87,34,.95)) drop-shadow(0 1px 2px rgba(0,0,0,.5))}
.fl svg{width:100%;height:100%;display:block}
@keyframes fk{0%,100%{transform:scale(1,1) rotate(-3deg)}35%{transform:scale(1.08,1.18) rotate(3deg)}70%{transform:scale(.96,1.06) rotate(-2deg)}}
.fg{background:none;border:0}
.fg div{width:100%;height:100%;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:800;color:#fff;background:radial-gradient(circle at 50% 40%,#ffb300,#ff5722 60%,#b71c1c);box-shadow:0 0 0 4px rgba(255,87,34,.28),0 0 14px rgba(255,87,34,.8);animation:fp 2s ease-in-out infinite}
@keyframes fp{0%,100%{box-shadow:0 0 0 3px rgba(255,87,34,.3),0 0 12px rgba(255,87,34,.7)}50%{box-shadow:0 0 0 8px rgba(255,87,34,.12),0 0 20px rgba(255,87,34,.95)}}
.fire-tiles{filter:saturate(1.8) brightness(1.2) drop-shadow(0 0 2px rgba(255,87,34,.9))}
</style>
</head>
<body class="light">
<div id="map"></div>
<div id="legend"></div>
<div id="note"></div>
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
  .addAttribution('© OpenStreetMap contributors · Open-Meteo · RainViewer · NASA GIBS · NASA EONET');

var state = {dark:false, layer:'none', title:'', err:'', f:false, lat:0, lon:0, seq:0, busy:false, labels:{}};
var base = null, overlay = null, pin = null, grid = null, gridAt = 0, gridZoom = 0, timer = null, off = null;
var cache = {storms:null, stormsAt:0, fires:null, firesAt:0};
var legend = document.getElementById('legend');
var EONET = 'https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=500&category=';

var RAMPS = {
  temp: [[-30,[94,60,153]],[-15,[59,111,212]],[0,[79,195,247]],[10,[102,187,106]],[20,[253,216,53]],[30,[251,140,0]],[40,[211,47,47]],[50,[123,31,162]]],
  wind: [[0,[42,111,219]],[15,[38,198,218]],[30,[102,187,106]],[50,[253,216,53]],[75,[251,140,0]],[100,[211,47,47]],[140,[142,36,170]]],
  pressure: [[980,[123,50,148]],[995,[59,111,212]],[1005,[79,195,247]],[1013,[224,224,224]],[1022,[255,183,77]],[1030,[244,81,30]],[1045,[183,28,28]]]
};

var CATS = [
  {k:'td', kt:0, c:[79,195,247], s:34, d:7},
  {k:'ts', kt:34, c:[102,187,106], s:40, d:5.5},
  {k:'c1', kt:64, c:[253,216,53], s:48, d:4.5},
  {k:'c2', kt:83, c:[255,179,0], s:54, d:3.6},
  {k:'c3', kt:96, c:[251,140,0], s:62, d:3},
  {k:'c4', kt:113, c:[229,57,53], s:70, d:2.3},
  {k:'c5', kt:137, c:[213,0,249], s:80, d:1.7}
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
  if (id === 'none') { legend.className = ''; return; }
  var html = '<div class="t">' + esc(state.title) + '</div>';
  var grad = null, lo = '', hi = '';
  if (id === 'temp') { grad = gradCss(RAMPS.temp); lo = fmtTemp(-30); hi = fmtTemp(50); }
  else if (id === 'wind') { grad = gradCss(RAMPS.wind); lo = '0'; hi = '140 km/h'; }
  else if (id === 'pressure') { grad = gradCss(RAMPS.pressure); lo = '980'; hi = '1045 hPa'; }
  else if (id === 'clouds') { grad = 'linear-gradient(90deg,rgba(150,160,180,0),rgba(150,160,180,.95))'; lo = '0%'; hi = '100%'; }
  else if (id === 'precip') { grad = 'linear-gradient(90deg,#8fd3f4,#2f80ed,#27ae60,#f2c94c,#eb5757,#9b51e0)'; lo = '·'; hi = '···'; }
  else if (id === 'sst') { grad = 'linear-gradient(90deg,#2b6cb0,#e6eef7,#e53e3e)'; lo = '-3°'; hi = '+3°'; }
  else if (id === 'fires') { grad = 'linear-gradient(90deg,#ffb300,#ff5722,#d50000)'; lo = '·'; hi = '···'; }
  else if (id === 'cyclones') { grad = gradCss(CAT_STOPS); lo = 'TD'; hi = 'C5'; }
  if (grad) {
    html += '<div class="bar" style="--grad:' + grad + '"></div><div class="r"><span>' + lo + '</span><span>' + hi + '</span></div>';
  }
  if (extra) html += '<div class="m">' + esc(extra) + '</div>';
  legend.innerHTML = html;
  legend.className = 'on' + (state.busy ? ' busy' : '');
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

function gibsLive(layer, level, opacity) {
  return L.tileLayer('https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/' + layer + '/default/default/GoogleMapsCompatible_Level' + level + '/{z}/{y}/{x}.png', {maxNativeZoom:level, opacity:opacity, tileSize:256, className:'fire-tiles'});
}

function probe(urls, ok, bad) {
  var i = 0;
  (function next() {
    if (i >= urls.length) { bad(); return; }
    var im = new Image();
    im.onload = function () { ok(i); };
    im.onerror = function () { i++; next(); };
    im.src = urls[i];
  })();
}

function getJson(url) {
  return fetch(url).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); });
}

var FieldLayer = L.Layer.extend({
  onAdd: function (m) {
    this._fill = L.DomUtil.create('canvas', 'fc blur');
    this._mark = L.DomUtil.create('canvas', 'fc');
    this._fill.style.opacity = 0;
    this._mark.style.opacity = 0;
    var p = m.getPane('fieldpane');
    p.appendChild(this._fill);
    p.appendChild(this._mark);
    this._hide = this._hide.bind(this);
    this.refresh = this.refresh.bind(this);
    m.on('moveend zoomend resize', this.refresh);
    m.on('zoomstart', this._hide);
    this.refresh();
  },
  onRemove: function (m) {
    L.DomUtil.remove(this._fill);
    L.DomUtil.remove(this._mark);
    m.off('moveend zoomend resize', this.refresh);
    m.off('zoomstart', this._hide);
    if (this._raf) cancelAnimationFrame(this._raf);
    this._map = null;
  },
  _hide: function () {
    this._fill.style.opacity = 0;
    this._mark.style.opacity = 0;
  },
  refresh: function () {
    var self = this;
    if (!self._map || self._raf) return;
    self._raf = requestAnimationFrame(function () { self._raf = 0; self._paint(); });
  },
  _paint: function () {
    var m = this._map;
    if (!m) return;
    var size = m.getSize(), tl = m.containerPointToLayerPoint([0, 0]);
    var cvs = [this._fill, this._mark];
    for (var i = 0; i < cvs.length; i++) {
      L.DomUtil.setPosition(cvs[i], tl);
      if (cvs[i].width !== size.x || cvs[i].height !== size.y) { cvs[i].width = size.x; cvs[i].height = size.y; }
    }
    var fctx = this._fill.getContext('2d'), mctx = this._mark.getContext('2d');
    fctx.clearRect(0, 0, size.x, size.y);
    mctx.clearRect(0, 0, size.x, size.y);
    var id = state.layer;
    if (!grid || !isGrid(id)) return;
    var arr = id === 'temp' ? grid.t : (id === 'clouds' ? grid.c : (id === 'wind' ? grid.ws : grid.p));
    var step = 12;
    for (var y = 0; y < size.y; y += step) {
      for (var x = 0; x < size.x; x += step) {
        var ll = m.containerPointToLatLng([x + step / 2, y + step / 2]);
        fctx.fillStyle = colorFor(id, sample(arr, ll.lat, lonFor(ll)));
        fctx.fillRect(x - 1, y - 1, step + 2, step + 2);
      }
    }
    var ink = state.dark ? '#ffffff' : '#14161c', halo = state.dark ? 'rgba(10,12,18,.75)' : 'rgba(255,255,255,.85)';
    var gap = id === 'wind' ? 58 : 96;
    mctx.textAlign = 'center'; mctx.textBaseline = 'middle'; mctx.font = '600 13px sans-serif'; mctx.lineJoin = 'round';
    for (var py = gap / 2; py < size.y; py += gap) {
      for (var px = gap / 2; px < size.x; px += gap) {
        var p = m.containerPointToLatLng([px, py]), lon = lonFor(p);
        if (id === 'wind') {
          var spd = sample(grid.ws, p.lat, lon);
          var dir = sample(grid.wd, p.lat, lon) + 180;
          var len = Math.min(22, 8 + spd * 0.28);
          mctx.save(); mctx.translate(px, py); mctx.rotate(dir * Math.PI / 180);
          mctx.strokeStyle = ink; mctx.fillStyle = ink; mctx.lineWidth = 2.2; mctx.lineCap = 'round';
          mctx.beginPath(); mctx.moveTo(0, len); mctx.lineTo(0, -len); mctx.stroke();
          mctx.beginPath(); mctx.moveTo(0, -len - 3); mctx.lineTo(-6, -len + 7); mctx.lineTo(6, -len + 7); mctx.closePath(); mctx.fill();
          mctx.restore();
        } else {
          var val = sample(arr, p.lat, lon);
          var label = id === 'temp' ? fmtTemp(val) : (id === 'clouds' ? Math.round(val) + '%' : String(Math.round(val)));
          mctx.lineWidth = 4; mctx.strokeStyle = halo; mctx.strokeText(label, px, py);
          mctx.fillStyle = ink; mctx.fillText(label, px, py);
        }
      }
    }
    this._fill.style.opacity = 1;
    this._mark.style.opacity = 1;
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

var ARM = 'M50 50C36 50 28 40 30 28C32 14 48 6 64 10C50 14 44 24 48 34C49 40 52 46 50 50Z';
var CY_SVG = '<svg viewBox="0 0 100 100"><circle class="ring" cx="50" cy="50" r="46"/><g class="arms"><path d="' + ARM + '"/><path d="' + ARM + '" transform="rotate(120 50 50)"/><path d="' + ARM + '" transform="rotate(240 50 50)"/></g><circle class="eye" cx="50" cy="50" r="9"/></svg>';

function stormIcon(cat, north, name) {
  var html = '<div class="cy' + (north ? ' n' : '') + '" style="--c:' + rgb(cat.c) + ';--s:' + cat.s + 'px;--d:' + cat.d + 's">' + CY_SVG + '<span class="cn">' + esc(name) + '</span></div>';
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

var FLAME = '<svg viewBox="0 0 24 24"><path fill="#ff5722" d="M12 2C12 6 7 8.5 7 14a5 5 0 0 0 10 0c0-2-1-3.5-2-5 0 2-1 3-2 3 1-4 0-8-1-10z"/><path fill="#ffd54f" d="M12 19a3 3 0 0 0 3-3c0-1.6-1.2-2.6-3-4.5C10.2 13.4 9 14.4 9 16a3 3 0 0 0 3 3z"/></svg>';

function fireMarker(p) {
  var size = 26 + (p.mag > 0 ? Math.min(14, Math.round(Math.log(p.mag + 1) * 1.6)) : 0);
  var icon = L.divIcon({className:'fl', html:'<div style="--k:' + (0.9 + (p.i % 5) * 0.15).toFixed(2) + 's">' + FLAME + '</div>', iconSize:[size, size], iconAnchor:[size / 2, size]});
  var m = L.marker([p.lat, p.lon], {icon:icon, keyboard:false});
  var info = '<b>' + esc(lbl('fire', 'Wildfire')) + '</b><br>' + esc(p.title);
  if (p.date) info += '<br>' + esc(p.date.slice(0, 10));
  m.bindPopup(info, {closeButton:false, offset:[0, -size / 2]});
  return m;
}

function renderFires(pts, layer) {
  layer.clearLayers();
  var z = map.getZoom(), buckets = {};
  pts.forEach(function (p) {
    var pt = map.project([p.lat, p.lon], z);
    var k = Math.floor(pt.x / 60) + ':' + Math.floor(pt.y / 60);
    (buckets[k] = buckets[k] || []).push(p);
  });
  Object.keys(buckets).forEach(function (k) {
    var b = buckets[k];
    if (b.length === 1 || z >= 9) {
      b.forEach(function (p) { layer.addLayer(fireMarker(p)); });
      return;
    }
    var lat = 0, lon = 0;
    b.forEach(function (p) { lat += p.lat; lon += p.lon; });
    lat /= b.length; lon /= b.length;
    var size = 34 + Math.min(22, Math.round(Math.log(b.length) * 7));
    var icon = L.divIcon({className:'fg', html:'<div>' + b.length + '</div>', iconSize:[size, size], iconAnchor:[size / 2, size / 2]});
    var m = L.marker([lat, lon], {icon:icon, keyboard:false});
    m.on('click', function () { map.setView([lat, lon], Math.min(12, map.getZoom() + 2)); });
    layer.addLayer(m);
  });
}

function parseFires(events) {
  var out = [];
  events.forEach(function (ev, i) {
    var geo = (ev.geometry || []).filter(function (g) { return g.type === 'Point' && g.coordinates && g.coordinates.length >= 2; });
    if (!geo.length) return;
    geo.sort(function (a, b) { return new Date(a.date) - new Date(b.date); });
    var g = geo[geo.length - 1];
    out.push({lat:g.coordinates[1], lon:g.coordinates[0], title:ev.title || '', date:g.date || '', mag:Number(g.magnitudeValue) || 0, i:i});
  });
  return out;
}

function loadFires(seq) {
  var group = L.layerGroup();
  overlay = group.addTo(map);
  state.busy = true; setLegend('');
  var modis = L.tileLayer('https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MODIS_Terra_Thermal_Anomalies_All/default/default/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png', {maxNativeZoom:6, opacity:0.9, tileSize:256, className:'fire-tiles'});
  group.addLayer(modis);
  var viirsUrls = [9, 8].map(function (lv) {
    return 'https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_SNPP_Thermal_Anomalies_375m_All/default/default/GoogleMapsCompatible_Level' + lv + '/4/6/9.png';
  });
  probe(viirsUrls, function (idx) {
    if (seq !== state.seq) return;
    var lv = [9, 8][idx];
    group.addLayer(gibsLive('VIIRS_SNPP_Thermal_Anomalies_375m_All', lv, 0.95));
    group.addLayer(gibsLive('VIIRS_NOAA20_Thermal_Anomalies_375m_All', lv, 0.95));
  }, function () {});
  var markers = L.layerGroup();
  group.addLayer(markers);
  var pts = [];
  var redo = function () { renderFires(pts, markers); };
  map.on('zoomend', redo);
  off = function () { map.off('zoomend', redo); };
  var fresh = cache.fires && Date.now() - cache.firesAt < 600000;
  var p = fresh ? Promise.resolve(cache.fires) : getJson(EONET + 'wildfires').then(function (d) {
    cache.fires = d.events || []; cache.firesAt = Date.now(); return cache.fires;
  });
  p.then(function (events) {
    if (seq !== state.seq) return;
    pts = parseFires(events);
    redo();
  }).catch(function () {}).then(function () {
    if (seq === state.seq) { state.busy = false; setLegend(''); }
  });
}

function setLayer(id, title, err, f, labels) {
  state.layer = id; state.title = title; state.err = err; state.f = f; state.busy = false;
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
    overlay = new FieldLayer().addTo(map);
    loadGrid(true);
  }
  setLegend('');
}

var COLS = 10, ROWS = 8;

function wrapLon(x) { return ((x + 540) % 360) - 180; }

function loadGrid(force) {
  if (!isGrid(state.layer)) return;
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
    var a = Math.min(1, v / 100) * 0.78;
    return state.dark ? 'rgba(214,222,240,' + a + ')' : 'rgba(86,98,122,' + a + ')';
  }
  var key = id === 'temp' ? 'temp' : (id === 'wind' ? 'wind' : 'pressure');
  var c = ramp(RAMPS[key], v);
  return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',0.6)';
}

map.on('moveend', function () {
  if (!isGrid(state.layer)) return;
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
