(function () {
  "use strict";
  var bridge = window.CatsBlockBridge;
  var status = { page: false, ready: false, error: "", layer: "ready" };
  window.CBStatus = function () { return JSON.stringify(status); };

  function send(name) {
    try {
      bridge[name].apply(bridge, Array.prototype.slice.call(arguments, 1));
    } catch (e) {}
  }

  function loadScript(urls, index, ok, bad) {
    var el = document.createElement("script");
    el.src = urls[index];
    el.onload = ok;
    el.onerror = function () {
      if (index + 1 < urls.length) loadScript(urls, index + 1, ok, bad);
      else bad();
    };
    document.head.appendChild(el);
  }

  function boot() {
  var ROWS = 16, COLS = 37, CELL = 4;
  var BASES = [
    "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
  ];
  var VARIABLE = { temp: "temperature_2m", clouds: "cloud_cover", wind: "wind_speed_10m", pressure: "pressure_msl", humidity: "relative_humidity_2m", dew: "dew_point_2m", uv: "uv_index", aqi: "us_aqi", sea: "sea_level_height_msl" };
  var STOPS = {
    temp: [[-30, "#7A30B0"], [-15, "#3056E2"], [0, "#22C4E2"], [10, "#48C668"], [20, "#FADE34"], [30, "#FA8C1C"], [40, "#E23428"], [50, "#96183C"]],
    dew: [[-20, "#6E3CAA"], [-5, "#3C64D2"], [5, "#3CBED2"], [12, "#5AC86E"], [18, "#F0DC46"], [22, "#FA9628"], [26, "#E63C32"], [30, "#96143C"]],
    wind: [[0, "#2A6FDB"], [15, "#26C6DA"], [30, "#66BB6A"], [50, "#FDD835"], [75, "#FB8C00"], [100, "#D32F2F"], [140, "#8E24AA"]],
    pressure: [[980, "#7B3294"], [995, "#3B6FD4"], [1005, "#4FC3F7"], [1013, "#E0E0E0"], [1022, "#FFB74D"], [1030, "#F4511E"], [1045, "#B71C1C"]],
    humidity: [[0, "#EBF5FF"], [20, "#80CBC4"], [40, "#26A69A"], [60, "#1E88E5"], [80, "#1565C0"], [100, "#0D47A1"]],
    uv: [[0, "#43A047"], [3, "#FADC34"], [6, "#FB8C00"], [8, "#E53935"], [11, "#8E24AA"], [14, "#5A1482"]],
    aqi: [[0, "#00E400"], [50, "#C8DC00"], [100, "#FF7E00"], [150, "#FF0000"], [200, "#99004C"], [300, "#7E0023"]],
    sea: [[-2, "#2166AC"], [-1, "#67A9CF"], [0, "#F7F7F7"], [1, "#FDAE61"], [2, "#D73027"]]
  };
  var RANGE = {};
  var LUT = {};

  function hexToRgb(hex) {
    return [parseInt(hex.substr(1, 2), 16), parseInt(hex.substr(3, 2), 16), parseInt(hex.substr(5, 2), 16)];
  }

  function buildLut(layer) {
    var stops = STOPS[layer];
    var lo = stops[0][0], hi = stops[stops.length - 1][0];
    RANGE[layer] = [lo, hi];
    var table = new Uint8ClampedArray(256 * 4);
    for (var i = 0; i < 256; i++) {
      var v = lo + (hi - lo) * i / 255;
      var k = 1;
      while (k < stops.length - 1 && stops[k][0] < v) k++;
      var a = stops[k - 1], b = stops[k];
      var t = b[0] === a[0] ? 0 : Math.min(1, Math.max(0, (v - a[0]) / (b[0] - a[0])));
      var ca = hexToRgb(a[1]), cb = hexToRgb(b[1]);
      table[i * 4] = ca[0] + (cb[0] - ca[0]) * t;
      table[i * 4 + 1] = ca[1] + (cb[1] - ca[1]) * t;
      table[i * 4 + 2] = ca[2] + (cb[2] - ca[2]) * t;
      table[i * 4 + 3] = layer === "sea" ? 210 : 184;
    }
    LUT[layer] = table;
  }
  Object.keys(STOPS).forEach(buildLut);

  var map = L.map("map", {
    zoomControl: false,
    attributionControl: false,
    minZoom: 2,
    maxZoom: 18,
    worldCopyJump: true,
    preferCanvas: true,
    zoomSnap: 0.25,
    zoomDelta: 0.5,
    fadeAnimation: true,
    markerZoomAnimation: true,
    inertia: true
  }).setView([0, 0], 2);

  map.createPane("basePane");
  map.getPane("basePane").classList.add("base-pane");
  map.getPane("basePane").style.zIndex = 200;
  map.createPane("radarPane");
  map.getPane("radarPane").style.zIndex = 300;
  map.getPane("radarPane").style.pointerEvents = "none";
  map.createPane("featurePane");
  map.getPane("featurePane").style.zIndex = 400;

  var active = true;
  var baseIndex = 0;
  var baseLayer = null;
  var baseReady = false;
  var baseErrors = 0;
  var baseLoaded = 0;
  var baseTimer = null;
  var currentLayer = "none";
  var layerToken = 0;
  var selectedTime = Date.now();
  var timeTimer = null;
  var radarLayer = null;
  var radarTimer = null;
  var sstLayer = null;
  var featureGroup = null;
  var cityMarker = null;
  var gridCanvas = document.getElementById("grid");
  var gridCtx = gridCanvas.getContext("2d");
  var gridData = null;
  var gridKey = "";
  var gridCache = {};
  var drawQueued = false;
  var activeControllers = [];

  function rememberGrid(key, values) {
    gridCache[key] = { at: Date.now(), values: values };
    var keys = Object.keys(gridCache);
    if (keys.length > 32) delete gridCache[keys[0]];
  }

  function mountBase(index) {
    if (baseLayer) { map.removeLayer(baseLayer); baseLayer = null; }
    if (baseTimer) { clearTimeout(baseTimer); baseTimer = null; }
    baseIndex = index;
    baseErrors = 0;
    baseLoaded = 0;
    baseLayer = L.tileLayer(BASES[index], {
      pane: "basePane",
      maxNativeZoom: 19,
      maxZoom: 18,
      tileSize: 256,
      updateWhenIdle: false,
      updateWhenZooming: false,
      keepBuffer: 3,
      crossOrigin: false
    });
    baseLayer.on("tileload", function () {
      baseLoaded++;
      if (!baseReady) { baseReady = true; status.ready = true; }
    });
    baseLayer.on("tileerror", function () {
      baseErrors++;
      if (baseLoaded === 0 && baseErrors >= 4) failBase();
    });
    baseLayer.addTo(map);
    baseTimer = setTimeout(function () {
      if (baseLoaded === 0) failBase();
    }, 7000);
  }

  function failBase() {
    if (baseLoaded > 0) return;
    if (baseIndex + 1 < BASES.length) {
      mountBase(baseIndex + 1);
    } else {
      status.error = "tiles";
    }
  }

  mountBase(0);

  function setLayerState(state) {
    status.layer = state;
  }

  function clearRadar() {
    if (radarTimer) { clearInterval(radarTimer); radarTimer = null; }
    if (radarLayer) { map.removeLayer(radarLayer); radarLayer = null; }
  }

  function clearSst() {
    if (sstLayer) { map.removeLayer(sstLayer); sstLayer = null; }
  }

  function clearFeatures() {
    if (featureGroup) { map.removeLayer(featureGroup); featureGroup = null; }
  }

  function clearGrid() {
    gridData = null;
    gridKey = "";
    gridCanvas.classList.remove("visible");
    gridCtx.clearRect(0, 0, gridCanvas.width, gridCanvas.height);
  }

  function resetLayers() {
    layerToken++;
    if (timeTimer) { clearTimeout(timeTimer); timeTimer = null; }
    activeControllers.splice(0).forEach(function (controller) { controller.abort(); });
    clearRadar();
    clearSst();
    clearFeatures();
    clearGrid();
  }

  function fetchJson(url, token) {
    var controller = typeof AbortController === "function" ? new AbortController() : null;
    if (controller) activeControllers.push(controller);
    var timer = setTimeout(function () { if (controller) controller.abort(); }, 20000);
    return fetch(url, controller ? { signal: controller.signal } : {}).then(function (response) {
      clearTimeout(timer);
      if (!response.ok) throw new Error("http " + response.status);
      return response.json();
    }).then(function (json) {
      clearTimeout(timer);
      if (controller) {
        var completed = activeControllers.indexOf(controller);
        if (completed >= 0) activeControllers.splice(completed, 1);
      }
      if (token !== layerToken) throw new Error("stale");
      return json;
    }, function (err) {
      clearTimeout(timer);
      if (controller) {
        var failed = activeControllers.indexOf(controller);
        if (failed >= 0) activeControllers.splice(failed, 1);
      }
      if (token !== layerToken) throw new Error("stale");
      throw err;
    });
  }

  function showRadar(token, epochMillis) {
    setLayerState("loading");
    function load() {
      fetchJson("https://api.rainviewer.com/public/weather-maps.json", token).then(function (data) {
        var past = data && data.radar && data.radar.past;
        if (!data || typeof data.host !== "string" || !past || !past.length) throw new Error("no frames");
        var frames = past.concat(data.radar.nowcast || []);
        var target = Math.floor(epochMillis / 1000);
        var frame = frames.reduce(function (best, item) {
          return !best || Math.abs(item.time - target) < Math.abs(best.time - target) ? item : best;
        }, null);
        if (typeof frame.path !== "string") throw new Error("bad frame");
        var url = data.host + frame.path + "/256/{z}/{x}/{y}/2/1_1.png";
        var next = L.tileLayer(url, {
          pane: "radarPane",
          maxNativeZoom: 7,
          maxZoom: 18,
          tileSize: 256,
          opacity: 0,
          className: "overlay-tiles",
          updateWhenIdle: false,
          keepBuffer: 2
        });
        var errors = 0;
        var done = false;
        next.on("load", function () {
          if (done || token !== layerToken) return;
          done = true;
          next.setOpacity(0.82);
          if (radarLayer && radarLayer !== next) {
            var old = radarLayer;
            old.setOpacity(0);
            setTimeout(function () { map.removeLayer(old); }, 360);
          }
          radarLayer = next;
          setLayerState("ready");
        });
        next.on("tileerror", function () {
          errors++;
          if (!done && errors >= 6 && token === layerToken) setLayerState("error");
        });
        next.addTo(map);
        if (!radarLayer) radarLayer = next;
      }).catch(function (err) {
        if (err && err.message === "stale") return;
        if (token === layerToken) setLayerState("error");
      });
    }
    load();
    radarTimer = setInterval(function () { if (active && token === layerToken) load(); }, 300000);
  }

  function showSst(token) {
    var d = new Date(Date.now() - 3 * 86400000);
    var date = d.toISOString().slice(0, 10);
    var name = "GHRSST_L4_MUR_Sea_Surface_Temperature_Anomalies_v4.1_STD";
    var url = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/all/" + name + "/default/" + date + "/GoogleMapsCompatible_Level7/{z}/{y}/{x}.png";
    setLayerState("loading");
    var errors = 0;
    var done = false;
    var layer = L.tileLayer(url, { pane: "radarPane", maxNativeZoom: 7, maxZoom: 18, opacity: 0.78, tileSize: 256 });
    layer.on("load", function () { if (!done && token === layerToken) { done = true; setLayerState("ready"); } });
    layer.on("tileerror", function () { errors++; if (!done && errors >= 6 && token === layerToken) setLayerState("error"); });
    layer.addTo(map);
    sstLayer = layer;
  }

  function showCyclones(token) {
    setLayerState("loading");
    fetchJson("https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=500&category=severeStorms", token).then(function (data) {
      var group = L.layerGroup([], { pane: "featurePane" });
      (data.events || []).forEach(function (event) {
        (event.geometry || []).forEach(function (g) {
          var c = g && g.coordinates;
          if (!c || c.length < 2 || !isFinite(c[0]) || !isFinite(c[1])) return;
          L.circleMarker([c[1], c[0]], { pane: "featurePane", radius: 6, color: "#ffffff", weight: 1, fillColor: "#1976d2", fillOpacity: 0.95 }).addTo(group);
        });
      });
      if (token !== layerToken) return;
      featureGroup = group.addTo(map);
      setLayerState("ready");
    }).catch(function (err) {
      if (err && err.message === "stale") return;
      if (token === layerToken) setLayerState("error");
    });
  }

  function gridCoordinates() {
    var points = [];
    for (var r = 0; r < ROWS; r++) for (var c = 0; c < COLS; c++) {
      points.push([-75 + 150 * r / (ROWS - 1), -180 + 360 * c / (COLS - 1)]);
    }
    return points;
  }

  function hourString(epochMillis) {
    return new Date(epochMillis).toISOString().slice(0, 13) + ":00";
  }

  function fetchGrid(layer, token, epochMillis) {
    var hour = hourString(epochMillis);
    var cacheKey = layer + ":" + hour;
    var cached = gridCache[cacheKey];
    if (cached && Date.now() - cached.at < 1800000) return Promise.resolve(cached.values);
    var points = gridCoordinates();
    var chunk = layer === "sea" ? 12 : 120;
    var base = layer === "aqi" ? "https://air-quality-api.open-meteo.com/v1/air-quality" : layer === "sea" ? "https://marine-api.open-meteo.com/v1/marine" : "https://api.open-meteo.com/v1/forecast";
    var jobs = [];
    for (var offset = 0; offset < points.length; offset += chunk) {
      jobs.push(offset);
    }
    var values = new Array(points.length);
    for (var i = 0; i < values.length; i++) values[i] = NaN;
    var index = 0;
    function worker() {
      if (index >= jobs.length) return Promise.resolve();
      var start = jobs[index++];
      var slice = points.slice(start, start + chunk);
      var lat = slice.map(function (p) { return p[0].toFixed(3); }).join(",");
      var lon = slice.map(function (p) { return p[1].toFixed(3); }).join(",");
      var query = "latitude=" + lat + "&longitude=" + lon + "&hourly=" + VARIABLE[layer] + "&timezone=UTC";
      query += "&start_hour=" + hour + "&end_hour=" + hour;
      if (layer === "wind") query += "&wind_speed_unit=kmh";
      return fetchWithRetry(base + "?" + query, token).then(function (json) {
        var list = Array.isArray(json) ? json : [json];
        for (var j = 0; j < slice.length; j++) {
          var item = list[j];
          var h = item && item.hourly;
          var arr = h && h[VARIABLE[layer]];
          if (!arr) continue;
          var at = 0;
          if (layer === "sea" && h.time) {
            at = h.time.indexOf(hour);
            if (at < 0) continue;
          }
          var v = arr[at];
          if (typeof v === "number" && isFinite(v)) values[start + j] = v;
        }
        return worker();
      });
    }
    var workers = [worker(), worker(), worker()];
    return Promise.all(workers).then(function () {
      var any = values.some(function (v) { return isFinite(v); });
      if (!any) throw new Error("empty");
      rememberGrid(cacheKey, values);
      return values;
    });
  }

  function fetchWithRetry(url, token) {
    var attempt = 0;
    function run() {
      return fetchJson(url, token).catch(function (err) {
        if (err && err.message === "stale") throw err;
        attempt++;
        if (attempt >= 3) throw err;
        return new Promise(function (resolve) { setTimeout(resolve, 450 * attempt); }).then(run);
      });
    }
    return run();
  }

  function showGrid(layer, token, epochMillis) {
    setLayerState("loading");
    fetchGrid(layer, token, epochMillis).then(function (values) {
      if (token !== layerToken) return;
      gridData = { layer: layer, values: values };
      gridCanvas.classList.add("visible");
      queueDraw();
      setLayerState("ready");
    }).catch(function (err) {
      if (err && err.message === "stale") return;
      if (token === layerToken) setLayerState("error");
    });
  }

  function fetchColdFrontValues(token, epochMillis) {
    var hour = hourString(epochMillis);
    var endHour = hourString(epochMillis + 6 * 60 * 60 * 1000);
    var cacheKey = "coldfront:" + hour;
    var cached = gridCache[cacheKey];
    if (cached && Date.now() - cached.at < 1800000) return Promise.resolve(cached.values);
    var points = gridCoordinates();
    var chunk = 120;
    var starts = [];
    for (var offset = 0; offset < points.length; offset += chunk) starts.push(offset);
    var values = new Array(points.length);
    for (var i = 0; i < values.length; i++) values[i] = NaN;
    var next = 0;
    function worker() {
      if (next >= starts.length) return Promise.resolve();
      var start = starts[next++];
      var slice = points.slice(start, start + chunk);
      var lat = slice.map(function (p) { return p[0].toFixed(3); }).join(",");
      var lon = slice.map(function (p) { return p[1].toFixed(3); }).join(",");
      var url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
        "&hourly=temperature_2m&start_hour=" + hour + "&end_hour=" + endHour + "&timezone=UTC";
      return fetchWithRetry(url, token).then(function (json) {
        var list = Array.isArray(json) ? json : [json];
        for (var j = 0; j < slice.length; j++) {
          var hourly = list[j] && list[j].hourly;
          var temperatures = hourly && hourly.temperature_2m;
          if (!temperatures || temperatures.length < 2) continue;
          var first = temperatures[0], last = temperatures[temperatures.length - 1];
          if (typeof first === "number" && isFinite(first) && typeof last === "number" && isFinite(last)) {
            values[start + j] = first - last;
          }
        }
        return worker();
      });
    }
    return Promise.all([worker(), worker(), worker()]).then(function () {
      if (!values.some(function (value) { return isFinite(value); })) throw new Error("empty");
      rememberGrid(cacheKey, values);
      return values;
    });
  }

  function showColdFronts(token, epochMillis) {
    setLayerState("loading");
    fetchColdFrontValues(token, epochMillis).then(function (values) {
      if (token !== layerToken) return;
      var points = gridCoordinates();
      var threshold = 3.5;
      var segments = [];
      for (var row = 0; row < ROWS - 1; row++) {
        for (var col = 0; col < COLS - 1; col++) {
          var indices = [row * COLS + col, row * COLS + col + 1, (row + 1) * COLS + col + 1, (row + 1) * COLS + col];
          var corners = indices.map(function (index) { return points[index]; });
          var field = indices.map(function (index) { return values[index]; });
          if (field.some(function (value) { return !isFinite(value); })) continue;
          var edges = [[0, 1], [1, 2], [2, 3], [3, 0]];
          var crossings = [];
          edges.forEach(function (edge, edgeIndex) {
            var a = field[edge[0]], b = field[edge[1]];
            if ((a < threshold) === (b < threshold)) return;
            var fraction = (threshold - a) / (b - a);
            var first = corners[edge[0]], last = corners[edge[1]];
            crossings.push({ edge: edgeIndex, point: [first[0] + (last[0] - first[0]) * fraction, first[1] + (last[1] - first[1]) * fraction] });
          });
          if (crossings.length === 2) segments.push([crossings[0].point, crossings[1].point]);
          else if (crossings.length === 4) {
            var center = (field[0] + field[1] + field[2] + field[3]) / 4;
            var pairs = center >= threshold ? [[0, 3], [1, 2]] : [[0, 1], [2, 3]];
            pairs.forEach(function (pair) { segments.push([crossings[pair[0]].point, crossings[pair[1]].point]); });
          }
        }
      }
      featureGroup = segments.length
        ? L.polyline(segments, { pane: "featurePane", color: "#1e88e5", weight: 2.5, opacity: 0.92, lineCap: "round", lineJoin: "round", smoothFactor: 1.5 }).addTo(map)
        : L.layerGroup().addTo(map);
      setLayerState("ready");
    }).catch(function (err) {
      if (err && err.message === "stale") return;
      if (token === layerToken) setLayerState("error");
    });
  }

  function sample(values, lat, lon) {
    var fr = (lat + 75) / 150 * (ROWS - 1);
    if (fr < 0 || fr > ROWS - 1) return NaN;
    var lonWrapped = ((lon + 180) % 360 + 360) % 360;
    var fc = lonWrapped / 360 * (COLS - 1);
    var r0 = Math.floor(fr), c0 = Math.floor(fc);
    var r1 = Math.min(ROWS - 1, r0 + 1), c1 = Math.min(COLS - 1, c0 + 1);
    var tr = fr - r0, tc = fc - c0;
    var a = values[r0 * COLS + c0], b = values[r0 * COLS + c1], c = values[r1 * COLS + c0], d = values[r1 * COLS + c1];
    var sum = 0, weight = 0;
    var w;
    if (isFinite(a)) { w = (1 - tr) * (1 - tc); sum += a * w; weight += w; }
    if (isFinite(b)) { w = (1 - tr) * tc; sum += b * w; weight += w; }
    if (isFinite(c)) { w = tr * (1 - tc); sum += c * w; weight += w; }
    if (isFinite(d)) { w = tr * tc; sum += d * w; weight += w; }
    return weight > 0 ? sum / weight : NaN;
  }

  function queueDraw() {
    if (drawQueued || !gridData || !active) return;
    drawQueued = true;
    requestAnimationFrame(function () { drawQueued = false; drawGrid(); });
  }

  function drawGrid() {
    if (!gridData) return;
    var size = map.getSize();
    var w = Math.max(1, Math.ceil(size.x / CELL)), h = Math.max(1, Math.ceil(size.y / CELL));
    if (gridCanvas.width !== w || gridCanvas.height !== h) { gridCanvas.width = w; gridCanvas.height = h; }
    var layer = gridData.layer;
    var image = gridCtx.createImageData(w, h);
    var data = image.data;
    var lut = LUT[layer];
    var lo = RANGE[layer][0], hi = RANGE[layer][1];
    var isClouds = layer === "clouds";
    var cloudLut = null;
    for (var y = 0; y < h; y++) {
      var latLng = map.containerPointToLatLng([0, y * CELL + CELL / 2]);
      var lat = latLng.lat;
      for (var x = 0; x < w; x++) {
        var lon = map.containerPointToLatLng([x * CELL + CELL / 2, y * CELL + CELL / 2]).lng;
        var v = sample(gridData.values, lat, lon);
        if (!isFinite(v)) continue;
        var o = (y * w + x) * 4;
        if (isClouds) {
          data[o] = 230; data[o + 1] = 242; data[o + 2] = 252;
          data[o + 3] = Math.min(0.85, Math.max(0, v / 100)) * 255;
        } else {
          if (layer === "uv" && v < 0.3) continue;
          var t = Math.round((Math.min(hi, Math.max(lo, v)) - lo) / (hi - lo) * 255) * 4;
          data[o] = lut[t]; data[o + 1] = lut[t + 1]; data[o + 2] = lut[t + 2]; data[o + 3] = lut[t + 3];
        }
      }
    }
    gridCtx.putImageData(image, 0, 0);
  }

  function syncSize() {
    map.invalidateSize({ animate: false });
    queueDraw();
  }
  if (window.ResizeObserver) new ResizeObserver(syncSize).observe(document.getElementById("map"));
  else window.addEventListener("resize", syncSize);
  [0, 250, 800, 2000].forEach(function (delay) { setTimeout(syncSize, delay); });

  map.on("move zoom", queueDraw);
  map.on("resize", queueDraw);

  function parseFeatures(json) {
    try { return JSON.parse(json); } catch (e) { return null; }
  }

  function showFeatures(layer, json) {
    if (layer !== currentLayer) return;
    var data = parseFeatures(json);
    clearFeatures();
    if (!data || !data.features) { setLayerState("error"); return; }
    var style;
    if (layer === "alerts") {
      var colors = { danger: "#d32f2f", alert: "#f57c00" };
      style = function (f) {
        var c = colors[f.properties && f.properties.level] || "#fbc02d";
        return { color: c, weight: 1.5, fillColor: c, fillOpacity: 0.28 };
      };
    } else if (layer === "drought") {
      var palette = { 1: "#fff176", 2: "#ffb74d", 3: "#ef6c00", 4: "#c62828" };
      style = function (f) {
        var c = palette[f.properties && f.properties.lvl] || "#7f0000";
        return { stroke: false, fillColor: c, fillOpacity: 0.45 };
      };
    }
    featureGroup = L.geoJSON(data, {
      pane: "featurePane",
      style: style,
      pointToLayer: function (f, latlng) {
        var radius = Math.min(10, Math.max(3, Number(f.properties && f.properties.radius) || 4));
        return L.circleMarker(latlng, { pane: "featurePane", radius: radius, color: "#ffffff", weight: 1, fillColor: "#ff5722", fillOpacity: 0.9 });
      }
    }).addTo(map);
    setLayerState("ready");
  }

  function applyLayer(layer) {
    resetLayers();
    currentLayer = layer;
    var token = layerToken;
    if (layer === "none") { setLayerState("ready"); return; }
    if (layer === "precip") showRadar(token, selectedTime);
    else if (layer === "sst") showSst(token);
    else if (layer === "cyclones") showCyclones(token);
    else if (layer === "coldfront") showColdFronts(token, selectedTime);
    else if (layer === "fires" || layer === "alerts" || layer === "drought") setLayerState("loading");
    else if (VARIABLE[layer]) showGrid(layer, token, selectedTime);
    else setLayerState("ready");
  }

  map.on("click", function (event) {
    var p = event.containerPoint;
    var ll = event.latlng.wrap();
    send("onPoint", ll.lat, ll.lng, p.x, p.y);
  });

  window.CBMap = {
    setLayer: function (layer) {
      if (typeof layer !== "string" || layer === currentLayer) return;
      applyLayer(layer);
    },
    setTime: function (epochMillis) {
      if (typeof epochMillis !== "number" || !isFinite(epochMillis) || epochMillis < 0) return;
      if (Math.abs(selectedTime - epochMillis) < 1000) return;
      selectedTime = epochMillis;
      if (currentLayer === "none") return;
      if (timeTimer) clearTimeout(timeTimer);
      timeTimer = setTimeout(function () {
        timeTimer = null;
        if (currentLayer !== "none" && (currentLayer === "precip" || currentLayer === "coldfront" || VARIABLE[currentLayer])) applyLayer(currentLayer);
      }, 250);
    },
    reloadLayer: function (layer) {
      if (typeof layer !== "string") return;
      currentLayer = "";
      gridCache = {};
      applyLayer(layer);
    },
    reloadBase: function () {
      baseReady = false;
      status.ready = false;
      status.error = "";
      mountBase(0);
    },
    setFeatures: function (layer, json) {
      if (typeof layer === "string" && typeof json === "string") showFeatures(layer, json);
    },
    featuresFailed: function (layer) {
      if (layer === currentLayer) setLayerState("error");
    },
    setDark: function (value) {
      document.body.classList.toggle("dark", value === true);
    },
    setCity: function (lat, lon) {
      if (!isFinite(lat) || !isFinite(lon)) return;
      if (cityMarker) { map.removeLayer(cityMarker); cityMarker = null; }
      cityMarker = L.marker([lat, lon], {
        icon: L.divIcon({ className: "", html: '<div class="city-dot"></div>', iconSize: [14, 14], iconAnchor: [7, 7] }),
        interactive: false,
        keyboard: false
      }).addTo(map);
    },
    focus: function (lat, lon, zoom, animate) {
      if (!isFinite(lat) || !isFinite(lon)) return;
      var z = isFinite(zoom) ? Math.min(18, Math.max(2, zoom)) : map.getZoom();
      if (animate === false) map.setView([lat, lon], z, { animate: false });
      else map.flyTo([lat, lon], z, { duration: 0.85 });
    },
    setActive: function (value) {
      active = value === true;
      if (active) { queueDraw(); map.invalidateSize(); }
    }
  };

    status.page = true;
  }

  loadScript([
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js",
    "https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.min.js",
    "https://unpkg.com/leaflet@1.9.4/dist/leaflet.min.js"
  ], 0, boot, function () { status.error = "script"; });
})();
