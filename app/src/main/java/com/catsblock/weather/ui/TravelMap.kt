package com.catsblock.weather.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.catsblock.weather.data.TravelPlan
import com.catsblock.weather.i18n.tr
import org.json.JSONArray
import org.json.JSONObject

internal object TravelMapPage {

    private val html: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" onerror="this.onerror=null;this.href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css'">
<style>
html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:var(--bg);font-family:sans-serif;-webkit-tap-highlight-color:transparent}
body.light{--bg:#eef0f5;--fg:#1b1d24;--chip:rgba(255,255,255,.92)}
body.dark{--bg:#14161c;--fg:#e9ebf2;--chip:rgba(32,35,44,.92)}
#map{position:absolute;inset:0;background:var(--bg)}
.dim .leaflet-tile-pane{filter:invert(1) hue-rotate(180deg) brightness(.82) contrast(.92)}
.leaflet-control-attribution{background:var(--chip)!important;color:var(--fg)!important;font-size:10px;border-radius:10px 0 0 0;padding:2px 8px}
#note{position:absolute;z-index:1100;left:50%;top:50%;transform:translate(-50%,-50%);display:none;padding:12px 20px;border-radius:20px;background:var(--chip);color:var(--fg);font-size:14px;font-weight:600;text-align:center;max-width:80%}
.tm{background:none;border:0}
.pin{width:28px;height:28px;box-sizing:border-box;border-radius:50%;border:3px solid #fff;color:#fff;font:700 13px/22px sans-serif;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.4)}
</style>
</head>
<body>
<div id="map"></div>
<div id="note"></div>
<script>
var INIT = __INIT__;
(function () {
var noteEl = document.getElementById('note');
document.body.className = INIT.dark ? 'dark' : 'light';
function note(t) { noteEl.textContent = t; noteEl.style.display = 'block'; }
function loadScript(urls, i, ok, bad) {
  var el = document.createElement('script');
  el.src = urls[i];
  el.onload = ok;
  el.onerror = function () { if (i + 1 < urls.length) loadScript(urls, i + 1, ok, bad); else bad(); };
  document.head.appendChild(el);
}
function start() {
  var holder = document.getElementById('map');
  var map = L.map('map', {zoomControl:false, attributionControl:false, minZoom:2, maxZoom:18, zoomSnap:0.25}).setView(INIT.path[0], 6);
  L.control.attribution({position:'bottomright', prefix:false}).addTo(map).addAttribution('© OpenStreetMap contributors');
  var tileOk = false;
  var base = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19});
  base.on('tileload', function () { tileOk = true; noteEl.style.display = 'none'; });
  base.addTo(map);
  holder.classList.toggle('dim', INIT.dark);
  L.polyline(INIT.path, {color:INIT.line, weight:14, opacity:0.28, lineCap:'round', lineJoin:'round'}).addTo(map);
  var line = L.polyline(INIT.path, {color:INIT.line, weight:5, lineCap:'round', lineJoin:'round'}).addTo(map);
  var last = INIT.stops.length - 1;
  INIT.stops.forEach(function (s, n) {
    var color = n === 0 ? INIT.start : (n === last ? INIT.end : INIT.line);
    var icon = L.divIcon({className:'tm', html:'<div class="pin" style="background:' + color + '">' + (n + 1) + '</div>', iconSize:[28, 28], iconAnchor:[14, 14]});
    L.marker([s[0], s[1]], {icon:icon, interactive:false}).addTo(map);
  });
  var fitted = false;
  function layout() {
    map.invalidateSize();
    if (!fitted && holder.clientWidth > 0 && holder.clientHeight > 0) {
      fitted = true;
      map.fitBounds(line.getBounds(), {padding:[36, 36], animate:false});
    }
  }
  if (window.ResizeObserver) new ResizeObserver(layout).observe(holder);
  window.addEventListener('resize', layout);
  [0, 200, 800].forEach(function (d) { setTimeout(layout, d); });
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

private fun Color.hex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

private fun travelMapInit(plan: TravelPlan, dark: Boolean, line: Color, start: Color, end: Color, error: String): String {
    val path = JSONArray()
    plan.path.forEach { path.put(JSONArray().put(it.lat).put(it.lon)) }
    val stops = JSONArray()
    plan.stops.forEach { stops.put(JSONArray().put(it.point.lat).put(it.point.lon)) }
    return JSONObject()
        .put("path", path)
        .put("stops", stops)
        .put("dark", dark)
        .put("line", line.hex())
        .put("start", start.hex())
        .put("end", end.hex())
        .put("err", error)
        .toString()
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun TravelMap(plan: TravelPlan, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = rememberDarkMap()
    val error = tr("Map data unavailable")
    val page = remember(plan, dark, scheme.primary, scheme.tertiary, scheme.secondary, error) {
        TravelMapPage.page(travelMapInit(plan, dark, scheme.primary, scheme.tertiary, scheme.secondary, error))
    }
    val background = scheme.surfaceContainer.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(background)
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        update = { web ->
            if (web.tag != page) {
                web.tag = page
                web.loadDataWithBaseURL(MapPage.BASE_URL, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}
