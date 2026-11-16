# Weather news API

The app's news tab reads meteorological news through four worker routes. All of them are protected by the existing `X-App-Token` / `X-Device-Id` authorization, so they behave like `/v1/weather`.

## GET /v1/news

Query parameters:

- `lang` – app language code (`en`, `pt-BR`, `pt-PT`, `es`, `fr`, `ru`, `de`, `it`, `ko`, `id`). Unknown values fall back to `en`.
- `region` – optional two-letter country code used only to rank local stories first.
- `q` – optional search term, up to 60 characters, matched against title and summary.
- `cursor` – opaque value returned by the previous page.

Response:

```json
{
  "articles": [
    {
      "id": "24d56a5ab0cd452e",
      "title": "Storm warning issued for the southern coast",
      "summary": "Short plain-text excerpt.",
      "source": "Severe Weather Europe",
      "publishedAt": 1794758433000,
      "imageUrl": "https://example.com/photo.jpg",
      "url": "https://example.com/story",
      "lang": "en",
      "region": "EU",
      "likes": 12,
      "liked": true,
      "reactions": {
        "🤩": 4,
        "😁": 2,
        "😭": 1,
        "⚡": 7,
        "👍": 12,
        "👏": 3
      },
      "myReaction": "⚡"
    }
  ],
  "cursor": "00000015",
  "total": 87
}
```

`liked`, `reactions` and `myReaction` describe the authenticated account identified by the bearer session. Reading the feed remains public after the app token check. `myReaction` is an empty string when the account has no reaction, and every reaction key is always present, including the ones with zero votes. Pages contain 15 articles. Ordering is by likes, then regional relevance, then publication time. Summaries and images are omitted (empty string) when the upstream feed does not provide them; nothing is invented.

## Account routes

`POST /v1/auth/register` and `POST /v1/auth/login` create first-party username/password accounts. `GET /v1/auth/me` returns the current account. `POST /v1/auth/logout` revokes the current access session and `POST /v1/auth/delete` permanently disables the account after password confirmation. Passwords are stored as PBKDF2-SHA-256 verifiers; plaintext passwords never appear in responses. A new login revokes previous sessions and replaces the active device.

## GET /v1/news/event

The event route reuses the same normalized feed, relevance filter, ranking, cache, and cursor infrastructure. Pass `event` with a normalized event term; it returns at most 10 articles for that event. It accepts the same `lang`, `region`, and `cursor` parameters and returns the same article shape.

## POST /v1/news/like

Body: `{"id": "<article id>", "liked": true}` with `Content-Type: application/json`. Send `"liked": false` to remove the like. The older `{"unlike": true}` form is still accepted.

Response: `200 {"id": "...", "likes": 13, "liked": true}`. `liked` always reports the state that is actually stored, so a client can apply it directly. Both operations are idempotent: repeating the same body leaves the total unchanged and never creates a second vote.

Errors: `401 authentication_required`, `400 bad_request` (missing or malformed identifier), `429 rate_limited` (the 10th action within 60 seconds starts a 15-minute lockout), `503 unavailable` (storage missing). A `429` response includes `Retry-After` and `X-Lockout-Seconds`.

## POST /v1/news/reaction

Body: `{"id": "<article id>", "reaction": "🤩"}`. Only 🤩, 😁, 😭, ⚡, 👍 and 👏 are accepted. Send `"reaction": ""` (or omit the field) to remove the reader's reaction.

The route is a set operation, not a toggle: the stored reaction becomes exactly the requested one. Selecting a different emoji replaces the previous one, repeating the same emoji is a no-op, and an empty value removes the vote. This makes retries and coalesced client requests safe.

Response:

```json
{
  "id": "24d56a5ab0cd452e",
  "reaction": "⚡",
  "reactions": { "🤩": 4, "😁": 2, "😭": 1, "⚡": 8, "👍": 12, "👏": 3 }
}
```

`reaction` is the state that is actually stored, and `reactions` is the resulting per-emoji tally. Errors: `401 authentication_required`, `400 bad_request` (unknown reaction or identifier), `429 rate_limited` (the 10th action within 60 seconds starts a 15-minute lockout), `503 unavailable` (storage missing). A `429` response includes `Retry-After` and `X-Lockout-Seconds`.

## Counts, consistency and abuse protection

Counters are never stored as separate mutable rows. Each like and each reaction is a single row keyed by `(article_id, voter_id)`, and every total in a response is computed with `COUNT(*)` over those rows:

- one reader can hold at most one like and at most one reaction per article, enforced by primary keys;
- a total can therefore never drift, double, or lose other reactions, regardless of how many requests arrive at once;
- every write is a single statement, so no read-modify-write sequence exists to interleave badly.

Rate limits use a fixed 60-second window stored in D1. The first 9 valid actions are accepted; the 10th atomically sets `blocked_until` to 15 minutes in the future and is rejected. Every request during that period is rejected without changing the vote. The budget key is the authenticated account, so changing devices, IP addresses, sessions or articles cannot multiply the budget. The limiter fails closed if D1 is unavailable; it never falls back to a non-atomic KV counter for mutations. The app additionally collapses rapid taps into one in-flight request per article and honors the server's `Retry-After` lockout locally.

## Sources and caching

Each language is served by a keyless Google News RSS weather query (`hl`/`gl` per locale) plus curated feeds where they exist: Severe Weather Europe, NOAA, IPCC (English), G1 Meio Ambiente (pt-BR), 20minutos and El País (Spanish), Le Monde (French), MeteoGiornale (Italian), tagesschau (German, filtered by weather keywords). Aggregated lists are cached in `APP_KV` under `news:feed:<lang>` for 15 minutes, so a normal request performs no upstream fetch.

## Requirements

- `APP_KV` must be bound; without it the routes answer `503 unavailable` and the app shows its "news unavailable" state.
- `NEWS_DB` (D1) must be bound and the migrations in `migrations/` applied (`wrangler d1 migrations apply`), including `0004_news_action_lockouts.sql` and `0005_first_party_accounts.sql`. Without the binding the likes, reactions and account routes answer `503 unavailable` rather than using an unsafe KV write path.
- `node --check news.js` passes; the module has no dependencies beyond the existing KV and D1 bindings.
- The app only shows the tab content when the build has `CLOUDFLARE_WEBSITE` (https) and `APP_TOKEN` configured.
