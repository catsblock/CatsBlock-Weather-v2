# CatsBlock Weather Security Audit

Date: 2026-11-16

> **Current implementation note (2026-11-16):** Account registration/login/session endpoints and news likes/reactions described in this historical review have since been removed from the Android app and Cloudflare Worker. Historical D1 migrations remain for schema-history safety; this removal does not erase legacy account or engagement records.

## Scope

This review covered the Android client, the Cloudflare Worker authentication and request-routing paths, local credential storage, external URL handling, crash diagnostics, repository hygiene, and the existing account/session migrations.

The review was source-based and was followed by an Android debug build, Android lint, JavaScript syntax checks, a whitespace/diff check, and a tracked-secret filename scan. No production Worker or D1 credentials were used, so deployed integration behavior was not exercised against production.

## Executive summary

The application had several concrete security weaknesses around local secret storage, authentication state handling, request-size enforcement, token binding, logout invalidation, and cleartext external content. Those issues were corrected in the working tree. The account dialog was also redesigned as a responsive Material 3 Expressive surface without retaining passwords in saved Compose state.

The most important remaining architectural risk is the static `X-App-Token` value compiled into the APK. Any value shipped to an Android client must be treated as public; it is not a secret regardless of obfuscation. The Worker still uses it as an application gate because removing it would lower the abuse barrier. A future release should replace this gate with Play Integrity or another attested bootstrap/exchange flow, and the server-side value should be rotated if it has ever been used outside the intended client.

## Findings and remediation

### SEC-01 — Personal API keys were stored in plaintext DataStore

**Risk:** Personal weather-provider API keys were persisted in the ordinary Preferences DataStore. A local file disclosure, rooted device, debug extraction, or backup mistake could expose them.

**Remediation:** `SettingsStore` now uses Android Keystore-backed `EncryptedSharedPreferences` for API keys and performs a one-time migration from the legacy DataStore keys, removing the plaintext copies after successful migration. The existing DataStore fallback remains only for the exceptional case where Android secure storage cannot be initialized; that case should be treated as a device/platform failure rather than a normal path.

### SEC-02 — Password fields used saved Compose state

**Risk:** `rememberSaveable` can serialize values into the activity saved-state bundle. Password and confirmation values must not be placed there.

**Remediation:** Password and confirmation now use ordinary composition memory. Username and non-sensitive dialog state may still survive configuration changes.

### SEC-03 — Authentication JSON bodies were not byte-bounded for chunked requests

**Risk:** The Worker checked `Content-Length` and then called `request.text()`. A missing, malformed, or untrusted length header could allow an oversized request body to be buffered before validation.

**Remediation:** Authentication bodies are now read from the stream with a strict 4 KiB byte limit and fatal UTF-8 decoding. Arrays and malformed JSON are rejected.

### SEC-04 — Access and refresh tokens were not bound to the installation

**Risk:** The database stored `installation_hash`, but session authentication did not verify the incoming device header. A copied bearer token could be replayed from another client.

**Remediation:** Access-session validation now requires a valid `X-Device-Id` and constant-time equality with the stored installation hash. Register, login, refresh, logout, and delete require the request header and JSON installation ID to match. Refresh rotation also validates the stored installation before claiming the token.

### SEC-05 — Logout did not revoke refresh sessions

**Risk:** Logout revoked the current access session but left refresh tokens usable. A stolen refresh token could therefore create a new session after logout.

**Remediation:** Logout now revokes all account access sessions and all non-revoked refresh sessions in one D1 batch. Account deletion already revoked both token classes and continues to do so.

### SEC-06 — Authentication timing exposed account existence

**Risk:** Login derived a password hash only when an account existed. Registration returned a distinct username-conflict response and skipped password work for an existing username. These differences could assist username enumeration.

**Remediation:** Login always performs PBKDF2 work using a fixed dummy salt when the account is absent. Registration performs equivalent dummy PBKDF2 work for a duplicate username and returns the same generic invalid-request class used for other malformed requests. The Android client no longer exposes a dedicated “username taken” branch.

### SEC-07 — Account deletion compared password hashes with a normal equality operator

**Risk:** Direct string comparison can introduce avoidable timing differences.

**Remediation:** Password verification for account deletion now uses the existing constant-time comparison helper.

### SEC-08 — Cleartext HTTP news URLs were accepted

**Risk:** Article and image URLs using HTTP could be downgraded or modified in transit.

**Remediation:** The Android manifest explicitly disables cleartext traffic. News article and image URLs are accepted only when they use HTTPS.

### SEC-09 — Crash snapshots persisted unsanitized exception text

**Risk:** Exception messages can contain URLs, identifiers, or accidental credentials. The last-crash snapshot was stored in ordinary app preferences before redaction.

**Remediation:** Crash descriptions and stack snippets are redacted and bounded before both in-memory logging and persistence.

### SEC-10 — Client-side session response validation was permissive

**Risk:** Malformed server responses could be persisted as a local session if the fields were merely non-empty.

**Remediation:** Account usernames and access/refresh tokens are now checked against the expected formats before persistence.

## Authentication design observations

Password hashing uses PBKDF2-HMAC-SHA-256 with 210,000 iterations and a random salt. Access and refresh tokens are random 32-byte values, while only their SHA-256 digests are stored in D1. Refresh tokens are single-use and family reuse revokes the family and account access sessions. The D1 migration adds uniqueness for normalized usernames and token/session identifiers.

The Worker rejects browser-style headers, applies application authorization before protected routes, caps general request bodies, applies authentication rate limits, and uses generic credential failure responses. The Android client uses Keystore-backed encrypted preferences for its account session.

## Residual risks and follow-up recommendations

1. **Static application token in the APK:** Treat it as public, rotate it if it has been exposed outside the app, and move toward Play Integrity or an attested short-lived application credential. Do not place D1, Cloudflare, provider, admin, or signing secrets in the APK.
2. **Production integration testing:** Run staging tests for register, login, access-token expiry, refresh rotation, refresh replay, logout, deletion, device mismatch, and rate-limit lockout using a disposable D1 database.
3. **Dependency and action pinning:** Pin GitHub Actions and Worker deployment dependencies to reviewed immutable versions or commit SHAs as part of the release-hardening process.
4. **Operational key rotation:** Rotate any provider keys that were ever entered into a prior unencrypted build or stored in a compromised device backup. The migration protects new writes and existing in-app migration, but it cannot erase copies made outside the app sandbox.

## Verification

- `:app:assembleDebug` — passed.
- `:app:lintDebug` — passed.
- `node --check cloudflare-worker/*.js` — passed.
- `git diff --check` — passed.
- Tracked-secret filename scan — passed.

The Android build generated a debug APK locally for inspection; release signing and production deployment were not performed by this audit.
