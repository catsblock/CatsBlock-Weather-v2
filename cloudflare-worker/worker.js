const SHORT_WINDOW_MS = 10 * 60 * 1000;
const LONG_WINDOW_MS = 12 * 60 * 60 * 1000;
const LOCKOUT_MS = 6 * 60 * 60 * 1000;
const SHORT_LIMIT = 20;
const LONG_LIMIT = 150;
const IP_CEILING_LIMIT = 400;
const IP_CEILING_WINDOW_MS = 10 * 60 * 1000;
const CACHE_TTL_S = 900;
const UPSTREAM_TIMEOUT_MS = 10000;
const MAX_BODY_CHARS = 3000000;
const MAX_URL_LENGTH = 512;
const WEATHER_EXHAUST_MS = 14 * 24 * 60 * 60 * 1000;
const WEATHER_WINDOW_MS = 4 * 24 * 60 * 60 * 1000;
const VISUAL_EXHAUST_MS = 24 * 60 * 60 * 1000;
const VISUAL_WINDOW_MS = 12 * 60 * 60 * 1000;
const WEATHER_LIMIT = 220;
const VISUAL_LIMIT = 75;
const OPENWEATHER_EXHAUST_MS = 24 * 60 * 60 * 1000;
const OPENWEATHER_WINDOW_MS = 12 * 60 * 60 * 1000;
const OPENWEATHER_LIMIT = 100;
const OPENWEATHER_MONTH_LIMIT = 350;
const MONTH_MS = 30 * 24 * 60 * 60 * 1000;
const WEATHER_MONTH_LIMIT = 590;
const VISUAL_MONTH_LIMIT = 260;

const DEVICE_ID_PATTERN = /^[a-f0-9]{64}$/;
const USER_KEY_PATTERN = /^[A-Za-z0-9_-]{16,64}$/;
const COORD_PATTERN = /^-?\d{1,3}(\.\d{1,10})?$/;

const SAFE_HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "no-referrer",
  "Cross-Origin-Resource-Policy": "same-origin",
  "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
  "Strict-Transport-Security": "max-age=63072000; includeSubDomains; preload",
  "Permissions-Policy": "geolocation=(), camera=(), microphone=()",
};

function redact(text, secrets) {
  let out = text;
  for (const secret of secrets) {
    if (!secret) continue;
    out = out.split(secret).join("[redacted]");
    out = out.split(encodeURIComponent(secret)).join("[redacted]");
  }
  return out;
}

function jsonResponse(payload, status, extraHeaders) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: Object.assign({ "Content-Type": "application/json; charset=utf-8" }, SAFE_HEADERS, extraHeaders || {}),
  });
}

function textResponse(body, status, contentType, extraHeaders) {
  return new Response(body, {
    status,
    headers: Object.assign({ "Content-Type": contentType }, SAFE_HEADERS, extraHeaders || {}),
  });
}

async function readJSON(kv, key) {
  let raw;
  try {
    raw = await kv.get(key);
  } catch (err) {
    return null;
  }
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch (err) {
    return null;
  }
}

async function writeJSON(kv, key, value, ttlSeconds) {
  try {
    await kv.put(key, JSON.stringify(value), { expirationTtl: Math.max(60, Math.ceil(ttlSeconds || 60)) });
    return true;
  } catch (err) {
    return false;
  }
}

function constantTimeEqual(a, b) {
  const enc = new TextEncoder();
  const x = enc.encode(String(a));
  const y = enc.encode(String(b));
  let diff = x.length ^ y.length;
  const len = Math.max(x.length, y.length);
  for (let i = 0; i < len; i++) {
    diff |= (x[i] || 0) ^ (y[i] || 0);
  }
  return diff === 0;
}

async function hmacHex(secret, message) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function clientIp(request) {
  const ip = (request.headers.get("CF-Connecting-IP") || "").trim();
  return ip.length > 0 && ip.length <= 64 ? ip : "";
}

function deviceId(request) {
  const id = (request.headers.get("X-Device-Id") || "").trim().toLowerCase();
  return DEVICE_ID_PATTERN.test(id) ? id : "";
}

async function identify(request, env) {
  const ip = clientIp(request);
  const device = deviceId(request);
  if (!ip) return { error: "client_unidentified" };
  if (!device) return { error: "device_unidentified" };
  const salt = env.RATE_SALT || "catsblock-weather";
  return {
    clientKey: "c:" + (await hmacHex(salt, "device|" + ip + "|" + device)),
    ipKey: "ip:" + (await hmacHex(salt, "ip|" + ip)),
  };
}

function authorized(request, env) {
  if (!env.APP_TOKEN) return true;
  return constantTimeEqual(request.headers.get("X-App-Token") || "", env.APP_TOKEN);
}

function freshWindow(entry, now, windowMs) {
  if (!entry || typeof entry.r !== "number" || entry.r <= now) return { c: 0, r: now + windowMs };
  return { c: Number(entry.c) || 0, r: entry.r };
}

async function peekRateLimit(kv, clientKey) {
  const now = Date.now();
  const rec = (await readJSON(kv, clientKey)) || {};
  const short = freshWindow(rec.s, now, SHORT_WINDOW_MS);
  const long = freshWindow(rec.l, now, LONG_WINDOW_MS);
  return {
    shortRemaining: Math.max(0, SHORT_LIMIT - short.c),
    shortLimit: SHORT_LIMIT,
    longRemaining: Math.max(0, LONG_LIMIT - long.c),
    longLimit: LONG_LIMIT,
    lockoutSeconds: rec.u && rec.u > now ? Math.ceil((rec.u - now) / 1000) : 0,
  };
}

async function consumeIpCeiling(kv, ipKey) {
  if (!IP_CEILING_LIMIT) return true;
  const now = Date.now();
  const rec = freshWindow(await readJSON(kv, ipKey), now, IP_CEILING_WINDOW_MS);
  rec.c += 1;
  if (rec.c > IP_CEILING_LIMIT) return false;
  await writeJSON(kv, ipKey, rec, (rec.r - now) / 1000 + 5);
  return true;
}

async function consumeRateLimit(kv, clientKey) {
  const now = Date.now();
  const rec = (await readJSON(kv, clientKey)) || {};
  if (rec.u && rec.u > now) {
    return { allowed: false, lockoutSeconds: Math.ceil((rec.u - now) / 1000) };
  }
  const short = freshWindow(rec.s, now, SHORT_WINDOW_MS);
  const long = freshWindow(rec.l, now, LONG_WINDOW_MS);
  short.c += 1;
  long.c += 1;

  if (short.c > SHORT_LIMIT || long.c > LONG_LIMIT) {
    const until = now + LOCKOUT_MS;
    await writeJSON(kv, clientKey, { s: short, l: long, u: until }, LOCKOUT_MS / 1000 + 5);
    return { allowed: false, lockoutSeconds: Math.ceil(LOCKOUT_MS / 1000) };
  }

  await writeJSON(kv, clientKey, { s: short, l: long }, (long.r - now) / 1000 + 5);

  return {
    allowed: true,
    shortRemaining: Math.max(0, SHORT_LIMIT - short.c),
    shortLimit: SHORT_LIMIT,
    longRemaining: Math.max(0, LONG_LIMIT - long.c),
    longLimit: LONG_LIMIT,
    lockoutSeconds: 0,
  };
}

async function exhaustedSeconds(kv, provider) {
  const data = await readJSON(kv, "exhausted:" + provider);
  if (!data || typeof data.until !== "number" || data.until <= Date.now()) return 0;
  return Math.ceil((data.until - Date.now()) / 1000);
}

async function markExhausted(kv, provider, durationMs) {
  const now = Date.now();
  const current = await readJSON(kv, "exhausted:" + provider);
  const until = Math.max(now + durationMs, current && current.until > now ? current.until : 0);
  await writeJSON(kv, "exhausted:" + provider, { until }, (until - now) / 1000);
}

function providerLimit(provider) {
  if (provider === "weather") return WEATHER_LIMIT;
  if (provider === "openweather") return OPENWEATHER_LIMIT;
  return VISUAL_LIMIT;
}

function providerWindow(provider) {
  if (provider === "weather") return WEATHER_WINDOW_MS;
  if (provider === "openweather") return OPENWEATHER_WINDOW_MS;
  return VISUAL_WINDOW_MS;
}

function providerExhaust(provider) {
  if (provider === "weather") return WEATHER_EXHAUST_MS;
  if (provider === "openweather") return OPENWEATHER_EXHAUST_MS;
  return VISUAL_EXHAUST_MS;
}

function monthlyLimit(provider) {
  if (provider === "weather") return WEATHER_MONTH_LIMIT;
  if (provider === "openweather") return OPENWEATHER_MONTH_LIMIT;
  return VISUAL_MONTH_LIMIT;
}

async function peekCounter(kv, key, limit) {
  const now = Date.now();
  const data = await readJSON(kv, key);
  if (!data || typeof data.reset !== "number" || data.reset <= now) return { used: 0, limit, resetSeconds: 0 };
  const used = Math.min(Number(data.count) || 0, limit);
  return { used, limit, resetSeconds: used >= limit ? Math.ceil((data.reset - now) / 1000) : 0 };
}

async function peekAll(kv, provider) {
  return {
    short: await peekCounter(kv, "usage:" + provider, providerLimit(provider)),
    monthly: await peekCounter(kv, "monthly:" + provider, monthlyLimit(provider)),
  };
}

async function recordCounter(kv, key, limit, windowMs, exhaustMs, provider, extend) {
  const now = Date.now();
  let data = await readJSON(kv, key);
  if (!data || typeof data.reset !== "number" || data.reset <= now) data = { count: 0, reset: now + windowMs };
  data.count = (Number(data.count) || 0) + 1;
  if (data.count >= limit) {
    if (extend) data.reset = now + exhaustMs;
    await markExhausted(kv, provider, Math.max(1000, data.reset - now));
  }
  await writeJSON(kv, key, data, (data.reset - now) / 1000 + 5);
}

async function recordUsage(kv, provider) {
  await recordCounter(kv, "usage:" + provider, providerLimit(provider), providerWindow(provider), providerExhaust(provider), provider, true);
  await recordCounter(kv, "monthly:" + provider, monthlyLimit(provider), MONTH_MS, MONTH_MS, provider, false);
}

function usageHeaders(all) {
  return {
    "X-Provider-Used": String(all.short.used),
    "X-Provider-Limit": String(all.short.limit),
    "X-Provider-Reset-Seconds": String(all.short.resetSeconds),
    "X-Monthly-Used": String(all.monthly.used),
    "X-Monthly-Limit": String(all.monthly.limit),
    "X-Monthly-Reset-Seconds": String(all.monthly.resetSeconds),
  };
}

function rateHeaders(limitState) {
  return {
    "X-RateLimit-Remaining-10m": String(limitState.shortRemaining),
    "X-RateLimit-Limit-10m": String(limitState.shortLimit),
    "X-RateLimit-Remaining-12h": String(limitState.longRemaining),
    "X-RateLimit-Limit-12h": String(limitState.longLimit),
    "X-Lockout-Seconds": String(limitState.lockoutSeconds || 0),
  };
}

function parseCoordinates(url) {
  const rawLat = url.searchParams.get("lat");
  const rawLon = url.searchParams.get("lon");
  if (!rawLat || !rawLon || !COORD_PATTERN.test(rawLat) || !COORD_PATTERN.test(rawLon)) return null;
  const lat = Number(rawLat);
  const lon = Number(rawLon);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return { lat: Math.round(lat * 100) / 100, lon: Math.round(lon * 100) / 100 };
}

function cacheKey(provider, lat, lon) {
  return "cache:" + provider + ":" + lat.toFixed(2) + ":" + lon.toFixed(2);
}

function isQuotaExceeded(provider, status, bodyText) {
  if (status === 429) return true;
  if (provider === "openweather") return false;
  if (provider === "weather") {
    if (status === 400 || status === 401 || status === 403) {
      return /220[6-9]/.test(bodyText) || /quota/i.test(bodyText) || /exceeded/i.test(bodyText);
    }
    return false;
  }
  if (status === 401 || status === 403) {
    return /quota/i.test(bodyText) || /exceeded/i.test(bodyText) || /limit/i.test(bodyText);
  }
  return false;
}

function upstreamUrl(provider, lat, lon, apiKey) {
  if (provider === "openweather") {
    const params = new URLSearchParams({
      lat: lat.toFixed(2),
      lon: lon.toFixed(2),
      units: "metric",
      exclude: "minutely",
      appid: apiKey,
    });
    return "https://api.openweathermap.org/data/3.0/onecall?" + params.toString();
  }
  if (provider === "weather") {
    const params = new URLSearchParams({
      key: apiKey,
      q: lat.toFixed(2) + "," + lon.toFixed(2),
      days: "7",
      aqi: "no",
      alerts: "yes",
    });
    return "https://api.weatherapi.com/v1/forecast.json?" + params.toString();
  }
  const params = new URLSearchParams({
    unitGroup: "metric",
    include: "days,hours,current,alerts",
    contentType: "json",
    key: apiKey,
  });
  return (
    "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/" +
    lat.toFixed(2) +
    "," +
    lon.toFixed(2) +
    "?" +
    params.toString()
  );
}

async function readCapped(response) {
  const declared = Number(response.headers.get("Content-Length") || 0);
  if (declared > MAX_BODY_CHARS) return null;
  const text = await response.text();
  return text.length > MAX_BODY_CHARS ? null : text;
}

async function handleStatus(request, env) {
  const id = await identify(request, env);
  if (id.error) return jsonResponse({ error: id.error }, 400);
  const limitState = await peekRateLimit(env.APP_KV, id.clientKey);
  const weatherExhausted = await exhaustedSeconds(env.APP_KV, "weather");
  const visualExhausted = await exhaustedSeconds(env.APP_KV, "visual");
  const openweatherExhausted = await exhaustedSeconds(env.APP_KV, "openweather");
  const openweatherAll = await peekAll(env.APP_KV, "openweather");
  const weatherAll = await peekAll(env.APP_KV, "weather");
  const visualAll = await peekAll(env.APP_KV, "visual");
  return jsonResponse(
    {
      usage: {
        weather: weatherAll.short,
        visual: visualAll.short,
        openweather: openweatherAll.short,
        monthly: {
          weather: weatherAll.monthly,
          visual: visualAll.monthly,
          openweather: openweatherAll.monthly,
        },
      },
      rateLimit: {
        shortRemaining: limitState.shortRemaining,
        shortLimit: limitState.shortLimit,
        longRemaining: limitState.longRemaining,
        longLimit: limitState.longLimit,
        lockoutSeconds: limitState.lockoutSeconds,
      },
      exhausted: {
        weather: weatherExhausted,
        visual: visualExhausted,
        openweather: openweatherExhausted,
      },
    },
    200,
    rateHeaders(limitState)
  );
}

async function handleProxy(request, env, provider) {
  const url = new URL(request.url);
  const coords = parseCoordinates(url);
  if (!coords) {
    return jsonResponse({ error: "invalid_coordinates" }, 400);
  }

  const rawUserKey = (request.headers.get("X-Api-Key") || "").trim();
  if (rawUserKey && !USER_KEY_PATTERN.test(rawUserKey)) {
    return jsonResponse({ error: "invalid_api_key_format" }, 400);
  }
  const usingUserKey = rawUserKey.length > 0;
  const sharedKey = provider === "weather" ? env.WEATHER_API_KEY : provider === "openweather" ? env.OPENWEATHER_API_KEY : env.VISUAL_API_KEY;
  const apiKey = usingUserKey ? rawUserKey : sharedKey;
  if (!apiKey) {
    return jsonResponse({ error: "service_unavailable" }, 503);
  }

  const id = await identify(request, env);
  if (id.error) {
    return jsonResponse({ error: id.error }, 400);
  }

  const exhaustDuration = providerExhaust(provider);
  if (!usingUserKey) {
    const remainingExhaust = await exhaustedSeconds(env.APP_KV, provider);
    if (remainingExhaust > 0) {
      const blockedUsage = await peekAll(env.APP_KV, provider);
      return jsonResponse(
        { error: "provider_exhausted", provider, retryAfterSeconds: remainingExhaust },
        503,
        Object.assign(usageHeaders(blockedUsage), {
          "X-Global-Exhausted-Seconds": String(remainingExhaust),
          "Retry-After": String(remainingExhaust),
        })
      );
    }
  }

  if (!(await consumeIpCeiling(env.APP_KV, id.ipKey))) {
    return jsonResponse({ error: "rate_limited", retryAfterSeconds: 600 }, 429, { "Retry-After": "600" });
  }

  const limitState = await consumeRateLimit(env.APP_KV, id.clientKey);
  if (!limitState.allowed) {
    return jsonResponse(
      { error: "rate_limited", retryAfterSeconds: limitState.lockoutSeconds },
      429,
      { "X-Lockout-Seconds": String(limitState.lockoutSeconds), "Retry-After": String(limitState.lockoutSeconds) }
    );
  }

  const key = cacheKey(provider, coords.lat, coords.lon);
  let cached = null;
  try {
    cached = await env.APP_KV.get(key);
  } catch (err) {
    cached = null;
  }
  if (cached) {
    const cachedUsage = await peekAll(env.APP_KV, provider);
    return textResponse(
      cached,
      200,
      "application/json; charset=utf-8",
      Object.assign(rateHeaders(limitState), usageHeaders(cachedUsage), { "X-Cache": "HIT" })
    );
  }

  let upstreamResponse;
  try {
    upstreamResponse = await fetch(upstreamUrl(provider, coords.lat, coords.lon, apiKey), {
      method: "GET",
      redirect: "manual",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
  } catch (err) {
    const failedUsage = await peekAll(env.APP_KV, provider);
    return jsonResponse({ error: "upstream_unreachable" }, 502, Object.assign(rateHeaders(limitState), usageHeaders(failedUsage)));
  }

  if (!usingUserKey) {
    await recordUsage(env.APP_KV, provider);
  }
  const usage = await peekAll(env.APP_KV, provider);
  const metaHeaders = Object.assign(rateHeaders(limitState), usageHeaders(usage));

  let bodyText;
  try {
    bodyText = await readCapped(upstreamResponse);
  } catch (err) {
    return jsonResponse({ error: "upstream_unreadable" }, 502, metaHeaders);
  }
  if (bodyText === null) {
    return jsonResponse({ error: "upstream_too_large" }, 502, metaHeaders);
  }
  bodyText = redact(bodyText, [apiKey]);

  if (!upstreamResponse.ok) {
    if (usingUserKey) {
      const authFailure = upstreamResponse.status === 401 || upstreamResponse.status === 403;
      return jsonResponse({ error: authFailure ? "user_api_key_rejected" : "upstream_error", status: upstreamResponse.status }, authFailure ? 401 : 502, metaHeaders);
    }
    if (isQuotaExceeded(provider, upstreamResponse.status, bodyText)) {
      await markExhausted(env.APP_KV, provider, exhaustDuration);
      return jsonResponse(
        { error: "provider_exhausted", provider, retryAfterSeconds: Math.ceil(exhaustDuration / 1000) },
        503,
        Object.assign({}, metaHeaders, { "X-Global-Exhausted-Seconds": String(Math.ceil(exhaustDuration / 1000)) })
      );
    }
    return jsonResponse({ error: "upstream_error", status: upstreamResponse.status }, 502, metaHeaders);
  }

  try {
    JSON.parse(bodyText);
  } catch (err) {
    return jsonResponse({ error: "upstream_invalid_response" }, 502, metaHeaders);
  }

  await writeJSONText(env.APP_KV, key, bodyText, CACHE_TTL_S);

  return textResponse(bodyText, 200, "application/json; charset=utf-8", Object.assign({}, metaHeaders, { "X-Cache": "MISS" }));
}

async function writeJSONText(kv, key, text, ttlSeconds) {
  try {
    await kv.put(key, text, { expirationTtl: ttlSeconds });
  } catch (err) {
    return false;
  }
  return true;
}

export default {
  async fetch(request, env) {
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: SAFE_HEADERS });
      }
      if (request.method !== "GET") {
        return jsonResponse({ error: "method_not_allowed" }, 405, { Allow: "GET, OPTIONS" });
      }
      if (request.url.length > MAX_URL_LENGTH) {
        return jsonResponse({ error: "uri_too_long" }, 414);
      }

      const url = new URL(request.url);
      const path = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, "") : url.pathname;

      if (path === "/" || path === "/v1/health") {
        return textResponse("ok", 200, "text/plain; charset=utf-8");
      }

      const routes = {
        "/v1/status": null,
        "/v1/weather": "weather",
        "/v1/visual": "visual",
        "/v1/openweather": "openweather",
      };
      if (!Object.prototype.hasOwnProperty.call(routes, path)) {
        return jsonResponse({ error: "not_found" }, 404);
      }

      if (!authorized(request, env)) {
        return jsonResponse({ error: "unauthorized" }, 401);
      }

      if (path === "/v1/status") {
        return await handleStatus(request, env);
      }
      return await handleProxy(request, env, routes[path]);
    } catch (err) {
      return jsonResponse({ error: "internal_error" }, 500);
    }
  },
};
