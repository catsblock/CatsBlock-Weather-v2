const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;
const ACTIVE_WINDOW_MS = 5 * 60 * 1000;
const FLUSH_MS = 30 * 1000;
const PRUNE_EVERY_MS = 10 * 60 * 1000;
const HOURLY_RETENTION_HOURS = 24 * 31;
const DAILY_RETENTION_DAYS = 62;
const HISTORY_DAYS = 30;
const HOURLY_POINTS = 48;
const UID_PATTERN = /^[a-f0-9]{64}$/;
const CATEGORIES = ["weather", "visual", "openweather", "agent", "status", "other"];

export const DAILY_REQUEST_LIMIT = 100000;

function reply(payload, status) {
  return new Response(JSON.stringify(payload), {
    status: status || 200,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

async function shortDigest(text) {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buffer).slice(0, 12))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function dayLabel(day) {
  return new Date(day * DAY_MS).toISOString().slice(0, 10);
}

function hourLabel(hour) {
  return new Date(hour * HOUR_MS).toISOString().slice(0, 13) + ":00Z";
}

function emptyCounts() {
  const counts = { errors: 0 };
  for (const name of CATEGORIES) counts[name] = 0;
  return counts;
}

function totalOf(row) {
  let total = 0;
  for (const name of CATEGORIES) total += Number(row[name]) || 0;
  return total;
}

export class MetricsHub {
  constructor(state) {
    this.state = state;
    this.sql = state.storage.sql;
    this.hourly = new Map();
    this.dayRequests = new Map();
    this.hourUsers = [];
    this.dayUsers = [];
    this.seenHours = new Set();
    this.seenHour = -1;
    this.active = new Map();
    this.dirtyActive = new Set();
    this.scheduled = false;
    this.lastPrune = 0;
    this.tail = Promise.resolve();
    state.blockConcurrencyWhile(async () => {
      this.init();
    });
  }

  init() {
    const columns = CATEGORIES.map((name) => name + " INTEGER NOT NULL DEFAULT 0").join(", ");
    this.sql.exec("CREATE TABLE IF NOT EXISTS hourly (hour INTEGER PRIMARY KEY, " + columns + ", errors INTEGER NOT NULL DEFAULT 0)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS daily (day INTEGER PRIMARY KEY, requests INTEGER NOT NULL DEFAULT 0, users INTEGER NOT NULL DEFAULT 0)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS hour_users (hour INTEGER NOT NULL, uid TEXT NOT NULL, PRIMARY KEY (hour, uid))");
    this.sql.exec("CREATE TABLE IF NOT EXISTS day_users (day INTEGER NOT NULL, uid TEXT NOT NULL, PRIMARY KEY (day, uid))");
    this.sql.exec("CREATE TABLE IF NOT EXISTS active (uid TEXT PRIMARY KEY, seen INTEGER NOT NULL)");
    const cutoff = Date.now() - ACTIVE_WINDOW_MS;
    for (const row of this.sql.exec("SELECT uid, seen FROM active WHERE seen > ?", cutoff)) {
      this.active.set(row.uid, row.seen);
    }
  }

  async hit(body, now) {
    if (typeof body.uid !== "string" || !UID_PATTERN.test(body.uid)) return { ok: false };
    const category = CATEGORIES.includes(body.cat) ? body.cat : "other";
    const hour = Math.floor(now / HOUR_MS);
    const day = Math.floor(now / DAY_MS);
    let counts = this.hourly.get(hour);
    if (!counts) {
      counts = emptyCounts();
      this.hourly.set(hour, counts);
    }
    counts[category] += 1;
    if (body.error === true) counts.errors += 1;
    this.dayRequests.set(day, (this.dayRequests.get(day) || 0) + 1);
    this.active.set(body.uid, now);
    this.dirtyActive.add(body.uid);
    if (this.seenHour !== hour) {
      this.seenHours.clear();
      this.seenHour = hour;
    }
    if (!this.seenHours.has(body.uid)) {
      this.seenHours.add(body.uid);
      this.hourUsers.push([hour, body.uid]);
      this.dayUsers.push([day, await shortDigest(body.uid + "|" + day)]);
    }
    this.schedule(now);
    return { ok: true };
  }

  schedule(now) {
    if (this.scheduled) return;
    this.scheduled = true;
    this.state.storage.setAlarm(now + FLUSH_MS).catch(() => {
      this.scheduled = false;
    });
  }

  flush(now) {
    const hourly = this.hourly;
    const dayRequests = this.dayRequests;
    const hourUsers = this.hourUsers;
    const dayUsers = this.dayUsers;
    const dirty = Array.from(this.dirtyActive);
    this.hourly = new Map();
    this.dayRequests = new Map();
    this.hourUsers = [];
    this.dayUsers = [];
    this.dirtyActive = new Set();
    try {
      this.state.storage.transactionSync(() => {
        const names = CATEGORIES.concat(["errors"]);
        const insert = "INSERT INTO hourly (hour, " + names.join(", ") + ") VALUES (?" + ", ?".repeat(names.length) + ") ON CONFLICT(hour) DO UPDATE SET " +
          names.map((name) => name + " = " + name + " + excluded." + name).join(", ");
        for (const [hour, counts] of hourly) {
          this.sql.exec(insert, hour, ...names.map((name) => counts[name]));
        }
        for (const [day, total] of dayRequests) {
          this.sql.exec("INSERT INTO daily (day, requests, users) VALUES (?, ?, 0) ON CONFLICT(day) DO UPDATE SET requests = requests + excluded.requests", day, total);
        }
        for (const [hour, uid] of hourUsers) {
          this.sql.exec("INSERT OR IGNORE INTO hour_users (hour, uid) VALUES (?, ?)", hour, uid);
        }
        const touchedDays = new Set();
        for (const [day, uid] of dayUsers) {
          this.sql.exec("INSERT OR IGNORE INTO day_users (day, uid) VALUES (?, ?)", day, uid);
          touchedDays.add(day);
        }
        for (const day of touchedDays) {
          this.sql.exec("INSERT INTO daily (day, requests, users) VALUES (?, 0, 0) ON CONFLICT(day) DO NOTHING", day);
          this.sql.exec("UPDATE daily SET users = (SELECT COUNT(*) FROM day_users WHERE day = ?) WHERE day = ?", day, day);
        }
        for (const uid of dirty) {
          const seen = this.active.get(uid);
          if (seen) this.sql.exec("INSERT INTO active (uid, seen) VALUES (?, ?) ON CONFLICT(uid) DO UPDATE SET seen = excluded.seen", uid, seen);
        }
      });
    } catch (err) {
      for (const [hour, counts] of hourly) {
        const merged = this.hourly.get(hour) || emptyCounts();
        for (const name of Object.keys(counts)) merged[name] = (merged[name] || 0) + counts[name];
        this.hourly.set(hour, merged);
      }
      for (const [day, total] of dayRequests) this.dayRequests.set(day, (this.dayRequests.get(day) || 0) + total);
      this.hourUsers = hourUsers.concat(this.hourUsers);
      this.dayUsers = dayUsers.concat(this.dayUsers);
      for (const uid of dirty) this.dirtyActive.add(uid);
      throw err;
    }
    for (const [uid, seen] of this.active) {
      if (seen <= now - ACTIVE_WINDOW_MS) this.active.delete(uid);
    }
    if (now - this.lastPrune >= PRUNE_EVERY_MS) {
      this.lastPrune = now;
      this.prune(now);
    }
  }

  prune(now) {
    const hour = Math.floor(now / HOUR_MS);
    const day = Math.floor(now / DAY_MS);
    this.sql.exec("DELETE FROM hour_users WHERE hour < ?", hour - 25);
    this.sql.exec("DELETE FROM day_users WHERE day < ?", day - 1);
    this.sql.exec("DELETE FROM hourly WHERE hour < ?", hour - HOURLY_RETENTION_HOURS);
    this.sql.exec("DELETE FROM daily WHERE day < ?", day - DAILY_RETENTION_DAYS);
    this.sql.exec("DELETE FROM active WHERE seen < ?", now - 2 * ACTIVE_WINDOW_MS);
  }

  snapshot(daysInput, now) {
    this.flush(now);
    const days = Math.min(60, Math.max(1, Math.floor(Number(daysInput)) || 30));
    const day = Math.floor(now / DAY_MS);
    const hour = Math.floor(now / HOUR_MS);

    let activeNow = 0;
    for (const seen of this.active.values()) {
      if (seen > now - ACTIVE_WINDOW_MS) activeNow += 1;
    }
    const unique24h = this.sql.exec("SELECT COUNT(DISTINCT uid) AS n FROM hour_users WHERE hour >= ?", hour - 23).one().n;

    const userRows = new Map();
    for (const row of this.sql.exec("SELECT day, users, requests FROM daily WHERE day >= ?", day - 59)) userRows.set(row.day, row);
    const users = [];
    for (let d = day - days + 1; d <= day; d++) {
      users.push({ day: dayLabel(d), value: userRows.has(d) ? userRows.get(d).users : 0 });
    }

    const hourlyRows = new Map();
    for (const row of this.sql.exec("SELECT * FROM hourly WHERE hour >= ?", hour - HOURLY_RETENTION_HOURS)) hourlyRows.set(row.hour, row);

    const todayStart = day * 24;
    const byCategory = emptyCounts();
    for (const [h, row] of hourlyRows) {
      if (h < todayStart) continue;
      for (const name of CATEGORIES.concat(["errors"])) byCategory[name] += Number(row[name]) || 0;
    }

    const daily = [];
    for (let d = day - HISTORY_DAYS + 1; d <= day; d++) {
      const entry = { day: dayLabel(d), total: 0, errors: 0 };
      for (const name of CATEGORIES) entry[name] = 0;
      daily.push(entry);
    }
    const dailyIndex = new Map(daily.map((entry, i) => [i + day - HISTORY_DAYS + 1, entry]));
    for (const [h, row] of hourlyRows) {
      const entry = dailyIndex.get(Math.floor(h / 24));
      if (!entry) continue;
      entry.total += totalOf(row);
      entry.errors += Number(row.errors) || 0;
      for (const name of CATEGORIES) entry[name] += Number(row[name]) || 0;
    }

    const hourlySeries = [];
    for (let h = hour - HOURLY_POINTS + 1; h <= hour; h++) {
      const row = hourlyRows.get(h) || emptyCounts();
      const entry = { hour: hourLabel(h), total: totalOf(row), errors: Number(row.errors) || 0 };
      for (const name of CATEGORIES) entry[name] = Number(row[name]) || 0;
      hourlySeries.push(entry);
    }

    const todayTotal = userRows.has(day) ? userRows.get(day).requests : 0;
    return {
      generatedAt: new Date(now).toISOString(),
      active: { now: activeNow, windowMinutes: ACTIVE_WINDOW_MS / 60000 },
      unique24h,
      usage: { today: todayTotal, limit: DAILY_REQUEST_LIMIT, byCategory, resetsAt: new Date((day + 1) * DAY_MS).toISOString() },
      users: { days, series: users },
      requests: { daily, hourly: hourlySeries },
    };
  }

  async process(body) {
    const now = Date.now();
    switch (body.op) {
      case "hit":
        return reply(await this.hit(body, now));
      case "snapshot":
        return reply(this.snapshot(body.days, now));
      default:
        return reply({ error: "bad_request" }, 400);
    }
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
    try {
      return await current;
    } catch (err) {
      return reply({ error: "internal_error" }, 500);
    }
  }

  async alarm() {
    this.scheduled = false;
    try {
      this.flush(Date.now());
    } catch (err) {
      this.schedule(Date.now());
      return;
    }
    if (this.hourly.size || this.dayRequests.size || this.hourUsers.length || this.dirtyActive.size) this.schedule(Date.now());
  }
}

export async function metricsCall(env, op, extra) {
  if (!env.METRICS_HUB) return null;
  try {
    const stub = env.METRICS_HUB.get(env.METRICS_HUB.idFromName("metrics:v1"));
    const response = await stub.fetch("https://metrics.internal/" + op, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(Object.assign({}, extra || {}, { op })),
      signal: AbortSignal.timeout(8000),
    });
    return response.ok ? await response.json() : null;
  } catch (err) {
    return null;
  }
}

export function recordHit(env, ctx, uid, category, error) {
  if (!env.METRICS_HUB || typeof uid !== "string" || !UID_PATTERN.test(uid)) return;
  ctx.waitUntil(metricsCall(env, "hit", { uid, cat: category, error: error === true }));
}
