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

`liked`, `reactions` and `myReaction` describe the reader identified by `X-Device-Id`. `myReaction` is an empty string when the reader has no reaction, and every reaction key is always present, including the ones with zero votes. Pages contain 15 articles. Ordering is by likes, then regional relevance, then publication time. Summaries and images are omitted (empty string) when the upstream feed does not provide them; nothing is invented.

## GET /v1/news/event

The event route reuses the same normalized feed, relevance filter, ranking, cache, and cursor infrastructure. Pass `event` with a normalized event term; it returns at most 10 articles for that event. It accepts the same `lang`, `region`, and `cursor` parameters and returns the same article shape.

## POST /v1/news/like

Body: `{"id": "<article id>"}`, optionally `{"id": "...", "unlike": true}`. `Content-Type: application/json`.

Responses:

- `200 {"id": "...", "likes": 13, "liked": true}` – the like was added, or already existed.
- `200 {"id": "...", "likes": 12, "liked": false}` – the like was removed, or did not exist.
- `409 {"error": "limit_reached"}` – the article already has 10 000 likes.
- `429 {"error": "rate_limited"}` – 40 like actions per ten minutes per device.
- `400 {"error": "bad_request"}` – missing or malformed identifier.
- `503 {"error": "unavailable"}` – the storage binding is missing.

The endpoint is idempotent: repeating a like keeps a single vote and the same total, and repeating an unlike keeps the total at zero.

## POST /v1/news/reaction

Body: `{"id": "<article id>", "reaction": "🤩"}`. Only 🤩, 😁, 😭, ⚡, 👍 and 👏 are accepted.

Behaviour: a reader has at most one reaction per article. Selecting a new emoji replaces the previous one and moves the vote between counters. Selecting the currently selected emoji removes the reaction.

Response:

```json
{
  "id": "24d56a5ab0cd452e",
  "reaction": "⚡",
  "reactions": { "🤩": 4, "😁": 2, "😭": 1, "⚡": 8, "👍": 12, "👏": 3 }
}
```

`reaction` is an empty string when the reaction was removed. Errors: `400 bad_request` (unknown article, reaction or identifier), `429 rate_limited` (80 reaction actions per ten minutes per device), `503 unavailable` (storage missing).

## Sources and caching

Each language is served by a keyless Google News RSS weather query (`hl`/`gl` per locale) plus curated feeds where they exist: Severe Weather Europe, NOAA, IPCC (English), G1 Meio Ambiente (pt-BR), 20minutos and El País (Spanish), Le Monde (French), MeteoGiornale (Italian), tagesschau (German, filtered by weather keywords). Aggregated lists are cached in `APP_KV` under `news:feed:<lang>` for 15 minutes, so a normal request performs no upstream fetch.

Likes, unlikes and reactions are stored in D1:

- `news_reaction_votes` – one row per reader and article (like), primary key `(article_id, voter_id)`.
- `news_reaction_counts` – per-emoji counters with a `0..10000` check constraint.
- `news_reactions` – legacy per-device like votes, kept for compatibility with existing deployments.

Per-device action throttling uses the existing KV counters.

## Requirements

- `APP_KV` must be bound; without it the routes answer `503 unavailable` and the app shows its "news unavailable" state.
- `NEWS_DB` (D1) must be bound and the migrations in `migrations/` applied (`wrangler d1 migrations apply`). Without the binding the likes and reactions answer `503 unavailable`.
- `node --check news.js` passes; the module has no dependencies beyond the existing KV and D1 bindings.
- The app only shows the tab content when the build has `CLOUDFLARE_WEBSITE` (https) and `APP_TOKEN` configured.
