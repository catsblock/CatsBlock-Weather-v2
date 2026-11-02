export const SHORT_WINDOW_MS = 10 * 60 * 1000;
export const LONG_WINDOW_MS = 12 * 60 * 60 * 1000;
export const LOCKOUT_MS = 6 * 60 * 60 * 1000;
export const SHORT_LIMIT = 20;
export const LONG_LIMIT = 150;
export const IP_CEILING_LIMIT = 400;
export const IP_CEILING_WINDOW_MS = 10 * 60 * 1000;
export const AGENT_TOKEN_LIMIT = 2500;
export const AGENT_IP_TOKEN_LIMIT = 50000;
export const BURST_WINDOW_MS = 10 * 1000;
export const BURST_LIMIT = 6;
export const BURST_LOCK_MS = 60 * 1000;
export const IP_SHARED_LIMIT = 4000;
export const IP_SHARED_WINDOW_MS = 10 * 60 * 1000;
export const AGENT_WINDOW_MS = 24 * 60 * 60 * 1000;
export const SPAM_WINDOW_MS = 15 * 60 * 1000;
export const SPAM_LIMIT = 30;
export const SPAM_LOCK_MS = 30 * 60 * 1000;
export const AGENT_CONCURRENCY = 3;
export const AGENT_LEASE_MS = 60 * 1000;

const KEY_PATTERN = /^[a-f0-9]{64}$/;
const SAFETY_MARGIN_S = 5;
const WINDOW_FIELDS = ["s", "l", "ip", "ag", "sh", "bu"];

function freshWindow(entry, now, windowMs) {
  if (!entry || typeof entry.r !== "number" || entry.r <= now) return { c: 0, r: now + windowMs };
  return { c: Number(entry.c) || 0, r: entry.r };
}

function activeWindow(entry, now) {
  return entry && typeof entry.r === "number" && entry.r > now ? { c: Number(entry.c) || 0, r: entry.r } : null;
}

function prune(data, now) {
  const next = { k: data.k };
  for (const field of WINDOW_FIELDS) {
    const live = activeWindow(data[field], now);
    if (live) next[field] = live;
  }
  if (typeof data.u === "number" && data.u > now) next.u = data.u;
  if (typeof data.bl === "number" && data.bl > now) next.bl = data.bl;
  if (Array.isArray(data.sp)) {
    const hits = data.sp.filter((t) => typeof t === "number" && t > now - SPAM_WINDOW_MS);
    if (hits.length) next.sp = hits;
  }
  if (Array.isArray(data.ls)) {
    const leases = data.ls.filter((t) => typeof t === "number" && t > now);
    if (leases.length) next.ls = leases;
  }
  return next;
}

function expiry(data) {
  let latest = 0;
  for (const field of WINDOW_FIELDS) {
    if (data[field] && data[field].r > latest) latest = data[field].r;
  }
  if (typeof data.u === "number" && data.u > latest) latest = data.u;
  if (typeof data.bl === "number" && data.bl > latest) latest = data.bl;
  if (Array.isArray(data.sp) && data.sp.length) {
    const last = Math.max(...data.sp) + SPAM_WINDOW_MS;
    if (last > latest) latest = last;
  }
  if (Array.isArray(data.ls)) {
    for (const t of data.ls) if (typeof t === "number" && t > latest) latest = t;
  }
  return latest;
}

function proxyState(data, now) {
  const short = freshWindow(data.s, now, SHORT_WINDOW_MS);
  const long = freshWindow(data.l, now, LONG_WINDOW_MS);
  return {
    shortRemaining: Math.max(0, SHORT_LIMIT - short.c),
    shortLimit: SHORT_LIMIT,
    longRemaining: Math.max(0, LONG_LIMIT - long.c),
    longLimit: LONG_LIMIT,
    lockoutSeconds: data.u && data.u > now ? Math.ceil((data.u - now) / 1000) : 0,
  };
}

function consumeProxy(data, now) {
  const ceiling = freshWindow(data.ip, now, IP_CEILING_WINDOW_MS);
  ceiling.c += 1;
  if (IP_CEILING_LIMIT && ceiling.c > IP_CEILING_LIMIT) return { ceilingExceeded: true };
  data.ip = ceiling;

  if (data.u && data.u > now) {
    return { ceilingExceeded: false, state: { allowed: false, lockoutSeconds: Math.ceil((data.u - now) / 1000) } };
  }
  const short = freshWindow(data.s, now, SHORT_WINDOW_MS);
  const long = freshWindow(data.l, now, LONG_WINDOW_MS);
  short.c += 1;
  long.c += 1;
  data.s = short;
  data.l = long;
  if (short.c > SHORT_LIMIT || long.c > LONG_LIMIT) {
    data.u = now + LOCKOUT_MS;
    return { ceilingExceeded: false, state: { allowed: false, lockoutSeconds: Math.ceil(LOCKOUT_MS / 1000) } };
  }
  return {
    ceilingExceeded: false,
    state: Object.assign({ allowed: true }, proxyState(data, now)),
  };
}

function agentPeek(data, now) {
  const live = activeWindow(data.ag, now);
  return live ? { used: live.c, resetSeconds: Math.ceil((live.r - now) / 1000) } : { used: 0, resetSeconds: 0 };
}

function sharedGuard(data, now) {
  const window = freshWindow(data.sh, now, IP_SHARED_WINDOW_MS);
  window.c += 1;
  if (window.c > IP_SHARED_LIMIT) return { exceeded: true };
  data.sh = window;
  return { exceeded: false };
}

function clampTokens(value) {
  const n = Math.floor(Number(value));
  return Number.isFinite(n) && n >= 0 && n <= 20000 ? n : 0;
}

function agentCharge(data, now, tokens) {
  const window = freshWindow(data.ag, now, AGENT_WINDOW_MS);
  window.c += tokens;
  data.ag = window;
  return { ok: true, used: window.c, resetSeconds: Math.ceil((window.r - now) / 1000) };
}

function agentBurst(data, now) {
  if (typeof data.bl === "number" && data.bl > now) {
    return { ok: false, lockSeconds: Math.ceil((data.bl - now) / 1000) };
  }
  const hits = (Array.isArray(data.sp) ? data.sp : []).filter((t) => typeof t === "number" && t > now - SPAM_WINDOW_MS);
  hits.push(now);
  data.sp = hits;
  if (hits.length >= SPAM_LIMIT) {
    data.bl = now + SPAM_LOCK_MS;
    data.sp = [];
    return { ok: false, lockSeconds: Math.ceil(SPAM_LOCK_MS / 1000) };
  }
  return { ok: true, lockSeconds: 0 };
}

function acquireSlot(data, now) {
  const live = (Array.isArray(data.ls) ? data.ls : []).filter((t) => typeof t === "number" && t > now);
  if (live.length < AGENT_CONCURRENCY) {
    live.push(now + AGENT_LEASE_MS);
    data.ls = live;
    return { ok: true, active: live.length };
  }
  data.ls = live;
  return { ok: false, active: live.length };
}

function releaseSlot(data, now) {
  const live = (Array.isArray(data.ls) ? data.ls : []).filter((t) => typeof t === "number" && t > now);
  live.sort((a, b) => a - b);
  live.shift();
  data.ls = live;
  return { ok: true };
}

function reply(payload, status) {
  return new Response(JSON.stringify(payload), {
    status: status || 200,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

export class RateLimiter {
  constructor(state, env) {
    this.state = state;
    this.kv = env.APP_KV;
    this.data = null;
  }

  async load(hash) {
    const key = "rl:" + hash;
    let stored = null;
    try {
      stored = await this.state.storage.get("d");
    } catch (err) {
      stored = null;
    }
    if (!stored && this.kv) {
      try {
        const raw = await this.kv.get(key);
        stored = raw ? JSON.parse(raw) : null;
      } catch (err) {
        stored = null;
      }
    }
    const base = stored && typeof stored === "object" ? stored : {};
    this.data = prune(Object.assign({}, base, { k: key }), Date.now());
  }

  async persist(now) {
    const live = prune(this.data, now);
    const until = expiry(live);
    this.data = live;
    if (until <= now) {
      await this.clear();
      return;
    }
    const ttl = Math.max(60, Math.ceil((until - now) / 1000) + SAFETY_MARGIN_S);
    await this.state.storage.put("d", live);
    await this.state.storage.setAlarm(until + SAFETY_MARGIN_S * 1000);
    if (this.kv) {
      try {
        await this.kv.put(live.k, JSON.stringify(live), { expirationTtl: ttl });
      } catch (err) {
        return;
      }
    }
  }

  async clear() {
    const key = this.data && this.data.k;
    await this.state.storage.deleteAll();
    if (key && this.kv) {
      try {
        await this.kv.delete(key);
      } catch (err) {
        return;
      }
    }
  }

  async alarm() {
    const stored = await this.state.storage.get("d");
    if (!stored) return;
    this.data = prune(stored, Date.now());
    await this.persist(Date.now());
  }

  async fetch(request) {
    let body;
    try {
      body = await request.json();
    } catch (err) {
      return reply({ error: "bad_request" }, 400);
    }
    if (!body || typeof body.key !== "string" || !KEY_PATTERN.test(body.key)) return reply({ error: "bad_request" }, 400);
    if (this.data === null) {
      await this.state.blockConcurrencyWhile(() => this.load(body.key));
    }
    const now = Date.now();
    this.data = prune(this.data, now);
    let result;
    let mutated = true;
    switch (body.op) {
      case "peek":
        result = proxyState(this.data, now);
        mutated = false;
        break;
      case "proxy":
        result = consumeProxy(this.data, now);
        break;
      case "agent.peek":
        result = agentPeek(this.data, now);
        mutated = false;
        break;
      case "agent.charge":
        result = agentCharge(this.data, now, clampTokens(body.tokens));
        break;
      case "agent.burst":
        result = agentBurst(this.data, now);
        break;
      case "queue.enter":
        result = acquireSlot(this.data, now);
        break;
      case "queue.leave":
        result = releaseSlot(this.data, now);
        break;
      case "ip.guard":
        result = sharedGuard(this.data, now);
        break;
      default:
        return reply({ error: "bad_request" }, 400);
    }
    if (mutated) await this.persist(now);
    return reply(result);
  }
}

export async function limiterCall(env, hash, op, extra) {
  if (!env.RATE_LIMITER) throw new Error("limiter_missing");
  const stub = env.RATE_LIMITER.get(env.RATE_LIMITER.idFromName(hash));
  const response = await stub.fetch("https://rate-limiter.internal/" + op, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(Object.assign({}, extra || {}, { key: hash, op })),
  });
  if (!response.ok) throw new Error("limiter_http");
  return response.json();
}
