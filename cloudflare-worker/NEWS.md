# Weather news API

The app's read-only news feed uses `GET /v1/news` and `GET /v1/news/event`. Both routes require the existing app authorization (`X-App-Token` / `X-Device-Id`) and use the `APP_KV` feed cache.

## GET /v1/news

Query parameters:
- `lang` – app language code (`en`, `pt-BR`, `pt-PT`, `es`, `fr`, `ru`, `de`, `it`, `ko`, `id`). Unknown values fall back to `en`.
- `region` – optional two-letter country code used to rank local stories first.
- `q` – optional search term, up to 60 characters, matched against title and summary.
- `cursor` – opaque value returned by the previous page.

Each article contains only its editorial fields: `id`, `title`, `summary`, `source`, `publishedAt`, `imageUrl`, `url`, `lang`, and `region`. Pages contain 15 articles and are ordered by regional relevance, then publication time. No account, like or reaction state is returned.

## GET /v1/news/event

The event route reuses the same normalized feed, relevance filter, cache, and cursor infrastructure. Pass `event` with a normalized event term; it returns at most 10 articles. It accepts the same `lang`, `region`, and `cursor` parameters and returns the same article shape.

## Removed functionality

First-party account registration, login, sessions, deletion, news likes, and reactions have been removed from the app and Worker API. Those paths now return `404 not_found`. Historical D1 migration files are kept so existing installations retain an intact migration history; this release does not read, write, or erase previously stored account/reaction rows. No user data is deleted by this code change.

## Sources and caching

Each language is served by a keyless Google News RSS weather query (`hl`/`gl` per locale) plus curated feeds where they exist: Severe Weather Europe, NOAA, IPCC (English), G1 Meio Ambiente (pt-BR), 20minutos and El País (Spanish), Le Monde (French), MeteoGiornale (Italian), tagesschau (German, filtered by weather keywords). Aggregated lists are cached in `APP_KV` under `news:feed:<lang>` for 15 minutes, so a normal request performs no upstream fetch.

## Requirements

- `APP_KV` must be bound; without it, news routes answer `503 unavailable` and the app shows its news-unavailable state.
- The app only shows the tab content when the build has `CLOUDFLARE_WEBSITE` (https) and `APP_TOKEN` configured.
