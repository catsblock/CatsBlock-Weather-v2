import { metricsCall } from "./analytics.js";

export const ADMIN_COMMAND = "/adminpanelcommandbycatsblockcorporation2026weather";
export const ADMIN_PATHS = new Set(["/v1/admin/stage1", "/v1/admin/stage2", "/v1/admin/metrics"]);

const BAN_MS = 14 * 60 * 60 * 1000;
const MAX_FAILURES = 2;
const STAGE_TTL_S = 300;
const SESSION_TTL_S = 1800;
const GLOBAL_WINDOW_MS = 60 * 60 * 1000;
const GLOBAL_LIMIT = 40;
const MAX_BODY_BYTES = 1024;
const EDGE_NEGATIVE_TTL_MS = 15000;
const EDGE_CACHE_MAX = 2000;
const MIN_FIRST_SECRET = 4;
const MIN_SECOND_SECRET = 2;
const CODE_PATTERN = /^[0-9]{10}$/;
const TOKEN_PATTERN = /^[a-z0-9]{2}\.[0-9]{10}\.[a-f0-9]{32}\.[a-f0-9]{64}$/;
const EPOCH_PATTERN = /^[A-Za-z0-9_-]{1,16}$/;
const FORBIDDEN = { error: "forbidden" };

function epochOf(env) {
  const value = env && typeof env.ADMIN_LOCK_EPOCH === "string" ? env.ADMIN_LOCK_EPOCH.trim() : "";
  return EPOCH_PATTERN.test(value) ? value : "0";
}

function guardStub(env, name) {
  if (!env.ADMIN_GUARD) throw new Error("guard_missing");
  return env.ADMIN_GUARD.get(env.ADMIN_GUARD.idFromName(epochOf(env) + ":" + name));
}

async function guardCall(env, name, op, extra) {
  const response = await guardStub(env, name).fetch("https://admin-guard.internal/" + op, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(Object.assign({}, extra || {}, { op })),
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) throw new Error("guard_http");
  return response.json();
}

function reply(payload, status) {
  return new Response(JSON.stringify(payload), {
    status: status || 200,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

export class AdminGuard {
  constructor(state) {
    this.state = state;
    this.data = null;
    this.inflight = 0;
    this.tail = Promise.resolve();
  }

  async load() {
    if (this.data) return;
    const stored = await this.state.storage.get("d");
    const base = stored && typeof stored === "object" ? stored : {};
    this.data = {
      fails: Number(base.fails) || 0,
      windowEnd: Number(base.windowEnd) || 0,
      banUntil: Number(base.banUntil) || 0,
      used: base.used && typeof base.used === "object" ? base.used : {},
      g: base.g && typeof base.g === "object" ? base.g : { c: 0, r: 0 },
    };
  }

  prune(now) {
    const d = this.data;
    if (d.windowEnd <= now) {
      d.fails = 0;
      d.windowEnd = 0;
    }
    if (d.banUntil <= now) d.banUntil = 0;
    for (const nonce of Object.keys(d.used)) {
      if (!(d.used[nonce] > now)) delete d.used[nonce];
    }
    if (!(d.g.r > now)) d.g = { c: 0, r: 0 };
  }

  async save(now) {
    const d = this.data;
    this.prune(now);
    let until = Math.max(d.windowEnd, d.banUntil, d.g.r);
    for (const nonce of Object.keys(d.used)) until = Math.max(until, d.used[nonce]);
    if (until <= now) {
      await this.state.storage.deleteAll();
      return;
    }
    await this.state.storage.put("d", d);
    await this.state.storage.setAlarm(until + 5000);
  }

  async process(body) {
    await this.load();
    const now = Date.now();
    const d = this.data;
    this.prune(now);
    let result;
    let mutated = true;
    switch (body.op) {
      case "reserve":
        if (d.banUntil > now) {
          result = { banned: true, banUntil: d.banUntil };
          mutated = false;
        } else if (d.fails + this.inflight >= MAX_FAILURES) {
          result = { denied: true };
          mutated = false;
        } else {
          this.inflight += 1;
          result = { ok: true };
          mutated = false;
        }
        break;
      case "release":
        this.inflight = Math.max(0, this.inflight - 1);
        result = { ok: true };
        mutated = false;
        break;
      case "fail":
        this.inflight = Math.max(0, this.inflight - 1);
        if (d.windowEnd <= now) d.windowEnd = now + BAN_MS;
        d.fails += 1;
        if (d.fails >= MAX_FAILURES) {
          d.banUntil = now + BAN_MS;
          result = { banned: true, banUntil: d.banUntil };
        } else {
          result = { banned: false, remaining: Math.max(0, MAX_FAILURES - d.fails) };
        }
        break;
      case "ban": {
        const until = Number(body.until);
        if (Number.isFinite(until) && until > now && until <= now + BAN_MS + 60000) d.banUntil = Math.max(d.banUntil, until);
        result = { ok: true };
        break;
      }
      case "consume": {
        const nonce = typeof body.nonce === "string" && /^[a-f0-9]{32}$/.test(body.nonce) ? body.nonce : "";
        const exp = Number(body.exp) * 1000;
        if (!nonce || !Number.isFinite(exp) || exp <= now || d.used[nonce]) {
          result = { ok: false };
          mutated = false;
        } else {
          d.used[nonce] = exp;
          result = { ok: true };
        }
        break;
      }
      case "global.hit":
        if (!(d.g.r > now)) d.g = { c: 0, r: now + GLOBAL_WINDOW_MS };
        d.g.c += 1;
        result = { ok: d.g.c <= GLOBAL_LIMIT };
        break;
      default:
        return reply({ error: "bad_request" }, 400);
    }
    if (mutated) await this.save(now);
    return reply(result);
  }

  async fetch(request) {
    if (request.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
    let body;
    try {
      body = await request.json();
    } catch (err) {
      return reply({ error: "bad_request" }, 400);
    }
    if (!body || typeof body !== "object") return reply({ error: "bad_request" }, 400);
    const current = this.tail.then(() => this.process(body), () => this.process(body));
    this.tail = current.then(() => undefined, () => undefined);
    return current;
  }

  async alarm() {
    await this.load();
    await this.save(Date.now());
  }
}

const edgeCache = new Map();

function banKey(env, scope, hash) {
  return "ban:" + epochOf(env) + ":" + scope + ":" + hash;
}

async function readBan(env, key) {
  const now = Date.now();
  const cached = edgeCache.get(key);
  if (cached) {
    if (cached.banned && cached.until > now) return true;
    if (!cached.banned && now - cached.checked < EDGE_NEGATIVE_TTL_MS) return false;
  }
  const raw = await env.APP_KV.get(key);
  const until = raw ? Number(raw) : 0;
  const banned = Number.isFinite(until) && until > now;
  if (edgeCache.size >= EDGE_CACHE_MAX) edgeCache.clear();
  edgeCache.set(key, { banned, until: banned ? until : 0, checked: now });
  return banned;
}

export async function isBannedAtEdge(env, id) {
  if (!env.APP_KV || !id || id.error) return false;
  try {
    if (await readBan(env, banKey(env, "ip", id.ipHash))) return true;
    if (id.hasDevice && (await readBan(env, banKey(env, "dev", id.deviceHash)))) return true;
  } catch (err) {
    return false;
  }
  return false;
}

async function applyBan(env, id, until) {
  const ttl = Math.max(60, Math.ceil((until - Date.now()) / 1000) + 60);
  const keys = [banKey(env, "ip", id.ipHash), banKey(env, "dev", id.deviceHash)];
  await Promise.all(keys.map((key) => env.APP_KV.put(key, String(until), { expirationTtl: ttl })));
  for (const key of keys) edgeCache.set(key, { banned: true, until, checked: Date.now() });
  await Promise.all([
    guardCall(env, "i:" + id.ipHash, "ban", { until }),
    guardCall(env, "d:" + id.deviceHash, "ban", { until }),
  ]);
}

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

async function deriveKeys(env, h) {
  const first = await h.readEnvSecret(env.SECOND_STAGE);
  const second = await h.readEnvSecret(env.THIRD_STAGE);
  if (first.length < MIN_FIRST_SECRET || second.length < MIN_SECOND_SECRET) return null;
  const root = await h.hmacHex(first + "\u0000" + second, "catsblock/admin/root/v1");
  return {
    first,
    second,
    sign: await h.hmacHex(root, "sign"),
    pepper: await h.hmacHex(root, "pepper"),
  };
}

function normalizeAnswer(value) {
  return String(value === undefined || value === null ? "" : value)
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .slice(0, 64);
}

function normalizeCode(value) {
  return typeof value === "string" ? value.trim().slice(0, 32) : "";
}

async function matches(h, pepper, provided, expected) {
  const a = await h.hmacHex(pepper, "cmp|" + provided);
  const b = await h.hmacHex(pepper, "cmp|" + expected);
  return h.constantTimeEqual(a, b);
}

async function mintToken(h, key, stage, exp, bind) {
  const nonce = crypto.randomUUID().replace(/-/g, "");
  const payload = stage + "." + exp + "." + nonce;
  const mac = await h.hmacHex(key, payload + "|" + bind);
  return { token: payload + "." + mac, nonce };
}

async function readToken(h, key, token, stage, bind) {
  if (typeof token !== "string" || !TOKEN_PATTERN.test(token)) return null;
  const [kind, exp, nonce, mac] = token.split(".");
  const expected = await h.hmacHex(key, kind + "." + exp + "." + nonce + "|" + bind);
  const valid = h.constantTimeEqual(mac, expected);
  if (!valid || kind !== stage || Number(exp) <= Math.floor(Date.now() / 1000)) return null;
  return { nonce, exp: Number(exp) };
}

async function reserveAttempt(env, id) {
  const names = ["i:" + id.ipHash, "d:" + id.deviceHash];
  const results = await Promise.all(names.map((name) => guardCall(env, name, "reserve")));
  const banned = results.find((r) => r.banned);
  const denied = results.some((r) => r.denied);
  if (banned || denied) {
    await Promise.all(names.filter((_, i) => results[i].ok).map((name) => guardCall(env, name, "release")));
    if (banned) {
      await applyBan(env, id, banned.banUntil);
      return { state: "banned" };
    }
    return { state: "busy" };
  }
  return { state: "ok" };
}

async function releaseAttempt(env, id) {
  await Promise.all([guardCall(env, "i:" + id.ipHash, "release"), guardCall(env, "d:" + id.deviceHash, "release")]);
}

async function failAttempt(env, id) {
  const results = await Promise.all([guardCall(env, "i:" + id.ipHash, "fail"), guardCall(env, "d:" + id.deviceHash, "fail")]);
  const banned = results.find((r) => r.banned);
  if (banned) {
    await applyBan(env, id, banned.banUntil);
    return { banned: true, remaining: 0 };
  }
  return { banned: false, remaining: Math.min(...results.map((r) => r.remaining)) };
}

async function credentialStage(env, id, h, check) {
  const global = await guardCall(env, "g:global", "global.hit");
  if (!global.ok) return h.jsonResponse({ error: "busy" }, 429);
  const reservation = await reserveAttempt(env, id);
  if (reservation.state === "banned") return h.jsonResponse(FORBIDDEN, 403);
  if (reservation.state === "busy") return h.jsonResponse({ error: "busy" }, 429);
  let success = false;
  try {
    success = await check();
  } catch (err) {
    await releaseAttempt(env, id);
    throw err;
  }
  if (success) {
    await releaseAttempt(env, id);
    return null;
  }
  const failure = await failAttempt(env, id);
  if (failure.banned) return h.jsonResponse(FORBIDDEN, 403);
  return h.jsonResponse({ ok: false, error: "invalid", remaining: failure.remaining }, 401);
}

export async function handleAdmin(request, env, path, h) {
  if (request.method !== "POST") return h.jsonResponse({ error: "method_not_allowed" }, 405, { Allow: "POST, OPTIONS" });
  if (!(await h.authorized(request, env))) return h.jsonResponse({ error: "unauthorized" }, 401);
  if ((request.headers.get("X-Client-Platform") || "").trim().toLowerCase() !== "android") {
    return h.jsonResponse({ error: "not_found" }, 404);
  }
  const id = await h.identify(request, env);
  if (id.error || !id.hasDevice) return h.jsonResponse({ error: "bad_request" }, 400);
  const keys = await deriveKeys(env, h);
  if (!keys || !env.APP_KV || !env.ADMIN_GUARD) return h.jsonResponse({ error: "unavailable" }, 503);

  if (path === "/v1/admin/metrics") {
    const header = request.headers.get("Authorization") || "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    const session = await readToken(h, keys.sign, token, "a2", id.deviceHash);
    if (!session) return h.jsonResponse({ error: "session_expired" }, 401);
    const body = (await readBody(request)) || {};
    const days = Number.isInteger(body.days) ? Math.min(60, Math.max(1, body.days)) : 30;
    const snapshot = await metricsCall(env, "snapshot", { days });
    if (!snapshot) return h.jsonResponse({ error: "unavailable" }, 503);
    return h.jsonResponse(Object.assign({ ok: true }, snapshot), 200);
  }

  const body = await readBody(request);
  if (!body) return h.jsonResponse({ error: "bad_request" }, 400);

  if (path === "/v1/admin/stage1") {
    const code = normalizeCode(body.code);
    const rejection = await credentialStage(env, id, h, async () => {
      const formatOk = CODE_PATTERN.test(code);
      const equal = await matches(h, keys.pepper, code, keys.first);
      return formatOk && equal;
    });
    if (rejection) return rejection;
    const exp = Math.floor(Date.now() / 1000) + STAGE_TTL_S;
    const minted = await mintToken(h, keys.sign, "s1", exp, id.ipHash + "|" + id.deviceHash);
    return h.jsonResponse({ ok: true, token: minted.token, expiresIn: STAGE_TTL_S }, 200);
  }

  const stage = await readToken(h, keys.sign, body.token, "s1", id.ipHash + "|" + id.deviceHash);
  if (!stage) return h.jsonResponse({ error: "stage_expired" }, 401);
  const answer = normalizeAnswer(body.answer);
  const rejection = await credentialStage(env, id, h, async () => {
    const equal = await matches(h, keys.pepper, answer, normalizeAnswer(keys.second));
    return equal && answer.length > 0;
  });
  if (rejection) return rejection;
  const consumed = await guardCall(env, "d:" + id.deviceHash, "consume", { nonce: stage.nonce, exp: stage.exp });
  if (!consumed.ok) return h.jsonResponse({ error: "stage_expired" }, 401);
  const exp = Math.floor(Date.now() / 1000) + SESSION_TTL_S;
  const minted = await mintToken(h, keys.sign, "a2", exp, id.deviceHash);
  return h.jsonResponse({ ok: true, token: minted.token, expiresIn: SESSION_TTL_S }, 200);
}
