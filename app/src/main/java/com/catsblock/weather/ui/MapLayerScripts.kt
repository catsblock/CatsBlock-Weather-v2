package com.catsblock.weather.ui

internal object MapLayerScripts {

    val source: String = """
var extra = null, land = null, landLoading = false, landWaiters = [], boSeq = 0, boTimer = null;
var OM = 'https://api.open-meteo.com/v1/forecast';
var GRID_KEY = {temp:'t', clouds:'c', wind:'ws', pressure:'p', dew:'dp', uv:'uv', aqi:'aq', sea:'sl', drought:'dr'};
var LEVEL_COLORS = {warning:'#ffd600', alert:'#ff6d00', danger:'#e53935'};
var LEVEL_RANK = {warning:1, alert:2, danger:3};
var BLACKOUT_COLORS = {minor:'#2979ff', moderate:'#ffd600', severe:'#e53935'};
var DRY = [[255, 236, 140], [255, 200, 60], [245, 140, 30], [216, 67, 21], [150, 20, 20]];
var LAND_URLS = [
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_land.geojson',
  'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_land.geojson'
];
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

function fetchWeather(p, id) {
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
  return omFetch('https://air-quality-api.open-meteo.com/v1/air-quality', p, '&current=us_aqi&timezone=UTC', 120).then(function (d) {
    return {aq:d.map(function (it) { return val(function () { return it.current.us_aqi; }); })};
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
  fetch('__drought.json').then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (fc) {
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
  fetch('__alerts.json').then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (fc) {
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

function loadBlackouts(seq) {
  var group = L.layerGroup();
  overlay = group.addTo(map);
  var run = function () {
    if (seq !== state.seq) return;
    if (map.getZoom() < 5) {
      group.clearLayers(); state.busy = false;
      setLegend(lbl('zoomIn', 'Zoom in to see outages'));
      return;
    }
    var b = map.getBounds(), my = ++boSeq;
    var url = '__blackouts.json?s=' + b.getSouth().toFixed(3) + '&n=' + b.getNorth().toFixed(3) + '&w=' + b.getWest().toFixed(3) + '&e=' + b.getEast().toFixed(3);
    state.busy = true; setLegend('');
    fetch(url).then(function (r) { if (!r.ok) throw new Error('http'); return r.json(); }).then(function (fc) {
      if (seq !== state.seq || my !== boSeq) return;
      group.clearLayers();
      var feats = fc.features || [];
      feats.forEach(function (f) {
        var p = f.properties, c = BLACKOUT_COLORS[p.level] || BLACKOUT_COLORS.minor;
        var shape = L.geoJSON(f, {pane:'zonepane', style:{color:c, weight:2, opacity:0.95, fillColor:c, fillOpacity:0.4}});
        shape.on('click', function (ev) {
          L.DomEvent.stopPropagation(ev);
          var d = JSON.parse(JSON.stringify(p));
          d.lat = ev.latlng.lat; d.lon = wrapLon(ev.latlng.lng);
          openDetail('blackout', d);
        });
        group.addLayer(shape);
      });
      state.busy = false;
      setLegend(feats.length ? '' : lbl('noOutages', 'No outages detected right now'));
    }).catch(function () { if (seq === state.seq && my === boSeq) { state.busy = false; setLegend(state.err); } });
  };
  var later = function () { clearTimeout(boTimer); boTimer = setTimeout(run, 900); };
  map.on('moveend', later);
  off = function () { map.off('moveend', later); clearTimeout(boTimer); };
  run();
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
    var fN = seasonFactor(new Date()), fS = 1 - fN;
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
""".trimIndent()
}
