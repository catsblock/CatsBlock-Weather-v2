export const SHORT_WINDOW_MS = 10 * 60 * 1000;
export const LONG_WINDOW_MS = 12 * 60 * 60 * 1000;
export const LOCKOUT_MS = 6 * 60 * 60 * 1000;
export const SHORT_LIMIT = 20;
export const LONG_LIMIT = 150;
export const IP_CEILING_LIMIT = 400;
export const IP_CEILING_WINDOW_MS = 10 * 60 * 1000;
export const AGENT_TOKEN_LIMIT = 10000;
export const AGENT_IP_TOKEN_LIMIT = 50000;
export const BURST_WINDOW_MS = 10 * 1000;
export const BURST_LIMIT = 6;
export const BURST_LOCK_MS = 60 * 1000;
export const IP_SHARED_LIMIT = 4000;
export const IP_SHARED_WINDOW_MS = 10 * 60 * 1000;
export const AGENT_WINDOW_MS = 5 * 60 * 60 * 1000;
export const SPAM_WINDOW_MS = 15 * 60 * 1000;
export const SPAM_LIMIT = 20;
export const SPAM_LOCK_MS = 30 * 60 * 1000;
export const IP_SPAM_LIMIT = 40;
export const IP_SPAM_LOCK_MS = 60 * 60 * 1000;
export const AGENT_CONCURRENCY = 3;
export const AGENT_LEASE_MS = 120 * 1000;
export const AGENT_RESERVATION_MS = 150 * 1000;

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
  for (const field of ["sp", "spi"]) {
    if (!Array.isArray(data[field])) continue;
    const hits = data[field].filter((t) => typeof t === "number" && t > now - SPAM_WINDOW_MS);
    if (hits.length) next[field] = hits;
  }
  for (const field of ["bli"]) {
    if (typeof data[field] === "number" && data[field] > now) next[field] = data[field];
  }
  if (Array.isArray(data.ar)) {
    const reservations = data.ar.filter((item) =>
      item && typeof item.id === "string" && typeof item.amount === "number" && Number.isInteger(item.amount) && item.amount > 0 && typeof item.r === "number" && item.r > now
    );
    if (reservations.length) next.ar = reservations;
  }
  if (Array.isArray(data.ls)) {
    const leases = data.ls.filter((lease) =>
      (typeof lease === "number" && lease > now) ||
      (lease && typeof lease === "object" && typeof lease.id === "string" && typeof lease.r === "number" && lease.r > now)
    );
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
  for (const field of ["bl", "bli"]) {
    if (typeof data[field] === "number" && data[field] > latest) latest = data[field];
  }
  for (const field of ["sp", "spi"]) {
    if (Array.isArray(data[field]) && data[field].length) {
      const last = Math.max(...data[field]) + SPAM_WINDOW_MS;
      if (last > latest) latest = last;
    }
  }
  if (Array.isArray(data.ar)) {
    for (const item of data.ar) {
      if (item && typeof item.r === "number" && item.r > latest) latest = item.r;
    }
  }
  if (Array.isArray(data.ls)) {
    for (const lease of data.ls) {
      const expires = typeof lease === "number" ? lease : lease && typeof lease.r === "number" ? lease.r : 0;
      if (expires > latest) latest = expires;
    }
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

function reservationTotal(data, now) {
  return (Array.isArray(data.ar) ? data.ar : []).reduce((total, item) =>
    item && typeof item.r === "number" && item.r > now && Number.isInteger(item.amount) && item.amount > 0 ? total + item.amount : total,
    0
  );
}

function agentPeek(data, now) {
  const live = activeWindow(data.ag, now);
  const used = live ? live.c : 0;
  const active = (Array.isArray(data.ar) ? data.ar : []).filter((item) => item && typeof item.r === "number" && item.r > now);
  const reserved = reservationTotal(data, now);
  const reservationResetSeconds = active.length
    ? Math.max(1, Math.ceil((Math.min(...active.map((item) => item.r)) - now) / 1000))
    : 0;
  return {
    used,
    reserved,
    resetSeconds: live ? Math.ceil((live.r - now) / 1000) : 0,
    reservationResetSeconds,
  };
}

function agentReserve(data, now, reservationId, tokens, limit) {
  if (typeof reservationId !== "string" || !/^[a-f0-9-]{36}$/i.test(reservationId)) return { ok: false, invalid: true };
  if (!Number.isInteger(tokens) || tokens < 1 || tokens > AGENT_TOKEN_LIMIT) return { ok: false, invalid: true };
  if (!Number.isInteger(limit) || limit < tokens || limit > AGENT_IP_TOKEN_LIMIT) return { ok: false, invalid: true };
  const existing = (Array.isArray(data.ar) ? data.ar : []).find((item) => item && item.id === reservationId);
  if (existing) {
    if (existing.amount !== tokens) return { ok: false, invalid: true };
    const state = agentPeek(data, now);
    return Object.assign({ ok: true }, state, { remaining: Math.max(0, limit - state.used - state.reserved) });
  }
  const window = freshWindow(data.ag, now, AGENT_WINDOW_MS);
  const reserved = reservationTotal(data, now);
  if (window.c + reserved + tokens > limit) {
    const active = (Array.isArray(data.ar) ? data.ar : []).filter((item) => item && typeof item.r === "number" && item.r > now);
    const retryAt = active.length ? Math.min(...active.map((item) => item.r)) : window.r;
    const retry = Math.max(1, Math.ceil((retryAt - now) / 1000));
    return { ok: false, used: window.c, reserved, remaining: Math.max(0, limit - window.c - reserved), resetSeconds: retry };
  }
  if (!Array.isArray(data.ar)) data.ar = [];
  data.ar.push({ id: reservationId, amount: tokens, r: now + AGENT_RESERVATION_MS });
  data.ag = window;
  return { ok: true, used: window.c, reserved: reserved + tokens, remaining: Math.max(0, limit - window.c - reserved - tokens), resetSeconds: Math.ceil((window.r - now) / 1000) };
}

function agentSettle(data, now, reservationId, tokens) {
  if (typeof reservationId !== "string" || !/^[a-f0-9-]{36}$/i.test(reservationId)) return { ok: false, invalid: true };
  if (!Number.isInteger(tokens) || tokens < 0 || tokens > AGENT_IP_TOKEN_LIMIT) return { ok: false, invalid: true };
  const reservations = Array.isArray(data.ar) ? data.ar : [];
  const index = reservations.findIndex((item) => item && item.id === reservationId && item.r > now);
  if (index < 0) return { ok: false, missing: true };
  reservations.splice(index, 1);
  data.ar = reservations;
  const window = freshWindow(data.ag, now, AGENT_WINDOW_MS);
  window.c += tokens;
  data.ag = window;
  return Object.assign({ ok: true }, agentPeek(data, now));
}

function agentCancel(data, now, reservationId) {
  if (typeof reservationId !== "string" || !/^[a-f0-9-]{36}$/i.test(reservationId)) return { ok: false, invalid: true };
  data.ar = (Array.isArray(data.ar) ? data.ar : []).filter((item) => !item || item.id !== reservationId);
  return Object.assign({ ok: true }, agentPeek(data, now));
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

function agentBurst(data, now, scope) {
  const ipScope = scope === "ip";
  const hitsField = ipScope ? "spi" : "sp";
  const lockField = ipScope ? "bli" : "bl";
  const limit = ipScope ? IP_SPAM_LIMIT : SPAM_LIMIT;
  const lockMs = ipScope ? IP_SPAM_LOCK_MS : SPAM_LOCK_MS;
  if (typeof data[lockField] === "number" && data[lockField] > now) {
    return { ok: false, lockSeconds: Math.ceil((data[lockField] - now) / 1000) };
  }
  const hits = (Array.isArray(data[hitsField]) ? data[hitsField] : []).filter((t) => typeof t === "number" && t > now - SPAM_WINDOW_MS);
  hits.push(now);
  data[hitsField] = hits;
  if (hits.length >= limit) {
    data[lockField] = now + lockMs;
    data[hitsField] = [];
    return { ok: false, lockSeconds: Math.ceil(lockMs / 1000) };
  }
  return { ok: true, lockSeconds: 0 };
}

function acquireSlot(data, now) {
  const live = (Array.isArray(data.ls) ? data.ls : []).filter((lease) =>
    (typeof lease === "number" && lease > now) ||
    (lease && typeof lease === "object" && typeof lease.id === "string" && typeof lease.r === "number" && lease.r > now)
  );
  if (live.length < AGENT_CONCURRENCY) {
    const id = crypto.randomUUID();
    live.push({ id, r: now + AGENT_LEASE_MS });
    data.ls = live;
    return { ok: true, active: live.length, leaseId: id };
  }
  data.ls = live;
  return { ok: false, active: live.length };
}

function releaseSlot(data, now, leaseId) {
  const live = (Array.isArray(data.ls) ? data.ls : []).filter((lease) =>
    (typeof lease === "number" && lease > now) ||
    (lease && typeof lease === "object" && typeof lease.id === "string" && typeof lease.r === "number" && lease.r > now)
  );
  if (typeof leaseId === "string" && leaseId.length > 0) {
    const index = live.findIndex((lease) => lease && typeof lease === "object" && lease.id === leaseId);
    if (index >= 0) live.splice(index, 1);
  }
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
    this.tail = Promise.resolve();
  }

  async load(hash) {
    const key = "rl:" + hash;
    let stored = null;
    stored = await this.state.storage.get("d");
    if (!stored && this.kv) {
      const raw = await this.kv.get(key);
      try {
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

  async process(body) {
    if (!body || typeof body.key !== "string" || !KEY_PATTERN.test(body.key)) return reply({ error: "bad_request" }, 400);
    if (this.data === null) {
      await this.load(body.key);
    }
    if (!this.data || this.data.k !== "rl:" + body.key) return reply({ error: "bad_request" }, 400);
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
        result = agentReserve(this.data, now, body.reservationId, body.tokens, body.limit);
        break;
      case "agent.settle":
        result = agentSettle(this.data, now, body.reservationId, body.tokens);
        break;
      case "agent.cancel":
        result = agentCancel(this.data, now, body.reservationId);
        break;
      case "agent.charge":
        result = agentCharge(this.data, now, clampTokens(body.tokens));
        break;
      case "agent.burst":
        result = agentBurst(this.data, now, body.scope);
        break;
      case "queue.enter":
        result = acquireSlot(this.data, now);
        break;
      case "queue.leave":
        result = releaseSlot(this.data, now, body.leaseId);
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

  enqueue(task) {
    const current = this.tail.then(task, task);
    this.tail = current.then(() => undefined, () => undefined);
    return current;
  }

  async fetch(request) {
    if (request.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
    let body;
    try {
      body = await request.json();
    } catch (err) {
      return reply({ error: "bad_request" }, 400);
    }
    return this.enqueue(() => this.process(body));
  }

  async alarm() {
    return this.enqueue(async () => {
      const stored = await this.state.storage.get("d");
      if (!stored) return;
      this.data = prune(stored, Date.now());
      await this.persist(Date.now());
    });
  }

}

export async function limiterCall(env, hash, op, extra) {
  if (!env.RATE_LIMITER) throw new Error("limiter_missing");
  if (typeof hash !== "string" || !KEY_PATTERN.test(hash)) throw new Error("limiter_key_invalid");
  const stub = env.RATE_LIMITER.get(env.RATE_LIMITER.idFromName(hash));
  const response = await stub.fetch("https://rate-limiter.internal/" + op, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(Object.assign({}, extra || {}, { key: hash, op })),
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error("limiter_http");
  return response.json();
}
