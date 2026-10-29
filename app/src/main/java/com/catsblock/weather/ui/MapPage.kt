package com.catsblock.weather.ui

internal object MapPage {

    const val BASE_URL = "https://weather.catsblock.app/"

    val html: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css">
<style>
:root{--top:0px;--bottom:0px}
body.light{--bg:#eef0f5;--fg:#1b1d24;--chip:rgba(255,255,255,.92);--sub:#5b6070;--edge:rgba(0,0,0,.08)}
body.dark{--bg:#14161c;--fg:#e9ebf2;--chip:rgba(32,35,44,.92);--sub:#a5abbb;--edge:rgba(255,255,255,.1)}
html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:var(--bg);font-family:sans-serif;-webkit-tap-highlight-color:transparent}
#map{position:absolute;inset:0;background:var(--bg)}
#field{position:absolute;inset:0;pointer-events:none;z-index:450;filter:blur(7px);transition:opacity .45s}
#marks{position:absolute;inset:0;pointer-events:none;z-index:460}
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
.pin{width:22px;height:22px;border-radius:50%;background:#2979ff;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.4);position:relative}
.pin:after{content:"";position:absolute;inset:-12px;border-radius:50%;background:rgba(41,121,255,.28);animation:pp 2s ease-out infinite}
@keyframes pp{0%{transform:scale(.4);opacity:1}100%{transform:scale(1.5);opacity:0}}
</style>
</head>
<body class="light">
<div id="map"></div>
<canvas id="field"></canvas>
<canvas id="marks"></canvas>
<div id="legend"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
<script>
var map = L.map('map', {zoomControl:false, attributionControl:false, minZoom:2, maxZoom:12, zoomSnap:0.25, worldCopyJump:true}).setView([0, 0], 4);
L.control.attribution({position:'bottomright', prefix:false}).addTo(map)
  .addAttribution('© OpenStreetMap · CARTO · Open-Meteo · RainViewer · NASA GIBS · NOAA NHC');

var state = {dark:false, layer:'none', title:'', err:'', f:false, lat:0, lon:0, seq:0};
var base = null, overlay = null, pin = null, grid = null, gridAt = 0, gridZoom = 0, timer = null, frame = 0;
var fieldCv = document.getElementById('field');
var marksCv = document.getElementById('marks');
var legend = document.getElementById('legend');

var RAMPS = {
  temp: [[-30,[94,60,153]],[-15,[59,111,212]],[0,[79,195,247]],[10,[102,187,106]],[20,[253,216,53]],[30,[251,140,0]],[40,[211,47,47]],[50,[123,31,162]]],
  wind: [[0,[42,111,219]],[15,[38,198,218]],[30,[102,187,106]],[50,[253,216,53]],[75,[251,140,0]],[100,[211,47,47]],[140,[142,36,170]]],
  pressure: [[980,[123,50,148]],[995,[59,111,212]],[1005,[79,195,247]],[1013,[224,224,224]],[1022,[255,183,77]],[1030,[244,81,30]],[1045,[183,28,28]]]
};

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

function css(stops) {
  var lo = stops[0][0], hi = stops[stops.length - 1][0], out = [];
  for (var i = 0; i < stops.length; i++) {
    var c = stops[i][1];
    out.push('rgb(' + c[0] + ',' + c[1] + ',' + c[2] + ') ' + Math.round((stops[i][0] - lo) / (hi - lo) * 100) + '%');
  }
  return 'linear-gradient(90deg,' + out.join(',') + ')';
}

function isGrid(id) { return id === 'temp' || id === 'clouds' || id === 'wind' || id === 'pressure'; }

function fmtTemp(c) { return state.f ? Math.round(c * 9 / 5 + 32) + '°' : Math.round(c) + '°'; }

function setLegend(extra) {
  var id = state.layer;
  if (id === 'none') { legend.className = ''; return; }
  var html = '<div class="t">' + state.title + '</div>';
  var grad = null, lo = '', hi = '';
  if (id === 'temp') { grad = css(RAMPS.temp); lo = fmtTemp(-30); hi = fmtTemp(50); }
  else if (id === 'wind') { grad = css(RAMPS.wind); lo = '0'; hi = '140 km/h'; }
  else if (id === 'pressure') { grad = css(RAMPS.pressure); lo = '980'; hi = '1045 hPa'; }
  else if (id === 'clouds') { grad = 'linear-gradient(90deg,rgba(150,160,180,0),rgba(150,160,180,.95))'; lo = '0%'; hi = '100%'; }
  else if (id === 'precip') { grad = 'linear-gradient(90deg,#8fd3f4,#2f80ed,#27ae60,#f2c94c,#eb5757,#9b51e0)'; lo = '·'; hi = '···'; }
  else if (id === 'sst') { grad = 'linear-gradient(90deg,#2b6cb0,#e6eef7,#e53e3e)'; lo = '-3°'; hi = '+3°'; }
  else if (id === 'fires') { grad = 'linear-gradient(90deg,#ffb300,#ff5722,#d50000)'; lo = '·'; hi = '···'; }
  else if (id === 'cyclones') { grad = 'linear-gradient(90deg,#4fc3f7,#fdd835,#fb8c00,#d32f2f)'; lo = 'TD'; hi = 'H5'; }
  if (grad) {
    html += '<div class="bar" style="--grad:' + grad + '"></div><div class="r"><span>' + lo + '</span><span>' + hi + '</span></div>';
  }
  if (extra) html += '<div class="m">' + extra + '</div>';
  legend.innerHTML = html;
  legend.className = 'on' + (state.busy ? ' busy' : '');
}

function setTheme(dark) {
  state.dark = dark;
  document.body.className = dark ? 'dark' : 'light';
  if (base) map.removeLayer(base);
  base = L.tileLayer('https://{s}.basemaps.cartocdn.com/' + (dark ? 'dark_all' : 'light_all') + '/{z}/{x}/{y}{r}.png', {subdomains:'abcd', maxZoom:19}).addTo(map);
  base.setZIndex(1);
  redraw();
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

var NHC = L.TileLayer.extend({
  getTileUrl: function (c) {
    var z = this._getZoomForUrl(), size = 40075016.6855784 / Math.pow(2, z);
    var x0 = -20037508.3427892 + c.x * size, y1 = 20037508.3427892 - c.y * size;
    return 'https://mapservices.weather.noaa.gov/tropical/rest/services/tropical/NHC_tropical_weather/MapServer/export?bbox=' +
      [x0, y1 - size, x0 + size, y1].join(',') + '&bboxSR=3857&imageSR=3857&size=256,256&dpi=96&format=png32&transparent=true&f=image';
  }
});

function clearOverlay() {
  if (overlay) { map.removeLayer(overlay); overlay = null; }
  grid = null;
  fieldCv.style.opacity = 0;
  var ctx = marksCv.getContext('2d');
  ctx.clearRect(0, 0, marksCv.width, marksCv.height);
}

function setLayer(id, title, err, f) {
  state.layer = id; state.title = title; state.err = err; state.f = f; state.busy = false;
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
    overlay = new NHC('', {opacity:0.95, tileSize:256, maxNativeZoom:10}).addTo(map);
    overlay.setZIndex(6);
  } else if (id === 'sst') {
    overlay = gibs('GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies_v4.1_STD', 7, 3, 0.8).addTo(map);
    overlay.setZIndex(4);
  } else if (id === 'fires') {
    overlay = L.layerGroup([
      gibs('VIIRS_SNPP_Thermal_Anomalies_375m_Day_v1_NRT', 8, 1, 0.95),
      gibs('VIIRS_SNPP_Thermal_Anomalies_375m_Night_v1_NRT', 8, 1, 0.95)
    ]).addTo(map);
  } else if (isGrid(id)) {
    loadGrid(true);
  }
  setLegend('');
}

var COLS = 8, ROWS = 6;

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
    fieldCv.style.opacity = 1;
    redraw();
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

function redraw() {
  if (frame) return;
  frame = requestAnimationFrame(function () { frame = 0; paint(); });
}

function paint() {
  var size = map.getSize(), dpr = 1;
  if (fieldCv.width !== size.x || fieldCv.height !== size.y) { fieldCv.width = size.x; fieldCv.height = size.y; marksCv.width = size.x; marksCv.height = size.y; }
  var fctx = fieldCv.getContext('2d'), mctx = marksCv.getContext('2d');
  fctx.clearRect(0, 0, size.x, size.y); mctx.clearRect(0, 0, size.x, size.y);
  var id = state.layer;
  if (!grid || !isGrid(id)) return;
  var arr = id === 'temp' ? grid.t : (id === 'clouds' ? grid.c : (id === 'wind' ? grid.ws : grid.p));
  var step = 12;
  for (var y = 0; y < size.y; y += step) {
    for (var x = 0; x < size.x; x += step) {
      var ll = map.containerPointToLatLng([x + step / 2, y + step / 2]);
      var v = sample(arr, ll.lat, lonFor(ll));
      fctx.fillStyle = colorFor(id, v);
      fctx.fillRect(x - 1, y - 1, step + 2, step + 2);
    }
  }
  var ink = state.dark ? '#ffffff' : '#14161c', halo = state.dark ? 'rgba(10,12,18,.75)' : 'rgba(255,255,255,.85)';
  var gap = id === 'wind' ? 58 : 96;
  mctx.textAlign = 'center'; mctx.textBaseline = 'middle'; mctx.font = '600 13px sans-serif'; mctx.lineJoin = 'round';
  for (var py = gap / 2; py < size.y; py += gap) {
    for (var px = gap / 2; px < size.x; px += gap) {
      var p = map.containerPointToLatLng([px, py]), lon = lonFor(p);
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
}

map.on('move zoom resize', redraw);
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
</script>
</body>
</html>
""".trimIndent()
}
