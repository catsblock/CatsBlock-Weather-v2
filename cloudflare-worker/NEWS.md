# Weather news API

The app's news tab reads meteorological news through two worker routes. Both are protected by the existing `X-App-Token` / `X-Device-Id` authorization, so they behave like `/v1/weather`.

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
      "likes": 12
    }
  ],
  "cursor": "00000015",
  "total": 87
}
```

Pages contain 15 articles. Ordering is by likes, then regional relevance, then publication time. Summaries and images are omitted (empty string) when the upstream feed does not provide them; nothing is invented.

## GET /v1/news/event

The event route reuses the same normalized feed, relevance filter, ranking, cache, and cursor infrastructure. Pass `event` with a normalized event term; it returns at most 10 articles for that event. It accepts the same `lang`, `region`, and `cursor` parameters.

## POST /v1/news/like

Body: `{"id": "<article id>"}` with `Content-Type: application/json`.

Responses: `200 {"id": "...", "likes": 13}`, `409 already_liked` or `409 limit_reached` (10 000 likes per article), `429 rate_limited` (40 likes per ten minutes per device), `400 bad_request`, `503 unavailable`.

## Sources and caching

Each language is served by a Google News weather query (`hl`/`gl` per locale) plus curated feeds where they exist: Severe Weather Europe, NOAA, IPCC (English), G1 Meio Ambiente (pt-BR), 20minutos and El País (Spanish), Le Monde (French), MeteoGiornale (Italian), tagesschau (German, filtered by weather keywords). Aggregated lists are cached in `APP_KV` under `news:feed:<lang>` for 15 minutes, so a normal request performs no upstream fetch. Like counters live in `news:likes:index`; per-device votes live in `news:vote:<article>:<owner>` for 30 days.

## Requirements

- `APP_KV` must be bound; without it the routes answer `503 unavailable` and the app shows its "news unavailable" state.
- `node --check news.js` passes; the module has no new dependencies beyond the existing KV binding.
- The app only shows the tab content when the build has `CLOUDFLARE_WEBSITE` (https) and `APP_TOKEN` configured.
