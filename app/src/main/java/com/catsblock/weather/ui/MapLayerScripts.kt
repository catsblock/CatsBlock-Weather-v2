package com.catsblock.weather.ui

internal object MapLayerScripts {

    val source: String = """
var extra = null, land = null, landLoading = false, landWaiters = [];
var OM = 'https://api.open-meteo.com/v1/forecast';
var GRID_KEY = {temp:'t', clouds:'c', wind:'ws', pressure:'p', humidity:'rh', dew:'dp', uv:'uv', aqi:'aq', sea:'sl', drought:'dr'};
var LEVEL_COLORS = {warning:'#ffd600', alert:'#ff6d00', danger:'#e53935'};
var LEVEL_RANK = {warning:1, alert:2, danger:3};
var DRY = [[255, 236, 140], [255, 200, 60], [245, 140, 30], [216, 67, 21], [150, 20, 20]];
var LAND_URLS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_land.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_land.geojson'
];
var FRONT = {drop:4, none:999, step:3, window:2};
var frontHits = [], zoneCanvas = null;
var mapTimeTimer = null;

var requestCache = new Map(), requestQueue = [], requestActive = 0, requestNextStart = 0;
var REQUEST_CACHE_LIMIT = 48, REQUEST_CONCURRENCY = 1, REQUEST_INTERVAL = 350, REQUEST_TIMEOUT = 15000;

function cloneJson(value) {
  return value === null || value === undefined ? value : JSON.parse(JSON.stringify(value));
}

function cacheJson(key, value, ttl) {
  requestCache.delete(key);
  requestCache.set(key, {value:cloneJson(value), at:Date.now(), ttl:ttl});
  while (requestCache.size > REQUEST_CACHE_LIMIT) requestCache.delete(requestCache.keys().next().value);
}

function staleJson(key) { var entry = requestCache.get(key); return entry && Date.now() - entry.at <= entry.ttl * 6 ? cloneJson(entry.value) : null; }

function cachedJson(key) {
  var entry = requestCache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.at > entry.ttl) return null;
  requestCache.delete(key);
  requestCache.set(key, entry);
  return cloneJson(entry.value);
}

function drainRequests() {
  while (requestActive < REQUEST_CONCURRENCY && requestQueue.length) {
    var job = requestQueue.shift();
    if (job.signal && job.signal.aborted) {
      job.reject(new Error('aborted'));
      continue;
    }
    requestActive++;
    var now = Date.now(), wait = Math.max(0, requestNextStart - now);
    requestNextStart = now + wait + REQUEST_INTERVAL;
    setTimeout(function (current) {
      if (current.signal && current.signal.aborted) {
        current.reject(new Error('aborted'));
        requestActive--;
        drainRequests();
        return;
      }
      Promise.resolve().then(current.run).then(current.resolve, current.reject).then(function () {
        requestActive--;
        drainRequests();
      }, function () {
        requestActive--;
        drainRequests();
      });
    }, wait, job);
  }
}

function scheduleRequest(run, signal) {
  return new Promise(function (resolve, reject) {
    requestQueue.push({run:run, signal:signal, resolve:resolve, reject:reject});
    drainRequests();
  });
}

function fetchMapLayerJson(url, options, timeoutMs) {
  options = options || {};
  var controller = new AbortController(), externalSignal = options.signal, timedOut = false;
  var requestOptions = {};
  Object.keys(options).forEach(function (key) {
    if (key !== 'signal') requestOptions[key] = options[key];
  });
  requestOptions.signal = controller.signal;
  var abortExternal = function () { controller.abort(); };
  if (externalSignal) {
    if (externalSignal.aborted) controller.abort();
    else externalSignal.addEventListener('abort', abortExternal, {once:true});
  }
  var timer = setTimeout(function () {
    timedOut = true;
    controller.abort();
  }, timeoutMs || REQUEST_TIMEOUT);
  return Promise.resolve().then(function () { return fetch(url, requestOptions); }).then(function (response) {
    if (!response.ok) {
      var error = new Error('HTTP ' + response.status);
      error.status = response.status;
      error.retryAfter = response.headers.get('Retry-After');
      throw error;
    }
    return response.json();
  }).catch(function (error) {
    if (timedOut) {
      var timeoutError = new Error('Request timed out');
      timeoutError.name = 'TimeoutError';
      throw timeoutError;
    }
    throw error;
  }).then(function (data) {
    clearTimeout(timer);
    if (externalSignal) externalSignal.removeEventListener('abort', abortExternal);
    return data;
  }, function (error) {
    clearTimeout(timer);
    if (externalSignal) externalSignal.removeEventListener('abort', abortExternal);
    throw error;
  });
}

function retryDelay(response, attempt) {
  var retryAfter = response && response.headers ? response.headers.get('Retry-After') : null;
  if (retryAfter) {
    var seconds = Number(retryAfter);
    if (isFinite(seconds)) return Math.min(30000, Math.max(250, seconds * 1000));
    var date = Date.parse(retryAfter);
    if (isFinite(date)) return Math.min(30000, Math.max(250, date - Date.now()));
  }
  return Math.min(12000, 500 * Math.pow(2, attempt)) + Math.floor(Math.random() * 250);
}

function requestJson(url, options, ttl) {
  options = options || {};
  var key = String(url), cached = cachedJson(key);
  if (cached !== null) return Promise.resolve(cached);
  var signal = options.signal || null;
  var cacheTtl = ttl || (key.indexOf('__') === 0 ? 120000 : 90000);
  function attempt(index) {
    return scheduleRequest(function () {
      if (signal && signal.aborted) throw new Error('aborted');
      return fetchMapLayerJson(url, options, REQUEST_TIMEOUT);
    }, signal).catch(function (error) {
      if (signal && signal.aborted) throw error;
      var status = error && error.status;
      if (index >= 2 || (status && status < 500 && status !== 408 && status !== 429)) {
        var stale = staleJson(key);
        if (stale !== null) return stale;
        throw error;
      }
      return new Promise(function (resolve) {
        var delay = retryDelay({headers:{get:function () { return error.retryAfter || null; }}}, index);
        setTimeout(resolve, delay);
      }).then(function () { return attempt(index + 1); });
    });
  }
  return attempt(0).then(function (value) {
    cacheJson(key, value, cacheTtl);
    return cloneJson(value);
  });
}
var gridCache = {}, GRID_TTL = 1800000, GRID_BOX = {s:-75, n:75, w:-180, e:180}, gridToken = 0, gridCtl = null, gridSignal = null;

map.createPane('zonepane').style.zIndex = 400;

function openDetail(type, data) {
  var ll = data.lat !== undefined ? [data.lat, data.lon] : map.getCenter();
  if (window.MapBridge && window.MapBridge.detail) {
    var point = map.latLngToContainerPoint(ll);
    window.MapBridge.detail(type, JSON.stringify(data), point.x, point.y);
    return;
  }
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

function landPath(ctx, size) {
  var z = map.getZoom(), scale = 256 * Math.pow(2, z), tl = map.project(map.containerPointToLatLng([0, 0]), z);
  var pd = size ? size.pad : 0, vw = size ? size.x : map.getSize().x, vh = size ? size.y : map.getSize().y;
  var b = L.latLngBounds(map.containerPointToLatLng([-pd, -pd]), map.containerPointToLatLng([vw + pd, vh + pd])), bw = b.getWest(), be = b.getEast(), bs = b.getSouth(), bn = b.getNorth();
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

function maskLand(ctx, keepLand, size) {
  ctx.save();
  landPath(ctx, size);
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
  return requestJson(url, gridSignal ? {signal:gridSignal} : null, 120000)
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

function fetchWeather(p, id) {
  var vars = id === 'humidity' ? 'relative_humidity_2m' : (id === 'dew' ? 'dew_point_2m' : (id === 'uv' ? 'uv_index' : 'temperature_2m,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m'));
  if (id === 'wind') vars = 'wind_speed_10m,wind_direction_10m,wind_gusts_10m';
  var hour = new Date(state.time * 1000).toISOString().slice(0, 13) + ':00';
  var tail = '&hourly=' + vars + '&start_hour=' + encodeURIComponent(hour) + '&end_hour=' + encodeURIComponent(hour) + '&wind_speed_unit=kmh&timezone=UTC';
  return omFetch(OM, p, tail, 120).then(function (d) {
    var g = {t:[], c:[], p:[], ws:[], wd:[], rh:[], dp:[], uv:[]};
    d.forEach(function (it) {
      var h = (it && it.hourly) || {};
      function first(key) { return val(function () { return h[key][0]; }); }
      g.t.push(first('temperature_2m'));
      g.c.push(first('cloud_cover'));
      g.p.push(first('pressure_msl'));
      g.ws.push(first('wind_speed_10m'));
      g.wd.push(first('wind_direction_10m'));
      g.rh.push(first('relative_humidity_2m'));
      g.dp.push(first('dew_point_2m'));
      g.uv.push(first('uv_index'));
    });
    if (!hasData(g[GRID_KEY[id]])) throw new Error('none');
    return g;
  });
}

function fetchAir(p) {
  var hour = new Date(state.time * 1000).toISOString().slice(0, 13) + ':00';
  return omFetch('https://air-quality-api.open-meteo.com/v1/air-quality', p, '&hourly=us_aqi&start_hour=' + encodeURIComponent(hour) + '&end_hour=' + encodeURIComponent(hour) + '&timezone=UTC', 120).then(function (d) {
    return {aq:d.map(function (it) { return val(function () { return it.hourly.us_aqi[0]; }); })};
  });
}

function fetchSea(p) {
  var hour = new Date().getUTCHours();
  return omFetch('https://marine-api.open-meteo.com/v1/marine', p, '&hourly=sea_level_height_msl&forecast_days=1&timezone=UTC', 12).then(function (d) {
    var sl = d.map(function (it) { return val(function () { return it.hourly.sea_level_height_msl[hour]; }); });
    if (!hasData(sl)) throw new Error('none');
    return {sl:sl};
  });
}

function fetchDrought(p) {
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
  var seq = state.seq, id = state.layer, token = ++gridToken;
  var timed = ['temp','clouds','wind','pressure','dew','uv','aqi'].indexOf(id) >= 0;
  var cacheKey = timed ? id + ':' + Math.floor(state.time / 3600) : id;
  var cached = gridCache[cacheKey];
  if (cached && Date.now() - cached.at < GRID_TTL) {
    grid = cached.g; gridAt = cached.at; gridZoom = map.getZoom();
    state.busy = false; setLegend('');
    if (overlay && overlay.refresh) overlay.refresh();
    return;
  }
  if (gridCtl) gridCtl.abort();
  gridCtl = window.AbortController ? new AbortController() : null;
  gridSignal = gridCtl ? gridCtl.signal : null;
  var p = {lats:[], lons:[]};
  for (var r = 0; r < ROWS; r++) {
    for (var c = 0; c < COLS; c++) {
      p.lats.push((GRID_BOX.s + (GRID_BOX.n - GRID_BOX.s) * r / (ROWS - 1)).toFixed(3));
      p.lons.push(wrapLon(GRID_BOX.w + (GRID_BOX.e - GRID_BOX.w) * c / (COLS - 1)).toFixed(3));
    }
  }
  state.busy = true; setLegend('');
  var job = id === 'aqi' ? fetchAir(p) : (id === 'sea' ? fetchSea(p) : (id === 'drought' ? fetchDrought(p) : fetchWeather(p, id)));
  job.then(function (g) {
    if (seq !== state.seq || token !== gridToken) return;
    g.s = GRID_BOX.s; g.n = GRID_BOX.n; g.w = GRID_BOX.w; g.e = GRID_BOX.e;
    gridCache[cacheKey] = {g:g, at:Date.now()};
    grid = g; gridAt = Date.now(); gridZoom = map.getZoom();
    state.busy = false; setLegend('');
    if (overlay && overlay.refresh) overlay.refresh();
   }).catch(function () {
    if (seq !== state.seq || token !== gridToken) return;
    var fallback = gridCache[cacheKey];
    if (fallback && Date.now() - fallback.at < GRID_TTL * 6) {
      grid = fallback.g;
      gridAt = fallback.at;
      gridZoom = map.getZoom();
      state.busy = false;
      setLegend('');
      if (overlay && overlay.refresh) overlay.refresh();
    } else {
      state.busy = false;
      setLegend(state.err);
    }
  });
}

function sample(arr, lat, lon) {
  var fx = (lon - grid.w) / (grid.e - grid.w) * (COLS - 1);
  var fy = (lat - grid.s) / (grid.n - grid.s) * (ROWS - 1);
  fx = Math.min(COLS - 1.000001, Math.max(0, fx));
  fy = Math.min(ROWS - 1.000001, Math.max(0, fy));
  var x0 = Math.floor(fx), y0 = Math.floor(fy), tx = fx - x0, ty = fy - y0;
  
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
  requestJson('__drought.json', null, 600000).then(function (fc) {
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
  requestJson('__alerts.json', null, 120000).then(function (fc) {
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

var rainMemo = Object.create(null);
function rainColor(r, g, b) {
  var key = (r << 16) | (g << 8) | b;
  if (Object.prototype.hasOwnProperty.call(rainMemo, key)) return rainMemo[key];
  var samples = state.labels.radarSamples || [], best = 2147483647, dbz = null;
  for (var i = 0; i < samples.length; i++) {
    var sample = samples[i], c = sample[1];
    var dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
    var distance = dr * dr + dg * dg + db * db;
    if (distance < best) { best = distance; dbz = sample[0]; }
  }
  if (dbz === null || best > 900) { rainMemo[key] = null; return null; }
  var stops = state.labels.radarStops || [[15,57330],[35,2653183],[45,8803800],[55,15555503],[65,16777215]], color;
  if (dbz < stops[0][0]) { rainMemo[key] = false; return false; }
  if (dbz <= stops[0][0]) color = stops[0][1];
  else if (dbz >= stops[stops.length - 1][0]) color = stops[stops.length - 1][1];
  else {
    for (var j = 1; j < stops.length; j++) {
      if (dbz <= stops[j][0]) {
        var lo = stops[j - 1], hi = stops[j], t = (dbz - lo[0]) / (hi[0] - lo[0]);
        var lr = (lo[1] >> 16) & 255, lg = (lo[1] >> 8) & 255, lb = lo[1] & 255;
        var hr = (hi[1] >> 16) & 255, hg = (hi[1] >> 8) & 255, hb = hi[1] & 255;
        color = (Math.round(lr + (hr - lr) * t) << 16) | (Math.round(lg + (hg - lg) * t) << 8) | Math.round(lb + (hb - lb) * t);
        break;
      }
    }
  }
  var out = [(color >> 16) & 255, (color >> 8) & 255, color & 255];
  rainMemo[key] = out;
  return out;
}

function rainTileLayer(host, path) {
  var layer = L.gridLayer({tileSize:256, maxNativeZoom:7, maxZoom:12, opacity:0.9, updateWhenIdle:true, updateWhenZooming:false, keepBuffer:2});
  layer.createTile = function (coords, done) {
    var canvas = L.DomUtil.create('canvas', 'leaflet-tile'), context = canvas.getContext('2d', {willReadFrequently:true});
    canvas.width = 256; canvas.height = 256;
    var image = new Image(); image.crossOrigin = 'anonymous';
    image.onload = function () {
      try {
        context.drawImage(image, 0, 0, 256, 256);
        var imageData = context.getImageData(0, 0, 256, 256), pixels = imageData.data;
        for (var i = 0; i < pixels.length; i += 4) {
          if (pixels[i + 3] < 16) continue;
          var color = rainColor(pixels[i], pixels[i + 1], pixels[i + 2]);
          if (color === false) pixels[i + 3] = 0;
          else if (color) { pixels[i] = color[0]; pixels[i + 1] = color[1]; pixels[i + 2] = color[2]; }
        }
        context.putImageData(imageData, 0, 0);
        done(null, canvas);
      } catch (error) { done(error, canvas); }
    };
    image.onerror = function () { done(new Error('radar_tile_unavailable'), canvas); };
    var n = 1 << coords.z, x = ((coords.x % n) + n) % n;
    image.src = host + path + '/256/' + coords.z + '/' + x + '/' + coords.y + '/2/0_0.png';
    return canvas;
  };
  return layer;
}

function setTime(epochMillis) {
  var next = Number(epochMillis) / 1000;
  if (!isFinite(next)) return;
  if (Math.abs(next - state.time) < 0.001 && !mapTimeTimer) return;
  state.time = next;
  if (mapTimeTimer) clearTimeout(mapTimeTimer);
  mapTimeTimer = setTimeout(function () {
    mapTimeTimer = null;
    if (state.layer === 'precip') startLayer(state.layer);
    else if (isGrid(state.layer) && state.layer !== 'sea' && state.layer !== 'drought') loadGrid(true);
  }, 220);
}

function fmtDelta(c) { return state.f ? Math.round(c * 9 / 5) + '°F' : Math.round(c) + '°C'; }

function frontGroups(cells) {
  var index = {}, seen = {}, groups = [];
  cells.forEach(function (c) { index[c.r + ':' + c.c] = c; });
  cells.forEach(function (c) {
    var key = c.r + ':' + c.c;
    if (seen[key]) return;
    seen[key] = true;
    var stack = [c], comp = [];
    while (stack.length) {
      var cur = stack.pop();
      comp.push(cur);
      for (var dr = -1; dr <= 1; dr++) {
        for (var dc = -1; dc <= 1; dc++) {
          var k = (cur.r + dr) + ':' + (cur.c + dc), nb = index[k];
          if (nb && !seen[k] && Math.abs(nb.t - cur.t) <= 18) { seen[k] = true; stack.push(nb); }
        }
      }
    }
    groups.push(comp);
  });
  return groups;
}

function frontTrack() {
  if (!grid || !grid.cp) return [];
  if (grid.tracks) return grid.tracks;
  var cells = [];
  for (var r = 0; r < ROWS; r++) {
    for (var c = 0; c < COLS; c++) {
      var v = grid.cp[r * COLS + c];
      if (v === null || v === undefined || v >= FRONT.none) continue;
      cells.push({r:r, c:c, t:v, lat:grid.s + (grid.n - grid.s) * r / (ROWS - 1), lon:grid.w + (grid.e - grid.w) * c / (COLS - 1)});
    }
  }
  var tracks = frontGroups(cells).filter(function (g) { return g.length >= 6; })
    .sort(function (a, b) { return b.length - a.length; }).slice(0, 4)
    .map(function (g) {
      g.sort(function (a, b) { return a.t - b.t; });
      var bins = Math.max(3, Math.min(8, Math.floor(g.length / 3))), pts = [];
      for (var i = 0; i < bins; i++) {
        var part = g.slice(Math.floor(i * g.length / bins), Math.floor((i + 1) * g.length / bins)), lat = 0, lon = 0, t = 0;
        if (!part.length) continue;
        part.forEach(function (k) { lat += k.lat; lon += k.lon; t += k.t; });
        pts.push({t:t / part.length, lat:lat / part.length, lon:lon / part.length});
      }
      return pts;
    }).filter(function (pts) { return pts.length >= 3; });
  grid.tracks = tracks;
  return tracks;
}


function smoothPath(pts) {
  var out = pts;
  for (var pass = 0; pass < 3; pass++) {
    if (out.length < 3) return out;
    var next = [out[0]];
    for (var i = 0; i < out.length - 1; i++) {
      var p = out[i], q = out[i + 1];
      next.push([p[0] * 0.75 + q[0] * 0.25, p[1] * 0.75 + q[1] * 0.25]);
      next.push([p[0] * 0.25 + q[0] * 0.75, p[1] * 0.25 + q[1] * 0.75]);
    }
    next.push(out[out.length - 1]);
    out = next;
  }
  return out;
}

function strokePath(ctx, pts, color, halo, width) {
  if (pts.length < 2) return;
  [[halo, width + 3], [color, width]].forEach(function (pass) {
    ctx.beginPath();
    ctx.moveTo(pts[0][0], pts[0][1]);
    for (var i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
    ctx.strokeStyle = pass[0];
    ctx.lineWidth = pass[1];
    ctx.stroke();
  });
}

function stormRadii(kt) {
  return {outer:110 + 2.2 * kt, inner:kt >= 64 ? 30 + 0.9 * kt : 0};
}

function paintStorms(ctx, m, size, zones) {
  if (!zoneCanvas) zoneCanvas = document.createElement('canvas');
  var pad = size.pad || 0, zw = size.x + pad * 2, zh = size.y + pad * 2;
  zoneCanvas.width = zw; zoneCanvas.height = zh;
  var zctx = zoneCanvas.getContext('2d'), scale = 156543.03392 / Math.pow(2, m.getZoom());
  var passes = [
    {now:false, inner:false, color:'#8a8f99', alpha:0.24},
    {now:false, inner:true, color:'#6b7079', alpha:0.26},
    {now:true, inner:false, color:'#2979ff', alpha:0.28},
    {now:true, inner:true, color:'#0d47a1', alpha:0.34}
  ];
  passes.forEach(function (ps) {
    zctx.setTransform(1, 0, 0, 1, 0, 0);
    zctx.clearRect(0, 0, zw, zh);
    zctx.translate(pad, pad);
    zctx.fillStyle = ps.color;
    var any = false;
    zones.forEach(function (z) {
      if (z.now !== ps.now) return;
      var rad = stormRadii(z.kt), km = ps.inner ? rad.inner : rad.outer;
      if (km <= 0) return;
      var px = km * 1000 / (scale * Math.cos(z.lat * Math.PI / 180));
      [-360, 0, 360].forEach(function (k) {
        var p = m.latLngToContainerPoint([z.lat, z.lon + k]);
        if (p.x + px < -pad || p.y + px < -pad || p.x - px > size.x + pad || p.y - px > size.y + pad) return;
        zctx.beginPath();
        zctx.arc(p.x, p.y, px, 0, Math.PI * 2);
        zctx.fill();
        any = true;
      });
    });
    if (!any) return;
    ctx.globalAlpha = ps.alpha;
    ctx.drawImage(zoneCanvas, -pad, -pad);
    ctx.globalAlpha = 1;
  });
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
  requestJson(q, null, 30000).then(function (d) {
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
  var cells = [];
  var redo = function () { renderFires(cells, group); };
  map.on('moveend zoomend', redo);
  off = function () { map.off('moveend zoomend', redo); };
  var fresh = cache.fires && Date.now() - cache.firesAt < 600000;
  var p = fresh ? Promise.resolve(cache.fires) : requestJson('__fires.json', null, 600000).catch(function () {
    return getJson(EONET + 'wildfires').then(function (d) { return parseFires(d.events || []); });
  }).then(function (c) {
    cache.fires = c; cache.firesAt = Date.now();
    return c;
  });
  p.then(function (c) {
    if (seq !== state.seq) return;
    cells = c;
    redo();
    state.busy = false; setLegend('');
  }).catch(function () {
    if (seq === state.seq) { state.busy = false; setLegend(''); }
  });
}
""".trimIndent()
}
