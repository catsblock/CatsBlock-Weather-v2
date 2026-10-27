const SHORT_WINDOW_MS = 10 * 60 * 1000;
const LONG_WINDOW_MS = 12 * 60 * 60 * 1000;
const LOCKOUT_MS = 6 * 60 * 60 * 1000;
const SHORT_LIMIT = 20;
const LONG_LIMIT = 150;
const CACHE_TTL_S = 900;
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

const SAFE_HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Cross-Origin-Resource-Policy": "same-origin",
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
    headers: Object.assign(
      { "Content-Type": "application/json; charset=utf-8" },
      SAFE_HEADERS,
      extraHeaders || {}
    ),
  });
}

function textResponse(body, status, contentType, extraHeaders) {
  return new Response(body, {
    status,
    headers: Object.assign(
      { "Content-Type": contentType },
      SAFE_HEADERS,
      extraHeaders || {}
    ),
  });
}

async function readJSON(kv, key) {
  const raw = await kv.get(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (err) {
    return null;
  }
}

async function writeJSON(kv, key, value, ttlSeconds) {
  const options = ttlSeconds ? { expirationTtl: ttlSeconds } : undefined;
  await kv.put(key, JSON.stringify(value), options);
}

function clientIp(request) {
  return request.headers.get("CF-Connecting-IP") || "";
}

async function peekRateLimit(kv, ip) {
  const now = Date.now();
  const lock = await readJSON(kv, "lock:" + ip);
  const short = await readJSON(kv, "rl10:" + ip);
  const long = await readJSON(kv, "rl12:" + ip);
  const shortRemaining = short && short.reset > now ? Math.max(0, SHORT_LIMIT - short.count) : SHORT_LIMIT;
  const longRemaining = long && long.reset > now ? Math.max(0, LONG_LIMIT - long.count) : LONG_LIMIT;
  const lockoutSeconds = lock && lock.until > now ? Math.ceil((lock.until - now) / 1000) : 0;
  return { shortRemaining, shortLimit: SHORT_LIMIT, longRemaining, longLimit: LONG_LIMIT, lockoutSeconds };
}

async function consumeRateLimit(kv, ip) {
  const now = Date.now();
  const lockKey = "lock:" + ip;
  const lock = await readJSON(kv, lockKey);
  if (lock && lock.until > now) {
    return { allowed: false, lockoutSeconds: Math.ceil((lock.until - now) / 1000) };
  }

  const shortKey = "rl10:" + ip;
  const longKey = "rl12:" + ip;
  let short = await readJSON(kv, shortKey);
  let long = await readJSON(kv, longKey);
  if (!short || short.reset <= now) short = { count: 0, reset: now + SHORT_WINDOW_MS };
  if (!long || long.reset <= now) long = { count: 0, reset: now + LONG_WINDOW_MS };
  short.count += 1;
  long.count += 1;

  if (short.count > SHORT_LIMIT || long.count > LONG_LIMIT) {
    const until = now + LOCKOUT_MS;
    await writeJSON(kv, lockKey, { until }, Math.ceil(LOCKOUT_MS / 1000));
    return { allowed: false, lockoutSeconds: Math.ceil(LOCKOUT_MS / 1000) };
  }

  await writeJSON(kv, shortKey, short, Math.ceil((short.reset - now) / 1000) + 5);
  await writeJSON(kv, longKey, long, Math.ceil((long.reset - now) / 1000) + 5);

  return {
    allowed: true,
    shortRemaining: Math.max(0, SHORT_LIMIT - short.count),
    shortLimit: SHORT_LIMIT,
    longRemaining: Math.max(0, LONG_LIMIT - long.count),
    longLimit: LONG_LIMIT,
  };
}

async function exhaustedSeconds(kv, provider) {
  const data = await readJSON(kv, "exhausted:" + provider);
  if (!data || data.until <= Date.now()) return 0;
  return Math.ceil((data.until - Date.now()) / 1000);
}

async function markExhausted(kv, provider, durationMs) {
  const now = Date.now();
  const current = await readJSON(kv, "exhausted:" + provider);
  const until = Math.max(now + durationMs, current && current.until > now ? current.until : 0);
  await writeJSON(kv, "exhausted:" + provider, { until }, Math.ceil((until - now) / 1000));
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
  if (!data || data.reset <= now) return { used: 0, limit, resetSeconds: 0 };
  const used = Math.min(data.count, limit);
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
  if (!data || data.reset <= now) data = { count: 0, reset: now + windowMs };
  data.count += 1;
  if (data.count >= limit) {
    if (extend) data.reset = now + exhaustMs;
    await markExhausted(kv, provider, Math.max(1000, data.reset - now));
  }
  await writeJSON(kv, key, data, Math.ceil((data.reset - now) / 1000) + 5);
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
  const lat = parseFloat(url.searchParams.get("lat"));
  const lon = parseFloat(url.searchParams.get("lon"));
  if (Number.isNaN(lat) || Number.isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    return null;
  }
  return { lat, lon };
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
      lat: lat.toFixed(4),
      lon: lon.toFixed(4),
      units: "metric",
      exclude: "minutely",
      appid: apiKey,
    });
    return "https://api.openweathermap.org/data/3.0/onecall?" + params.toString();
  }
  if (provider === "weather") {
    const params = new URLSearchParams({
      key: apiKey,
      q: lat.toFixed(4) + "," + lon.toFixed(4),
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
    lat.toFixed(4) +
    "," +
    lon.toFixed(4) +
    "?" +
    params.toString()
  );
}

async function handleStatus(request, env) {
  const ip = clientIp(request);
  if (!ip) return jsonResponse({ error: "client_unidentified" }, 400);
  const limitState = await peekRateLimit(env.APP_KV, ip);
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

  const userKey = (request.headers.get("X-Api-Key") || "").trim();
  const apiKey = userKey || (provider === "weather" ? env.WEATHER_API_KEY : provider === "openweather" ? env.OPENWEATHER_API_KEY : env.VISUAL_API_KEY);
  if (!apiKey) {
    return jsonResponse({ error: "provider_unconfigured" }, 500);
  }

  const exhaustDuration = providerExhaust(provider);
  const remainingExhaust = await exhaustedSeconds(env.APP_KV, provider);
  if (remainingExhaust > 0) {
    const blockedUsage = await peekAll(env.APP_KV, provider);
    return jsonResponse(
      { error: "provider_exhausted", provider, retryAfterSeconds: remainingExhaust },
      503,
      Object.assign(usageHeaders(blockedUsage), { "X-Global-Exhausted-Seconds": String(remainingExhaust) })
    );
  }

  const ip = clientIp(request);
  if (!ip) {
    return jsonResponse({ error: "client_unidentified" }, 400);
  }

  const limitState = await consumeRateLimit(env.APP_KV, ip);
  if (!limitState.allowed) {
    return jsonResponse(
      { error: "rate_limited", retryAfterSeconds: limitState.lockoutSeconds },
      429,
      { "X-Lockout-Seconds": String(limitState.lockoutSeconds) }
    );
  }

  const key = cacheKey(provider, coords.lat, coords.lon);
  const cached = await env.APP_KV.get(key);
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
    upstreamResponse = await fetch(upstreamUrl(provider, coords.lat, coords.lon, apiKey));
  } catch (err) {
    const failedUsage = await peekAll(env.APP_KV, provider);
    return jsonResponse({ error: "upstream_unreachable" }, 502, Object.assign(rateHeaders(limitState), usageHeaders(failedUsage)));
  }
  await recordUsage(env.APP_KV, provider);
  const usage = await peekAll(env.APP_KV, provider);
  const metaHeaders = Object.assign(rateHeaders(limitState), usageHeaders(usage));

  let bodyText;
  try {
    bodyText = redact(await upstreamResponse.text(), [apiKey]);
  } catch (err) {
    return jsonResponse({ error: "upstream_unreadable" }, 502, metaHeaders);
  }

  if (!upstreamResponse.ok) {
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

  await env.APP_KV.put(key, bodyText, { expirationTtl: CACHE_TTL_S });

  return textResponse(bodyText, 200, "application/json; charset=utf-8", Object.assign({}, metaHeaders, { "X-Cache": "MISS" }));
}

export default {
  async fetch(request, env) {
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: SAFE_HEADERS });
      }
      if (request.method !== "GET") {
        return jsonResponse({ error: "method_not_allowed" }, 405);
      }

      const url = new URL(request.url);

      if (url.pathname === "/" || url.pathname === "/v1/health") {
        return textResponse("ok", 200, "text/plain; charset=utf-8");
      }
      if (url.pathname === "/v1/status") {
        return await handleStatus(request, env);
      }
      if (url.pathname === "/v1/weather") {
        return await handleProxy(request, env, "weather");
      }
      if (url.pathname === "/v1/visual") {
        return await handleProxy(request, env, "visual");
      }
      if (url.pathname === "/v1/openweather") {
        return await handleProxy(request, env, "openweather");
      }

      return jsonResponse({ error: "not_found" }, 404);
    } catch (err) {
      return jsonResponse({ error: "internal_error" }, 500);
    }
  },
};
