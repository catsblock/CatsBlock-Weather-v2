const USERNAME_PATTERN = /^[a-z][a-z0-9_]{2,31}$/;
const RESERVED_USERNAMES = ["admin", "root", "system", "support", "moderator", "security"];
const TOKEN_BYTES = 32;
const ACCESS_SECONDS = 900;
const REFRESH_SECONDS = 2592000;
const MAX_BODY_BYTES = 4096;
const DEVICE_ID_PATTERN = /^[a-f0-9]{64}$/;
const DUMMY_SALT = "AAAAAAAAAAAAAAAAAAAAAA";
const DUMMY_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
const NO_STORE = { "Cache-Control": "no-store" };

class AuthError extends Error {
  constructor(code, status) {
    super(code);
    this.status = status || 400;
  }
}

function bytesToBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64ToBytes(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function digest(value) {
  const result = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(result)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function derivePassword(password, salt) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", salt: base64ToBytes(salt), iterations: 210000, hash: "SHA-256" }, key, 256);
  return bytesToBase64(new Uint8Array(bits));
}

async function randomToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(TOKEN_BYTES));
  return bytesToBase64(bytes);
}

function normalizeUsername(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function validateUsername(value) {
  const username = normalizeUsername(value);
  if (!USERNAME_PATTERN.test(username) || RESERVED_USERNAMES.includes(username)) throw new AuthError("invalid_username", 400);
  return username;
}

function validatePassword(value) {
  if (typeof value !== "string" || value.length < 10 || value.length > 128) throw new AuthError("invalid_password", 400);
  return value;
}

function constantTimeEqual(left, right) {
  const a = new TextEncoder().encode(String(left));
  const b = new TextEncoder().encode(String(right));
  let difference = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) difference |= (a[index] || 0) ^ (b[index] || 0);
  return difference === 0;
}

async function parseBody(request) {
  const declared = Number(request.headers.get("Content-Length") || 0);
  if (request.headers.has("Content-Length") && (!Number.isFinite(declared) || declared > MAX_BODY_BYTES)) return null;
  if (!request.body) return null;
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
    const text = new TextDecoder("utf-8", { fatal: true }).decode(merged);
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function publicAccount(row) {
  return { id: Number(row.id), username: row.username, role: row.role, createdAt: Number(row.created_at) };
}

async function sessionRow(env, token, request) {
  if (!env.NEWS_DB || typeof token !== "string" || token.length < 20) return null;
  const device = String(request.headers.get("X-Device-Id") || "").trim().toLowerCase();
  if (!DEVICE_ID_PATTERN.test(device)) return null;
  const hash = await digest(token);
  const row = await env.NEWS_DB.prepare("SELECT s.*, a.username, a.role, a.status FROM account_sessions s JOIN accounts a ON a.id = s.account_id WHERE s.token_hash = ? AND s.revoked_at = 0 AND s.expires_at > ? AND a.status = 'active'").bind(hash, Date.now()).first();
  return row && constantTimeEqual(row.installation_hash, device) ? row : null;
}

export async function authenticateSession(request, env) {
  const header = request.headers.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  const row = await sessionRow(env, token, request);
  return row ? { token, accountId: Number(row.account_id), username: row.username, role: row.role, sessionId: row.session_id } : null;
}

function readInstallation(request, body) {
  const header = String(request.headers.get("X-Device-Id") || "").trim().toLowerCase();
  const payload = typeof body.installationId === "string" ? body.installationId.trim().toLowerCase() : "";
  if (!DEVICE_ID_PATTERN.test(header) || !DEVICE_ID_PATTERN.test(payload) || !constantTimeEqual(header, payload)) return "";
  return header;
}

function lockResponse(h, verdict) {
  const retry = Math.max(1, Number(verdict && verdict.lockSeconds) || 1800);
  return h.jsonResponse({ error: "rate_limited", retryAfterSeconds: retry }, 429, {
    "Retry-After": String(retry),
    "X-Lockout-Seconds": String(retry),
    "Cache-Control": "no-store"
  });
}

function limiterReady(h) {
  return typeof h.identify === "function" && typeof h.limiterCall === "function";
}

async function authBudget(request, env, h, path, body) {
  if (!/\/(register|login|refresh)$/.test(path)) return null;
  if (!limiterReady(h)) return h.jsonResponse({ error: "security_not_configured" }, 503);
  const identity = await h.identify(request, env);
  if (!identity || identity.error || !identity.ipHash) return h.jsonResponse({ error: "client_unidentified" }, 400);
  const verdict = await h.limiterCall(env, identity.ipHash, "auth.peek");
  if (verdict && verdict.locked) return lockResponse(h, verdict);
  if (path.endsWith("/login")) {
    const username = normalizeUsername(body.username);
    if (username) {
      const accountVerdict = await h.limiterCall(env, await digest("auth-user|" + username), "auth.peek");
      if (accountVerdict && accountVerdict.locked) return lockResponse(h, accountVerdict);
    }
  }
  return null;
}

async function noteAuthFailure(request, env, h, path, body) {
  if (!/\/(register|login|refresh|delete)$/.test(path) || !limiterReady(h)) return;
  try {
    const identity = await h.identify(request, env);
    if (identity && !identity.error && identity.ipHash) await h.limiterCall(env, identity.ipHash, "auth.fail");
    const username = path.endsWith("/login") && body ? normalizeUsername(body.username) : "";
    if (username) await h.limiterCall(env, await digest("auth-user|" + username), "auth.fail");
  } catch {
    return;
  }
}

async function clearAccountFailures(env, h, username) {
  if (!username || !limiterReady(h)) return;
  try {
    await h.limiterCall(env, await digest("auth-user|" + username), "auth.reset");
  } catch {
    return;
  }
}

async function bindDevice(env, accountId, installationHash, now) {
  await env.NEWS_DB.batch([
    env.NEWS_DB.prepare("DELETE FROM account_devices WHERE account_id = ? OR installation_hash = ?").bind(accountId, installationHash),
    env.NEWS_DB.prepare("INSERT INTO account_devices(account_id, installation_hash, created_at, updated_at) VALUES (?, ?, ?, ?)").bind(accountId, installationHash, now, now)
  ]);
}

async function issueSession(env, accountId, installationHash) {
  const access = await randomToken();
  const refresh = await randomToken();
  const family = await randomToken();
  const now = Date.now();
  await env.NEWS_DB.batch([
    env.NEWS_DB.prepare("UPDATE account_sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at = 0").bind(now, accountId),
    env.NEWS_DB.prepare("UPDATE refresh_sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at = 0").bind(now, accountId),
    env.NEWS_DB.prepare("INSERT INTO account_sessions(token_hash, session_id, account_id, installation_hash, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?, 0)").bind(await digest(access), await randomToken(), accountId, installationHash, now + ACCESS_SECONDS * 1000),
    env.NEWS_DB.prepare("INSERT INTO refresh_sessions(token_hash, family_id, account_id, installation_hash, expires_at, used_at, revoked_at) VALUES (?, ?, ?, ?, ?, 0, 0)").bind(await digest(refresh), family, accountId, installationHash, now + REFRESH_SECONDS * 1000)
  ]);
  return { accessToken: access, refreshToken: refresh };
}

async function registerAccount(env, body, installation) {
  const username = validateUsername(body.username);
  const password = validatePassword(body.password);
  if (password !== body.passwordConfirmation) throw new AuthError("password_mismatch", 400);
  const existing = await env.NEWS_DB.prepare("SELECT id FROM accounts WHERE username_normalized = ?").bind(username).first();
  if (existing) {
    await derivePassword(password, DUMMY_SALT);
    throw new AuthError("invalid_request", 400);
  }
  const salt = bytesToBase64(crypto.getRandomValues(new Uint8Array(16)));
  const passwordHash = await derivePassword(password, salt);
  const now = Date.now();
  const result = await env.NEWS_DB.prepare("INSERT INTO accounts(username, username_normalized, password_hash, password_salt, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'user', 'active', ?, ?) ON CONFLICT(username_normalized) DO NOTHING").bind(username, username, passwordHash, salt, now, now).run();
  const accountId = Number(result.meta && result.meta.last_row_id);
  if (!result.meta || !Number(result.meta.changes) || !accountId) {
    await derivePassword(password, DUMMY_SALT);
    throw new AuthError("invalid_request", 400);
  }
  await bindDevice(env, accountId, installation, now);
  const tokens = await issueSession(env, accountId, installation);
  return { user: { id: accountId, username, role: "user" }, ...tokens };
}

async function loginAccount(env, h, body, installation) {
  const username = normalizeUsername(body.username);
  const password = validatePassword(body.password);
  const account = await env.NEWS_DB.prepare("SELECT * FROM accounts WHERE username_normalized = ? AND status = 'active'").bind(username).first();
  const candidateHash = await derivePassword(password, account?.password_salt || DUMMY_SALT);
  const valid = Boolean(account) && constantTimeEqual(candidateHash, account?.password_hash || DUMMY_HASH);
  if (!valid) throw new AuthError("invalid_credentials", 401);
  await bindDevice(env, account.id, installation, Date.now());
  const tokens = await issueSession(env, account.id, installation);
  await clearAccountFailures(env, h, username);
  return { user: publicAccount(account), ...tokens };
}

async function refreshSession(env, body, installation) {
  const refreshToken = typeof body.refreshToken === "string" ? body.refreshToken : "";
  if (refreshToken.length < 20) throw new AuthError("refresh_invalid", 401);
  const hash = await digest(refreshToken);
  const now = Date.now();
  const existing = await env.NEWS_DB.prepare("SELECT * FROM refresh_sessions WHERE token_hash = ?").bind(hash).first();
  if (!existing || !constantTimeEqual(existing.installation_hash, installation) || Number(existing.expires_at) <= now || Number(existing.revoked_at) !== 0) {
    throw new AuthError("refresh_invalid", 401);
  }
  const claimed = await env.NEWS_DB.prepare(
    "UPDATE refresh_sessions SET used_at = ? WHERE token_hash = ? AND used_at = 0 AND revoked_at = 0 AND expires_at > ? RETURNING *"
  ).bind(now, hash, now).first();
  if (!claimed) {
    await env.NEWS_DB.batch([
      env.NEWS_DB.prepare("UPDATE refresh_sessions SET revoked_at = ? WHERE family_id = ?").bind(now, existing.family_id),
      env.NEWS_DB.prepare("UPDATE account_sessions SET revoked_at = ? WHERE account_id = ?").bind(now, existing.account_id)
    ]);
    throw new AuthError("refresh_reuse_detected", 401);
  }
  const account = await env.NEWS_DB.prepare("SELECT * FROM accounts WHERE id = ? AND status = 'active'").bind(claimed.account_id).first();
  if (!account) throw new AuthError("account_unavailable", 401);
  const tokens = await issueSession(env, account.id, claimed.installation_hash);
  return { user: publicAccount(account), ...tokens };
}

async function logoutAccount(env, session) {
  if (session) {
    const now = Date.now();
    await env.NEWS_DB.batch([
      env.NEWS_DB.prepare("UPDATE account_sessions SET revoked_at = ? WHERE account_id = ?").bind(now, session.accountId),
      env.NEWS_DB.prepare("UPDATE refresh_sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at = 0").bind(now, session.accountId)
    ]);
  }
  return { success: true };
}

async function deleteAccount(env, session, body) {
  if (!session) throw new AuthError("unauthorized", 401);
  const password = validatePassword(body.password);
  const account = await env.NEWS_DB.prepare("SELECT * FROM accounts WHERE id = ?").bind(session.accountId).first();
  if (!account || !constantTimeEqual(await derivePassword(password, account.password_salt), account.password_hash)) throw new AuthError("invalid_credentials", 401);
  const now = Date.now();
  await env.NEWS_DB.batch([
    env.NEWS_DB.prepare("UPDATE accounts SET status = 'deleted', username = ?, username_normalized = ?, password_hash = '', password_salt = '', updated_at = ? WHERE id = ?").bind(`deleted_${session.accountId}`, `deleted_${session.accountId}`, now, session.accountId),
    env.NEWS_DB.prepare("UPDATE account_sessions SET revoked_at = ? WHERE account_id = ?").bind(now, session.accountId),
    env.NEWS_DB.prepare("UPDATE refresh_sessions SET revoked_at = ? WHERE account_id = ?").bind(now, session.accountId),
    env.NEWS_DB.prepare("DELETE FROM news_likes WHERE voter_id = ?").bind(String(session.accountId)),
    env.NEWS_DB.prepare("DELETE FROM news_reaction_votes WHERE voter_id = ?").bind(String(session.accountId)),
    env.NEWS_DB.prepare("DELETE FROM news_action_limits WHERE key = ?").bind("account:" + session.accountId),
    env.NEWS_DB.prepare("DELETE FROM account_devices WHERE account_id = ?").bind(session.accountId)
  ]);
  return { success: true };
}

export async function handleAuth(request, env, h, path) {
  if (!env.NEWS_DB) return h.jsonResponse({ error: "unavailable" }, 503, NO_STORE);
  if (request.method !== "POST" && !(path === "/v1/auth/me" && request.method === "GET")) return h.jsonResponse({ error: "method_not_allowed" }, 405, NO_STORE);
  if (path !== "/v1/auth/me" && !/^application\/json/i.test(request.headers.get("Content-Type") || "")) return h.jsonResponse({ error: "unsupported_media_type" }, 415, NO_STORE);
  let body = null;
  try {
    if (path === "/v1/auth/me") {
      const session = await authenticateSession(request, env);
      return session ? h.jsonResponse({ user: { id: session.accountId, username: session.username, role: session.role } }, 200, NO_STORE) : h.jsonResponse({ user: null }, 200, NO_STORE);
    }
    body = await parseBody(request);
    if (!body) {
      await noteAuthFailure(request, env, h, path, body);
      return h.jsonResponse({ error: "bad_request" }, 400, NO_STORE);
    }
    let budgetError;
    try {
      budgetError = await authBudget(request, env, h, path, body);
    } catch {
      return h.jsonResponse({ error: "unavailable" }, 503, NO_STORE);
    }
    if (budgetError) return budgetError;
    const installation = readInstallation(request, body);
    if (!installation) {
      await noteAuthFailure(request, env, h, path, body);
      return h.jsonResponse({ error: "client_unidentified" }, 400, NO_STORE);
    }
    if (path === "/v1/auth/register") return h.jsonResponse(await registerAccount(env, body, installation), 201, NO_STORE);
    if (path === "/v1/auth/login") return h.jsonResponse(await loginAccount(env, h, body, installation), 200, NO_STORE);
    if (path === "/v1/auth/refresh") return h.jsonResponse(await refreshSession(env, body, installation), 200, NO_STORE);
    const session = await authenticateSession(request, env);
    if (path === "/v1/auth/logout") return h.jsonResponse(await logoutAccount(env, session), 200, NO_STORE);
    if (path === "/v1/auth/delete") return h.jsonResponse(await deleteAccount(env, session, body), 200, NO_STORE);
    return h.jsonResponse({ error: "not_found" }, 404, NO_STORE);
  } catch (error) {
    if (error instanceof AuthError) {
      await noteAuthFailure(request, env, h, path, body);
      return h.jsonResponse({ error: error.message }, error.status, NO_STORE);
    }
    console.error("auth_unhandled", error && error.message ? String(error.message).slice(0, 200) : "unknown");
    return h.jsonResponse({ error: "internal_error" }, 500, NO_STORE);
  }
}
