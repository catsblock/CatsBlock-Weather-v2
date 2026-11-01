export const SHORT_WINDOW_MS = 10 * 60 * 1000;
export const LONG_WINDOW_MS = 12 * 60 * 60 * 1000;
export const LOCKOUT_MS = 6 * 60 * 60 * 1000;
export const SHORT_LIMIT = 20;
export const LONG_LIMIT = 150;
export const IP_CEILING_LIMIT = 400;
export const IP_CEILING_WINDOW_MS = 10 * 60 * 1000;
export const AGENT_RATE_LIMIT = 5;
export const AGENT_WINDOW_MS = 24 * 60 * 60 * 1000;

const KEY_PATTERN = /^[a-f0-9]{64}$/;
const SAFETY_MARGIN_S = 5;
const WINDOW_FIELDS = ["s", "l", "ip", "ag"];

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
  return next;
}

function expiry(data) {
  let latest = 0;
  for (const field of WINDOW_FIELDS) {
    if (data[field] && data[field].r > latest) latest = data[field].r;
  }
  if (typeof data.u === "number" && data.u > latest) latest = data.u;
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

function agentReserve(data, now) {
  const window = freshWindow(data.ag, now, AGENT_WINDOW_MS);
  if (window.c >= AGENT_RATE_LIMIT) return { ok: false, resetSeconds: Math.ceil((window.r - now) / 1000) };
  window.c += 1;
  data.ag = window;
  return { ok: true, used: window.c, resetSeconds: Math.ceil((window.r - now) / 1000) };
}

function agentRefund(data, now) {
  const live = activeWindow(data.ag, now);
  if (!live) return { ok: true };
  live.c = Math.max(0, live.c - 1);
  data.ag = live;
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
      case "agent.reserve":
        result = agentReserve(this.data, now);
        break;
      case "agent.refund":
        result = agentRefund(this.data, now);
        break;
      default:
        return reply({ error: "bad_request" }, 400);
    }
    if (mutated) await this.persist(now);
    return reply(result);
  }
}

export async function limiterCall(env, hash, op) {
  if (!env.RATE_LIMITER) throw new Error("limiter_missing");
  const stub = env.RATE_LIMITER.get(env.RATE_LIMITER.idFromName(hash));
  const response = await stub.fetch("https://rate-limiter.internal/" + op, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ key: hash, op }),
  });
  if (!response.ok) throw new Error("limiter_http");
  return response.json();
}
