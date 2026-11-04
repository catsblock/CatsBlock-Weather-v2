package com.catsblock.weather.ui

internal object MapLayerScripts {

    val source: String = """
var extra = null, land = null, landLoading = false, landWaiters = [];
var hist = {el:null, cfg:null, min:0, max:0, frames:[], host:'', idx:0, timer:null};
var HOUR = 3600000, DAY = 86400000;
var OM = 'https://api.open-meteo.com/v1/forecast';
var OM_ARCHIVE = 'https://archive-api.open-meteo.com/v1/archive';
var OM_PAST = 'https://historical-forecast-api.open-meteo.com/v1/forecast';
var GRID_KEY = {temp:'t', clouds:'c', wind:'ws', pressure:'p', dew:'dp', uv:'uv', aqi:'aq', sea:'sl', drought:'dr'};
var LEVEL_COLORS = {warning:'#ffd600', alert:'#ff6d00', danger:'#e53935'};
var LEVEL_RANK = {warning:1, alert:2, danger:3};
var DRY = [[255, 236, 140], [255, 200, 60], [245, 140, 30], [216, 67, 21], [150, 20, 20]];
var LAND_URLS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_land.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_land.geojson'
];
var HISTORY = {
  precip:{mode:'frames'},
  temp:{mode:'hour', min:Date.UTC(1940, 0, 1)},
  clouds:{mode:'hour', min:Date.UTC(1940, 0, 1)},
  wind:{mode:'hour', min:Date.UTC(1940, 0, 1)},
  pressure:{mode:'hour', min:Date.UTC(1940, 0, 1)},
  dew:{mode:'hour', min:Date.UTC(1940, 0, 1)},
  uv:{mode:'hour', min:Date.UTC(2022, 0, 1)},
  aqi:{mode:'hour', min:Date.UTC(2022, 9, 1)},
  sea:{mode:'hour', min:Date.UTC(2022, 9, 1)},
  alerts:{mode:'hour', min:null},
  cyclones:{mode:'day', min:Date.UTC(2000, 0, 1)},
  sst:{mode:'day', min:Date.UTC(2002, 5, 1), lag:3},
  fires:{mode:'day', span:6},
  drought:{mode:'day', min:Date.UTC(1940, 0, 31)},
  coldfront:{mode:'day', min:Date.UTC(1940, 0, 1)}
};
var COLD = {
  N: {key:'cfN', turn:2.4, target:30, origins:[[72, -52, 160], [70, -40, 190], [66, -38, 140], [75, -35, 175]]},
  S: {key:'cfS', turn:-2.4, target:-30, origins:[[-68, -62, 20], [-69, -5, 5], [-68, 45, 15], [-67, 100, 10], [-68, 150, 25], [-69, -110, 355]]}
};

map.createPane('zonepane').style.zIndex = 400;

function openDetail(type, data) {
  if (window.MapBridge && window.MapBridge.detail) { window.MapBridge.detail(type, JSON.stringify(data)); return; }
  var ll = data.lat !== undefined ? [data.lat, data.lon] : map.getCenter();
  var title = data.event || data.name || lbl('fire', 'Wildfire');
  L.popup({closeButton:false}).setLatLng(ll).setContent('<b>' + esc(title) + '</b>').openOn(map);
}

map.on('contextmenu', function (ev) { openDetail('municipality', {lat:ev.latlng.lat, lon:wrapLon(ev.latlng.lng)}); });

function hard(colors) {
  var n = colors.length, parts = [];
  colors.forEach(function (c, i) {
    parts.push(c + ' ' + (i * 100 / n) + '%');
    parts.push(c + ' ' + ((i + 1) * 100 / n) + '%');
  });
  return 'linear-gradient(90deg,' + parts.join(',') + ')';
}

function droughtLevel(v) {
  if (v >= 0.85) return -1;
  if (v >= 0.65) return 0;
  if (v >= 0.45) return 1;
  if (v >= 0.3) return 2;
  if (v >= 0.18) return 3;
  return 4;
}

function inConus(ll) {
  var x = wrapLon(ll.lng);
  return ll.lat > 24 && ll.lat < 50 && x > -125 && x < -66;
}

function prepareLand(d) {
  var out = [];
  (d.features || []).forEach(function (f) {
    var g = f.geometry;
    if (!g) return;
    var polys = g.type === 'Polygon' ? [g.coordinates] : (g.type === 'MultiPolygon' ? g.coordinates : []);
    polys.forEach(function (poly) {
      var outer = poly[0], w = 180, e = -180, s = 90, n = -90;
      for (var i = 0; i < outer.length; i++) {
        var p = outer[i];
        if (p[0] < w) w = p[0];
        if (p[0] > e) e = p[0];
        if (p[1] < s) s = p[1];
        if (p[1] > n) n = p[1];
      }
      out.push({rings:poly, w:w, e:e, s:s, n:n});
    });
  });
  return out;
}

function loadLand(done) {
  if (land) { done(); return; }
  landWaiters.push(done);
  if (landLoading) return;
  landLoading = true;
  var i = 0;
  (function next() {
    getJson(LAND_URLS[i]).then(function (d) {
      land = prepareLand(d);
      landLoading = false;
      var waiting = landWaiters;
      landWaiters = [];
      waiting.forEach(function (f) { f(); });
    }).catch(function () {
      i++;
      if (i < LAND_URLS.length) { next(); return; }
      landLoading = false;
      landWaiters = [];
      if (state.layer === 'sea' || state.layer === 'drought') { state.busy = false; setLegend(state.err); }
    });
  })();
}

function landPath(ctx) {
  var z = map.getZoom(), scale = 256 * Math.pow(2, z), tl = map.project(map.containerPointToLatLng([0, 0]), z);
  var b = map.getBounds(), bw = b.getWest(), be = b.getEast(), bs = b.getSouth(), bn = b.getNorth();
  var k0 = Math.floor((bw + 180) / 360), k1 = Math.floor((be + 180) / 360);
  ctx.beginPath();
  for (var k = k0; k <= k1; k++) {
    var off = k * 360;
    for (var i = 0; i < land.length; i++) {
      var p = land[i];
      if (p.e + off < bw || p.w + off > be || p.n < bs || p.s > bn) continue;
      for (var r = 0; r < p.rings.length; r++) {
        var ring = p.rings[r], px = 0, py = 0;
        for (var j = 0; j < ring.length; j++) {
          var lat = Math.max(-85.0511, Math.min(85.0511, ring[j][1])), sn = Math.sin(lat * Math.PI / 180);
          var x = (ring[j][0] + off + 180) / 360 * scale - tl.x;
          var y = (0.5 - Math.log((1 + sn) / (1 - sn)) / (4 * Math.PI)) * scale - tl.y;
          if (j === 0) { ctx.moveTo(x, y); px = x; py = y; continue; }
          if (Math.abs(x - px) < 0.6 && Math.abs(y - py) < 0.6 && j < ring.length - 1) continue;
          ctx.lineTo(x, y); px = x; py = y;
        }
        ctx.closePath();
      }
    }
  }
}

function maskLand(ctx, keepLand) {
  ctx.save();
  landPath(ctx);
  ctx.globalCompositeOperation = keepLand ? 'destination-in' : 'destination-out';
  ctx.fillStyle = '#000';
  ctx.fill('evenodd');
  ctx.restore();
}

function landThenRefresh(seq) {
  loadLand(function () { if (seq === state.seq && overlay && overlay.refresh) overlay.refresh(); });
}

function val(fn) {
  try { var v = fn(); return v === undefined ? null : v; } catch (e) { return null; }
}

function omChunk(base, la, lo, tail) {
  var url = base + '?latitude=' + la.join(',') + '&longitude=' + lo.join(',') + tail;
  return fetch(url).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); })
    .then(function (d) { return Array.isArray(d) ? d : [d]; })
    .catch(function () { return la.map(function () { return null; }); });
}

function omFetch(base, p, tail, size) {
  var jobs = [];
  for (var i = 0; i < p.lats.length; i += size) jobs.push(omChunk(base, p.lats.slice(i, i + size), p.lons.slice(i, i + size), tail));
  return Promise.all(jobs).then(function (parts) {
    var all = [].concat.apply([], parts);
    if (!all.some(function (x) { return x; })) throw new Error('none');
    return all;
  });
}

function hasData(arr) { return arr.some(function (v) { return v !== null && v !== undefined; }); }

function pad(n) { return n < 10 ? '0' + n : '' + n; }

function isoDay(ms) { return new Date(ms).toISOString().slice(0, 10); }

function histBounds(cfg) {
  var now = Date.now(), min, max;
  if (cfg.mode === 'hour') {
    max = Math.floor(now / HOUR) * HOUR;
    min = Math.ceil((cfg.min || now - 7 * DAY) / HOUR) * HOUR;
  } else {
    max = Math.floor((now - (cfg.lag || 0) * DAY) / DAY) * DAY;
    min = cfg.span !== undefined ? (Math.floor(now / DAY) - cfg.span) * DAY : (cfg.min || max - 7 * DAY);
  }
  return {min:min, max:max};
}

function histInit() {
  if (hist.el) return;
  var css = document.createElement('style');
  css.textContent = '#hist{position:absolute;z-index:1000;left:88px;right:12px;bottom:calc(var(--bottom) + 34px);display:none;flex-direction:column;gap:6px;padding:10px 14px;border-radius:22px;background:var(--chip);color:var(--fg);box-shadow:0 4px 18px rgba(0,0,0,.25);border:1px solid var(--edge);box-sizing:border-box}' +
    '#hist.on{display:flex}' +
    '#hist .hr{display:flex;align-items:center;gap:8px}' +
    '#hist .ht{flex:1;min-width:0;font-size:12px;font-weight:600;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '#hist input[type=date]{background:transparent;color:var(--fg);border:1px solid var(--edge);border-radius:12px;padding:4px 8px;font-size:12px;min-width:0}' +
    '#hist button{border:0;border-radius:14px;padding:6px 12px;font-size:12px;font-weight:700;background:var(--edge);color:var(--fg)}' +
    '#hist button.on{background:#2979ff;color:#fff}' +
    '#hist input[type=range]{width:100%;margin:0}' +
    '#hist.day #hRange{display:none}' +
    '#hist.frames #hDate{display:none}';
  document.head.appendChild(css);
  var el = document.createElement('div');
  el.id = 'hist';
  el.innerHTML = '<div class="hr"><input id="hDate" type="date"><span id="hTime" class="ht"></span><button id="hLive" type="button"></button></div><input id="hRange" type="range" min="0" max="23" step="1" value="0">';
  document.body.appendChild(el);
  hist.el = el;
  hist.date = document.getElementById('hDate');
  hist.time = document.getElementById('hTime');
  hist.live = document.getElementById('hLive');
  hist.range = document.getElementById('hRange');
  hist.date.addEventListener('change', histPick);
  hist.range.addEventListener('input', function () {
    if (hist.cfg && hist.cfg.mode === 'frames') { showFrame(+hist.range.value); histLabel(); } else { histPick(); }
  });
  hist.live.addEventListener('click', histLive);
}

function histSetup(id) {
  if (INIT.round) return;
  histInit();
  var cfg = HISTORY[id] || null;
  hist.cfg = cfg;
  hist.idx = 0;
  hist.frames = [];
  clearTimeout(hist.timer);
  if (!cfg) { hist.el.className = ''; return; }
  hist.el.className = 'on ' + cfg.mode;
  hist.live.textContent = lbl('hLive', 'Live');
  if (cfg.mode === 'frames') return;
  var b = histBounds(cfg);
  hist.min = b.min; hist.max = b.max;
  hist.date.min = isoDay(b.min); hist.date.max = isoDay(b.max);
  if (id === 'alerts') {
    fetch('__alertrange.json').then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (d) {
      if (state.layer !== 'alerts' || !d.min) return;
      cfg.min = d.min * 1000;
      var nb = histBounds(cfg);
      hist.min = nb.min; hist.date.min = isoDay(nb.min);
    }).catch(function () {});
  }
  histSync();
}

function histSync() {
  var cfg = hist.cfg;
  if (!cfg || cfg.mode === 'frames') return;
  var at = state.at === null ? hist.max : state.at, d = new Date(at);
  hist.date.value = isoDay(at);
  if (cfg.mode === 'hour') {
    hist.range.min = 0;
    hist.range.max = isoDay(at) === isoDay(hist.max) ? new Date(hist.max).getUTCHours() : 23;
    hist.range.value = d.getUTCHours();
  }
  hist.time.textContent = cfg.mode === 'hour' ? isoDay(at) + ' ' + pad(d.getUTCHours()) + ':00 UTC' : isoDay(at);
  hist.live.className = state.at === null ? 'on' : '';
}

function histLabel() {
  var f = hist.frames[hist.idx];
  if (!hist.el || !f) return;
  hist.time.textContent = new Date(f.time * 1000).toLocaleString([], {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});
  hist.live.className = hist.idx === hist.frames.length - 1 ? 'on' : '';
}

function histPick() {
  var cfg = hist.cfg, day = Date.parse(hist.date.value + 'T00:00:00Z');
  if (!cfg || isNaN(day)) return;
  var at = day + (cfg.mode === 'hour' ? Math.min(+hist.range.value, 23) * HOUR : 0);
  at = Math.max(hist.min, Math.min(hist.max, at));
  histApply(at >= hist.max ? null : at);
}

function histLive() {
  if (hist.cfg && hist.cfg.mode === 'frames') { showFrame(hist.frames.length - 1); hist.range.value = hist.idx; histLabel(); return; }
  histApply(null);
}

function histApply(at) {
  state.at = at;
  histSync();
  clearTimeout(hist.timer);
  hist.timer = setTimeout(function () { startLayer(state.layer); }, 350);
}

function framesReady(host, past) {
  hist.frames = past; hist.host = host;
  showFrame(past.length - 1);
  if (!hist.el || !hist.cfg || hist.cfg.mode !== 'frames') return;
  hist.range.min = 0; hist.range.max = past.length - 1; hist.range.value = hist.idx;
  histLabel();
}

function showFrame(i) {
  var f = hist.frames[i];
  if (!f) return;
  var next = L.tileLayer(hist.host + f.path + '/256/{z}/{x}/{y}/2/1_1.png', {maxNativeZoom:7, opacity:0.82, tileSize:256}).addTo(map);
  next.setZIndex(5);
  if (overlay) map.removeLayer(overlay);
  overlay = next;
  hist.idx = i;
}

function usdmDate(ms) {
  var back = (new Date(ms).getUTCDay() + 5) % 7;
  return new Date(ms - back * DAY).toISOString().slice(0, 10).replace(/-/g, '');
}

function omSource(at, older) { return Date.now() - at < 6 * DAY ? OM : older; }

function atTail(vars, at) {
  var d = isoDay(at);
  return '&hourly=' + vars + '&start_date=' + d + '&end_date=' + d + '&timezone=UTC';
}

function hourAt(it, name, at) {
  return val(function () { return it.hourly[name][new Date(at).getUTCHours()]; });
}

function fetchWeatherAt(p, id, at) {
  var vars = id === 'dew' ? 'dew_point_2m' : (id === 'uv' ? 'uv_index' : 'temperature_2m,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m');
  var base = omSource(at, id === 'uv' ? OM_PAST : OM_ARCHIVE);
  return omFetch(base, p, atTail(vars, at) + '&wind_speed_unit=kmh', 60).then(function (d) {
    var g = {t:[], c:[], p:[], ws:[], wd:[], dp:[], uv:[]};
    d.forEach(function (it) {
      g.t.push(hourAt(it, 'temperature_2m', at));
      g.c.push(hourAt(it, 'cloud_cover', at));
      g.p.push(hourAt(it, 'pressure_msl', at));
      g.ws.push(hourAt(it, 'wind_speed_10m', at));
      g.wd.push(hourAt(it, 'wind_direction_10m', at));
      g.dp.push(hourAt(it, 'dew_point_2m', at));
      g.uv.push(hourAt(it, 'uv_index', at));
    });
    if (!hasData(id === 'dew' ? g.dp : (id === 'uv' ? g.uv : g.t))) throw new Error('none');
    return g;
  });
}

function fetchAirAt(p, at) {
  return omFetch('https://air-quality-api.open-meteo.com/v1/air-quality', p, atTail('us_aqi', at), 60).then(function (d) {
    var aq = d.map(function (it) { return hourAt(it, 'us_aqi', at); });
    if (!hasData(aq)) throw new Error('none');
    return {aq:aq};
  });
}

function fetchSeaAt(p, at) {
  return omFetch('https://marine-api.open-meteo.com/v1/marine', p, atTail('sea_level_height_msl', at), 12).then(function (d) {
    var sl = d.map(function (it) { return hourAt(it, 'sea_level_height_msl', at); });
    if (!hasData(sl)) throw new Error('none');
    return {sl:sl};
  });
}

function fetchDroughtAt(p, at) {
  var end = at - DAY, base = omSource(end, OM_ARCHIVE);
  var tail = '&daily=precipitation_sum,et0_fao_evapotranspiration&start_date=' + isoDay(at - 30 * DAY) + '&end_date=' + isoDay(end) + '&timezone=UTC';
  return omFetch(base, p, tail, 60).then(function (d) {
    var dr = d.map(function (it) {
      return val(function () {
        var pr = it.daily.precipitation_sum, et = it.daily.et0_fao_evapotranspiration, a = 0, b = 0;
        for (var i = 0; i < pr.length; i++) { a += pr[i] || 0; b += et[i] || 0; }
        return b < 10 ? 1.5 : a / b;
      });
    });
    if (!hasData(dr)) throw new Error('none');
    return {dr:dr};
  });
}

function fetchWeather(p, id) {
  if (state.at !== null) return fetchWeatherAt(p, id, state.at);
  var vars = id === 'dew' ? 'dew_point_2m' : (id === 'uv' ? 'uv_index' : 'temperature_2m,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m');
  var job = omFetch(OM, p, '&current=' + vars + '&wind_speed_unit=kmh&timezone=UTC', 120).then(function (d) {
    var g = {t:[], c:[], p:[], ws:[], wd:[], dp:[], uv:[]};
    d.forEach(function (it) {
      var cur = (it && it.current) || {};
      g.t.push(val(function () { return cur.temperature_2m; }));
      g.c.push(val(function () { return cur.cloud_cover; }));
      g.p.push(val(function () { return cur.pressure_msl; }));
      g.ws.push(val(function () { return cur.wind_speed_10m; }));
      g.wd.push(val(function () { return cur.wind_direction_10m; }));
      g.dp.push(val(function () { return cur.dew_point_2m; }));
      g.uv.push(val(function () { return cur.uv_index; }));
    });
    if (id === 'uv' && !hasData(g.uv)) throw new Error('none');
    return g;
  });
  if (id !== 'uv') return job;
  return job.catch(function () {
    var hour = new Date().getUTCHours();
    return omFetch(OM, p, '&hourly=uv_index&forecast_days=1&timezone=UTC', 120).then(function (d) {
      return {uv:d.map(function (it) { return val(function () { return it.hourly.uv_index[hour]; }); })};
    });
  });
}

function fetchAir(p) {
  if (state.at !== null) return fetchAirAt(p, state.at);
  return omFetch('https://air-quality-api.open-meteo.com/v1/air-quality', p, '&current=us_aqi&timezone=UTC', 120).then(function (d) {
    return {aq:d.map(function (it) { return val(function () { return it.current.us_aqi; }); })};
  });
}

function fetchSea(p) {
  if (state.at !== null) return fetchSeaAt(p, state.at);
  var hour = new Date().getUTCHours();
  return omFetch('https://marine-api.open-meteo.com/v1/marine', p, '&hourly=sea_level_height_msl&forecast_days=1&timezone=UTC', 12).then(function (d) {
    var sl = d.map(function (it) { return val(function () { return it.hourly.sea_level_height_msl[hour]; }); });
    if (!hasData(sl)) throw new Error('none');
    return {sl:sl};
  });
}

function fetchDrought(p) {
  if (state.at !== null) return fetchDroughtAt(p, state.at);
  return omFetch(OM, p, '&daily=precipitation_sum,et0_fao_evapotranspiration&past_days=30&forecast_days=1&timezone=UTC', 120).then(function (d) {
    return {dr:d.map(function (it) {
      return val(function () {
        var pr = it.daily.precipitation_sum, et = it.daily.et0_fao_evapotranspiration, a = 0, b = 0, n = pr.length - 1;
        for (var i = 0; i < n; i++) { a += pr[i] || 0; b += et[i] || 0; }
        return b < 10 ? 1.5 : a / b;
      });
    })};
  });
}

function loadGrid(force) {
  if (!isGrid(state.layer) || !state.canvas) return;
  var seq = state.seq, id = state.layer;
  var b = map.getBounds().pad(0.3);
  var s = Math.max(-80, b.getSouth()), n = Math.min(80, b.getNorth()), w = b.getWest(), e = b.getEast();
  if (e - w > 340) { w = -170; e = 170; }
  var p = {lats:[], lons:[]};
  for (var r = 0; r < ROWS; r++) {
    for (var c = 0; c < COLS; c++) {
      p.lats.push((s + (n - s) * r / (ROWS - 1)).toFixed(3));
      p.lons.push(wrapLon(w + (e - w) * c / (COLS - 1)).toFixed(3));
    }
  }
  state.busy = true; setLegend('');
  var job = id === 'aqi' ? fetchAir(p) : (id === 'sea' ? fetchSea(p) : (id === 'drought' ? fetchDrought(p) : fetchWeather(p, id)));
  job.then(function (g) {
    if (seq !== state.seq) return;
    g.s = s; g.n = n; g.w = w; g.e = e;
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
  var vs = [arr[y0 * COLS + x0], arr[y0 * COLS + x0 + 1], arr[(y0 + 1) * COLS + x0], arr[(y0 + 1) * COLS + x0 + 1]];
  var ws = [(1 - tx) * (1 - ty), tx * (1 - ty), (1 - tx) * ty, tx * ty], sum = 0, wt = 0;
  for (var i = 0; i < 4; i++) {
    if (vs[i] !== null && vs[i] !== undefined && !isNaN(vs[i])) { sum += vs[i] * ws[i]; wt += ws[i]; }
  }
  return wt > 0.001 ? sum / wt : NaN;
}

function colorFor(id, v, ll) {
  if (v === null || v === undefined || isNaN(v)) return [0, 0, 0, 0];
  if (id === 'clouds') {
    var a = Math.min(1, v / 100) * 0.85, b = state.dark ? [236, 242, 252] : [255, 255, 255];
    return [b[0], b[1], b[2], Math.round(a * 255)];
  }
  if (id === 'drought') {
    if (state.usdm && ll && inConus(ll)) return [0, 0, 0, 0];
    var lv = droughtLevel(v);
    if (lv < 0) return [0, 0, 0, 0];
    var d = DRY[lv];
    return [d[0], d[1], d[2], 165];
  }
  if (id === 'uv' && v < 0.3) return [0, 0, 0, 0];
  var c = ramp(RAMPS[id], v);
  return [Math.round(c[0]), Math.round(c[1]), Math.round(c[2]), id === 'sea' ? 210 : 184];
}

function loadUsdm(seq) {
  fetch(state.at === null ? '__drought.json' : '__drought.json?d=' + usdmDate(state.at)).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (fc) {
    if (seq !== state.seq || !fc.features || !fc.features.length) return;
    extra = L.geoJSON(fc, {
      renderer:L.canvas({pane:'zonepane'}),
      interactive:false,
      style:function (f) {
        var c = rgb(DRY[Math.max(0, Math.min(4, f.properties.lvl))]);
        return {color:c, weight:0.8, opacity:0.9, fillColor:c, fillOpacity:0.62};
      }
    }).addTo(map);
    state.usdm = true;
    if (overlay && overlay.refresh) overlay.refresh();
  }).catch(function () {});
}

function loadAlerts(seq) {
  state.busy = true; setLegend('');
  fetch(state.at === null ? '__alerts.json' : '__alerts.json?at=' + Math.floor(state.at / 1000)).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (fc) {
    if (seq !== state.seq) return;
    var feats = (fc.features || []).slice().sort(function (a, b) { return LEVEL_RANK[a.properties.level] - LEVEL_RANK[b.properties.level]; });
    overlay = L.geoJSON({type:'FeatureCollection', features:feats}, {
      renderer:L.canvas({pane:'zonepane', padding:0.5}),
      style:function (f) {
        var c = LEVEL_COLORS[f.properties.level] || LEVEL_COLORS.warning;
        return {color:c, weight:1.6, opacity:0.95, fillColor:c, fillOpacity:0.38};
      },
      onEachFeature:function (f, layer) {
        layer.on('click', function (ev) {
          L.DomEvent.stopPropagation(ev);
          var d = JSON.parse(JSON.stringify(f.properties));
          d.lat = ev.latlng.lat; d.lon = wrapLon(ev.latlng.lng);
          openDetail('alert', d);
        });
      }
    }).addTo(map);
    state.busy = false;
    setLegend(feats.length ? '' : lbl('noAlerts', 'No active alerts right now'));
  }).catch(function () { if (seq === state.seq) { state.busy = false; setLegend(state.err); } });
}

function seasonFactor(date) {
  var start = new Date(Date.UTC(date.getUTCFullYear(), 0, 0));
  var doy = Math.floor((date - start) / 86400000);
  return (1 + Math.cos(2 * Math.PI * (doy - 20) / 365.25)) / 2;
}

function dest(lat, lon, bearing, deg) {
  var r = Math.PI / 180, d = deg * r, b = bearing * r, p1 = lat * r, l1 = lon * r;
  var p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(b));
  var l2 = l1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
  return [p2 / r, l2 / r];
}

function frontPath(origin, turn, reach, target) {
  var pts = [[origin[0], origin[1]]], lat = origin[0], lon = origin[1], b = origin[2];
  for (var i = 0; i < 90; i++) {
    var n = dest(lat, lon, b, 1.2);
    while (n[1] - lon > 180) n[1] -= 360;
    while (lon - n[1] > 180) n[1] += 360;
    lat = n[0]; lon = n[1]; b += turn;
    pts.push([lat, lon]);
    if (Math.abs(lat - origin[0]) >= reach || Math.abs(lat) <= Math.abs(target)) break;
  }
  return pts;
}

function loadColdFront(seq) {
  var group = L.layerGroup();
  overlay = group.addTo(map);
  var heads = [], flow = null, rebuild = null;
  function build() {
    if (seq !== state.seq) return;
    group.clearLayers(); heads = [];
    var fN = seasonFactor(new Date(state.at === null ? Date.now() : state.at)), fS = 1 - fN;
    [['N', fN], ['S', fS]].forEach(function (h) {
      var cfg = COLD[h[0]], f = h[1], name = lbl(cfg.key, h[0] === 'N' ? 'Greenland' : 'Antarctica');
      cfg.origins.forEach(function (o, idx) {
        var vf = Math.min(1, Math.max(0.05, f * (0.88 + 0.12 * Math.sin(idx * 1.7 + 1))));
        var span = Math.abs(o[0] - cfg.target), reach = 7 + (span - 7) * (0.15 + 0.85 * vf);
        var path = frontPath(o, cfg.turn, reach, cfg.target);
        if (path.length < 3) return;
        var info = '<b>' + esc(lbl('cf', 'Cold fronts')) + '</b><br>' + esc(name) + ' · ' + esc(lbl('cfAdvance', 'Advance')) + ' ' + Math.round(vf * 100) + '%';
        group.addLayer(L.polyline(path, {pane:'trackpane', color:'#ffffff', weight:7, opacity:0.28, lineCap:'round', interactive:false}));
        var line = L.polyline(path, {pane:'trackpane', color:'#1e88e5', weight:3.4, opacity:0.95, dashArray:'10 8', className:'cfflow', lineCap:'round'});
        line.bindPopup(info, {closeButton:false});
        group.addLayer(line);
        for (var i = 5; i < path.length - 1; i += 6) {
          var a = map.latLngToLayerPoint(path[i]), c = map.latLngToLayerPoint(path[i + 1]);
          var ang = Math.atan2(c.y - a.y, c.x - a.x) * 180 / Math.PI;
          group.addLayer(L.marker(path[i], {pane:'trackpane', interactive:false, keyboard:false, icon:L.divIcon({className:'cfw', html:'<div class="cfm" style="transform:rotate(' + ang.toFixed(1) + 'deg)"></div>', iconSize:[14, 14], iconAnchor:[7, 7]})}));
        }
        var head = L.marker(path[0], {pane:'trackpane', interactive:false, keyboard:false, icon:L.divIcon({className:'cfw', html:'<div class="cfh"></div>', iconSize:[16, 16], iconAnchor:[8, 8]})});
        group.addLayer(head);
        heads.push({m:head, path:path});
        if (idx === 0) {
          group.addLayer(L.marker(path[0], {pane:'trackpane', interactive:false, keyboard:false, icon:L.divIcon({className:'cfw', html:'<div class="cfo">' + esc(name) + ' · ' + Math.round(f * 100) + '%</div>', iconSize:[0, 0]})}));
        }
      });
    });
    setLegend(lbl('cfN', 'Greenland') + ' ' + Math.round(fN * 100) + '% · ' + lbl('cfS', 'Antarctica') + ' ' + Math.round(fS * 100) + '%');
  }
  build();
  flow = setInterval(function () {
    var t = Date.now() / 16000;
    heads.forEach(function (h, i) {
      var u = (t + i * 0.17) % 1, pos = u * (h.path.length - 1), k = Math.floor(pos), r = pos - k;
      var a = h.path[k], c = h.path[Math.min(k + 1, h.path.length - 1)];
      h.m.setLatLng([a[0] + (c[0] - a[0]) * r, a[1] + (c[1] - a[1]) * r]);
    });
  }, 90);
  rebuild = setInterval(build, 600000);
  map.on('zoomend', build);
  off = function () { clearInterval(flow); clearInterval(rebuild); map.off('zoomend', build); };
}

function parseFires(events) {
  var out = [];
  events.forEach(function (ev) {
    (ev.geometry || []).forEach(function (g) {
      if (g.type === 'Point' && g.coordinates && g.coordinates.length >= 2) {
        out.push([Math.floor(g.coordinates[1] / 0.25) * 0.25, Math.floor(g.coordinates[0] / 0.25) * 0.25, 1, 0, 0, 0, 0, 0, 0]);
      }
    });
  });
  return out;
}

var HZ = ['url(#hz1)', 'url(#hz2)', 'url(#hz3)'];
var HC = ['#ffb300', '#ff6d00', '#e53935'];

function showFire(e, cs) {
  if (!(window.MapBridge && window.MapBridge.detail)) return;
  var q = '__firedetail.json?lat=' + e.lat + '&lon=' + e.lon + '&size=' + cs + '&n=' + e.n + '&frp=' + e.frp + '&first=' + e.first +
    '&last=' + e.last + '&bright=' + e.bright + '&conf=' + e.conf + '&night=' + e.night;
  fetch(q).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (d) {
    openDetail('fire', d);
  }).catch(function () {
    openDetail('fire', {lat:e.lat + cs / 2, lon:e.lon + cs / 2, hotspots:e.n, frp:e.frp, status:'active', cause:'investigating', behavior:'active', modeled:[], detected:'', reported:'', updated:'', costUsd:0, areaHa:e.n * 20, fuelKey:'temperate', fuelText:'', brightness:e.bright, confidence:e.conf, night:e.night > 0});
  });
}

function renderFires(cells, group) {
  group.clearLayers();
  var z = map.getZoom(), cs = z <= 3.5 ? 4 : (z <= 4.5 ? 2 : (z <= 5.5 ? 1 : (z <= 6.5 ? 0.5 : 0.25)));
  var b = map.getBounds().pad(0.2), m = {};
  for (var i = 0; i < cells.length; i++) {
    var c = cells[i];
    if (c[0] > b.getNorth() || c[0] + 0.25 < b.getSouth() || c[1] > b.getEast() || c[1] + 0.25 < b.getWest()) continue;
    var k = Math.floor(c[0] / cs) + ':' + Math.floor(c[1] / cs), e = m[k];
    if (!e) {
      m[k] = {lat:Math.floor(c[0] / cs) * cs, lon:Math.floor(c[1] / cs) * cs, n:c[2], frp:c[3], first:c[4] || 0, last:c[5] || 0, bright:c[6] || 0, conf:c[7] || 0, night:c[8] || 0};
    } else {
      e.n += c[2];
      if (c[3] > e.frp) e.frp = c[3];
      if (c[4] && (!e.first || c[4] < e.first)) e.first = c[4];
      if ((c[5] || 0) > e.last) e.last = c[5];
      if ((c[6] || 0) > e.bright) e.bright = c[6];
      if ((c[7] || 0) > e.conf) e.conf = c[7];
      e.night += c[8] || 0;
    }
  }
  Object.keys(m).forEach(function (k) {
    var e = m[k], lv = (e.n >= 12 || e.frp >= 150) ? 2 : ((e.n >= 3 || e.frp >= 40) ? 1 : 0);
    var r = L.rectangle([[e.lat, e.lon], [e.lat + cs, e.lon + cs]], {pane:'trackpane', color:HC[lv], weight:1.6, opacity:0.95, fillColor:HZ[lv], fillOpacity:1, lineJoin:'round'});
    if (window.MapBridge && window.MapBridge.detail) {
      r.on('click', function (ev) { L.DomEvent.stopPropagation(ev); showFire(e, cs); });
    } else {
      var info = '<b>' + esc(lbl('fire', 'Wildfire')) + '</b><br>' + esc(lbl('hotspots', 'Hotspots')) + ': ' + e.n;
      if (e.frp > 0) info += '<br>FRP: ' + Math.round(e.frp) + ' MW';
      r.bindPopup(info, {closeButton:false});
    }
    group.addLayer(r);
  });
}

function loadFires(seq) {
  var group = L.layerGroup();
  overlay = group.addTo(map);
  state.busy = true; setLegend('');
  var cells = [], day = state.at === null ? null : Math.floor(state.at / DAY);
  var redo = function () { renderFires(cells, group); };
  map.on('moveend zoomend', redo);
  off = function () { map.off('moveend zoomend', redo); };
  var fresh = day === null && cache.fires && Date.now() - cache.firesAt < 600000;
  var p = fresh ? Promise.resolve(cache.fires) : fetch(day === null ? '__fires.json' : '__fires.json?day=' + day).then(function (r) {
    if (!r.ok) throw new Error('http');
    return r.json();
  }).catch(function () {
    if (day !== null) throw new Error('none');
    return getJson(EONET + 'wildfires').then(function (d) { return parseFires(d.events || []); });
  }).then(function (c) {
    if (day === null) { cache.fires = c; cache.firesAt = Date.now(); }
    return c;
  });
  p.then(function (c) {
    if (seq !== state.seq) return;
    cells = c;
    redo();
    state.busy = false; setLegend('');
  }).catch(function () {
    if (seq === state.seq) { state.busy = false; setLegend(day === null ? '' : state.err); }
  });
}
""".trimIndent()
}
