const MAX_BODY_BYTES = 4096;
const RETENTION_S = 90 * 24 * 60 * 60;
const RATE_WINDOW_S = 60 * 60;
const RATE_MAX = 20;
const PAGE_SIZE = 20;
const MAX_PROMPT = 200;
const MAX_REPLY = 1200;
const KEY_PREFIX = "fb:";
const MAX_SUBMISSIONS = 2;
const MESSAGE_KEY = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const KEY_CEILING = 9999999999999;
const LANG_PATTERN = /^[a-z]{2,3}(-[A-Z]{2})?$/;
const CURSOR_MAX = 512;
const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g;

const REASONS = {
  good: new Set(["accurate", "helpful", "clear", "complete", "right_place", "cards", "other"]),
  bad: new Set(["inaccurate", "not_relevant", "incomplete", "confusing", "wrong_place", "outdated", "other"]),
};

async function readBody(request) {
  const declared = Number(request.headers.get("Content-Length") || 0);
  if (declared > MAX_BODY_BYTES || !request.body) return null;
  const reader = request.body.getReader();
  const chunks = [];
  let received = 0;
  try {
    for (;;) {
      const part = await reader.read();
      if (part.done) break;
      received += part.value.byteLength;
      if (received > MAX_BODY_BYTES) {
        await reader.cancel();
        return null;
      }
      chunks.push(part.value);
    }
    const merged = new Uint8Array(received);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.byteLength;
    }
    const parsed = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(merged));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch (err) {
    return null;
  }
}

function cleanText(value, max) {
  if (typeof value !== "string") return "";
  const text = value.replace(CONTROL_CHARS, "").trim();
  return Array.from(text).slice(0, max).join("");
}

function validate(body) {
  const rating = body.rating === "good" || body.rating === "bad" ? body.rating : "";
  if (!rating) return null;
  if (!Array.isArray(body.reasons) || body.reasons.length === 0 || body.reasons.length > 8) return null;
  const reasons = [];
  for (const reason of body.reasons) {
    if (typeof reason !== "string" || !REASONS[rating].has(reason)) return null;
    if (!reasons.includes(reason)) reasons.push(reason);
  }
  const prompt = cleanText(body.prompt, MAX_PROMPT);
  const reply = cleanText(body.reply, MAX_REPLY);
  if (!prompt || !reply) return null;
  const messageKey = typeof body.messageKey === "string" && MESSAGE_KEY.test(body.messageKey) ? body.messageKey : "";
  if (!messageKey) return null;
  const lang = typeof body.lang === "string" && LANG_PATTERN.test(body.lang) ? body.lang : "en";
  return { messageKey, rating, reasons, prompt, reply, lang };
}

async function markerKeyFor(owner, messageKey) {
  const data = new TextEncoder().encode(owner + "|" + messageKey);
  const digest = await crypto.subtle.digest("SHA-256", data);
  const hex = Array.from(new Uint8Array(digest)).map((x) => x.toString(16).padStart(2, "0")).join("");
  return "fbm:" + hex.slice(0, 40);
}

async function readMarker(env, key) {
  const raw = await env.APP_KV.get(key);
  if (!raw) return null;
  try {
    const m = JSON.parse(raw);
    return m && typeof m.key === "string" && typeof m.rating === "string" ? { key: m.key, rating: m.rating, n: Number(m.n) || 1 } : null;
  } catch (err) {
    return null;
  }
}

async function withinRate(env, key) {
  const raw = await env.APP_KV.get(key);
  const count = raw ? Number(raw) : 0;
  if (Number.isFinite(count) && count >= RATE_MAX) return false;
  await env.APP_KV.put(key, String((Number.isFinite(count) ? count : 0) + 1), { expirationTtl: RATE_WINDOW_S });
  return true;
}

export async function handleFeedback(request, env, h) {
  if (!env.APP_KV) return h.jsonResponse({ error: "unavailable" }, 503);
  const id = await h.identify(request, env);
  if (id.error) return h.jsonResponse({ error: "bad_request" }, 400);
  const body = await readBody(request);
  const entry = body ? validate(body) : null;
  if (!entry) return h.jsonResponse({ error: "bad_request" }, 400);
  const owner = id.hasDevice ? id.deviceHash : id.ipHash;
  const markerKey = await markerKeyFor(owner, entry.messageKey);
  const marker = await readMarker(env, markerKey);
  if (marker && marker.rating === entry.rating) return h.jsonResponse({ error: "already_submitted" }, 409);
  if (marker && marker.n >= MAX_SUBMISSIONS) return h.jsonResponse({ error: "change_limit" }, 409);
  if (!(await withinRate(env, "fbrate:" + owner))) return h.jsonResponse({ error: "rate_limited" }, 429);
  const now = Date.now();
  const stored = { v: 1, ts: now, rating: entry.rating, reasons: entry.reasons, prompt: entry.prompt, reply: entry.reply, lang: entry.lang };
  let key = marker ? marker.key : "";
  if (!key) {
    const suffix = crypto.randomUUID().replace(/-/g, "").slice(0, 8);
    key = KEY_PREFIX + String(KEY_CEILING - now).padStart(13, "0") + ":" + suffix;
  }
  await env.APP_KV.put(key, JSON.stringify(stored), { expirationTtl: RETENTION_S });
  await env.APP_KV.put(markerKey, JSON.stringify({ key, rating: entry.rating, n: marker ? marker.n + 1 : 1 }), { expirationTtl: RETENTION_S });
  return h.jsonResponse({ ok: true }, 200);
}

export async function listFeedback(env, cursor) {
  if (!env.APP_KV) return null;
  try {
    const options = { prefix: KEY_PREFIX, limit: PAGE_SIZE };
    if (typeof cursor === "string" && cursor && cursor.length <= CURSOR_MAX) options.cursor = cursor;
    const page = await env.APP_KV.list(options);
    const rows = await Promise.all(
      page.keys.map(async (item) => {
        const raw = await env.APP_KV.get(item.name);
        if (!raw) return null;
        try {
          const data = JSON.parse(raw);
          return {
            id: item.name,
            ts: Number(data.ts) || 0,
            rating: data.rating === "good" ? "good" : "bad",
            reasons: Array.isArray(data.reasons) ? data.reasons.filter((r) => typeof r === "string").slice(0, 8) : [],
            prompt: typeof data.prompt === "string" ? data.prompt : "",
            reply: typeof data.reply === "string" ? data.reply : "",
            lang: typeof data.lang === "string" ? data.lang : "en",
          };
        } catch (err) {
          return null;
        }
      })
    );
    return { items: rows.filter(Boolean), cursor: page.list_complete ? "" : page.cursor || "" };
  } catch (err) {
    return null;
  }
}
