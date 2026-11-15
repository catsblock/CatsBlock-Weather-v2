const PAGE_SIZE = 15;
const CACHE_TTL_S = 900;
const MAX_LIKES = 10000;
const MAX_QUERY = 60;
const MAX_EVENT = 40;
const MAX_CURSOR = 256;
const FETCH_TIMEOUT_MS = 12000;
const MAX_BODY_BYTES = 2048;
const ACTION_WINDOW_S = 120;
const ACTION_MAX = 14;
const ACTION_BLOCK_S = 900;
const REACTION_LIST = ["🤩", "😁", "😭", "⚡", "👍", "👏"];
const REACTIONS = new Set(REACTION_LIST);
const REACTION_KEY = "news:reaction:";
const REACTION_COUNTS_KEY = "news:reaction-counts:";
const VOTE_TTL_S = 30 * 24 * 60 * 60;
const INDEX_TTL_S = 120 * 24 * 60 * 60;
const ITEM_LIMIT = 40;
const SUMMARY_MAX = 220;
const TITLE_MAX = 180;
const ID_PATTERN = /^[0-9a-f]{16}$/;
const LANG_PATTERN = /^[a-z]{2,3}(-[A-Z]{2})?$/;
const REGION_PATTERN = /^[A-Z]{2}$/;
const FEED_KEY = "news:feed:";
const LIKES_KEY = "news:likes:index";
const VOTE_KEY = "news:vote:";
const RATE_KEY = "news:rate:";
const REACTION_RATE_KEY = "news:rate:reaction:";
const USER_AGENT = "CatsBlockWeather/2.0 (+https://github.com/catsblock)";
const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g;
const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: "\"", apos: "'", nbsp: " ", "#39": "'", "#34": "\"", "#8217": "\u2019", "#8216": "\u2018", "#8220": "\u201c", "#8221": "\u201d", "#8211": "\u2013", "#8212": "\u2014", "#8230": "\u2026" };
const GOOGLE = {
  en: { hl: "en-US", gl: "US", query: "weather storm climate" },
  "pt-BR": { hl: "pt-BR", gl: "BR", query: "tempo tempestade clima" },
  "pt-PT": { hl: "pt-PT", gl: "PT", query: "tempo tempestade clima" },
  es: { hl: "es", gl: "ES", query: "tiempo tormenta clima" },
  fr: { hl: "fr", gl: "FR", query: "météo tempête climat" },
  ru: { hl: "ru", gl: "RU", query: "погода шторм климат" },
  de: { hl: "de", gl: "DE", query: "Wetter Sturm Klima" },
  it: { hl: "it", gl: "IT", query: "meteo tempesta clima" },
  ko: { hl: "ko", gl: "KR", query: "날씨 태풍 기후" },
  id: { hl: "id", gl: "ID", query: "cuaca badai iklim" }
};
const CURATED = {
  en: [
    { url: "https://www.severe-weather.eu/feed/", source: "Severe Weather Europe", region: "EU", strict: true },
    { url: "https://www.noaa.gov/rss.xml", source: "NOAA", region: "US", strict: false },
    { url: "https://www.ipcc.ch/feed/", source: "IPCC", region: "GLOBAL", strict: true }
  ],
  "pt-BR": [
    { url: "https://g1.globo.com/rss/g1/meio-ambiente/", source: "G1 Meio Ambiente", region: "BR", strict: true }
  ],
  es: [
    { url: "https://www.20minutos.es/rss/medio-ambiente/", source: "20minutos", region: "ES", strict: true },
    { url: "https://elpais.com/rss/clima.xml", source: "El País", region: "ES", strict: true }
  ],
  fr: [
    { url: "https://www.lemonde.fr/climat/rss_full.xml", source: "Le Monde", region: "FR", strict: true }
  ],
  it: [
    { url: "https://www.meteogiornale.it/feed", source: "MeteoGiornale", region: "IT", strict: true }
  ],
  de: [
    { url: "https://www.tagesschau.de/xml/rss2/", source: "tagesschau", region: "DE", strict: false }
  ]
};
const KEYWORDS = {
  en: ["weather", "storm", "climate", "rain", "snow", "hurricane", "cyclone", "typhoon", "flood", "drought", "heat", "cold", "wind", "atmosphere", "temperature", "forecast", "wildfire", "el niño", "la niña", "meteorolog", "precipitation", "hail", "thunder", "monsoon"],
  pt: ["tempo", "tempestade", "clima", "chuva", "neve", "furacão", "ciclone", "inundação", "seca", "calor", "frio", "vento", "atmosfera", "temperatura", "previsão", "incêndio", "niño", "niña", "meteorolog", "granizo", "trovoada", "frente fria", "onda de calor"],
  es: ["tiempo", "tormenta", "clima", "lluvia", "nieve", "huracán", "ciclón", "inundación", "sequía", "calor", "frío", "viento", "atmósfera", "temperatura", "pronóstico", "incendio", "niño", "niña", "meteorolog", "granizo", "frente frío"],
  fr: ["météo", "tempête", "climat", "pluie", "neige", "ouragan", "cyclone", "inondation", "sécheresse", "chaleur", "froid", "vent", "atmosphère", "température", "prévision", "incendie", "niño", "niña", "météorolog", "grêle", "orage"],
  ru: ["погода", "шторм", "климат", "дождь", "снег", "ураган", "циклон", "наводнение", "засуха", "жара", "похолодание", "ветер", "атмосфера", "температура", "прогноз", "пожар", "эль-ниньо", "метеоролог", "град", "гроза"],
  de: ["wetter", "sturm", "klima", "regen", "schnee", "orkan", "zyklon", "überschwemmung", "dürre", "hitze", "kälte", "wind", "atmosphäre", "temperatur", "vorhersage", "waldbrand", "el niño", "la niña", "meteorolog", "hagel", "gewitter"],
  it: ["meteo", "tempesta", "clima", "pioggia", "neve", "uragano", "ciclone", "alluvione", "siccità", "caldo", "freddo", "vento", "atmosfera", "temperatura", "previsione", "incendio", "niño", "niña", "meteorolog", "grandine", "temporale"],
  ko: ["날씨", "폭풍", "기후", "태풍", "홍수", "가뭄", "폭염", "한파", "바람", "대기", "기온", "예보", "산불", "엘니뇨", "라니냐", "기상청", "우박", "뇌우", "장마", "폭설", "미세먼지", "강수"],
  id: ["cuaca", "badai", "iklim", "hujan", "salju", "topan", "siklon", "banjir", "kekeringan", "panas", "dingin", "angin", "atmosfer", "suhu", "prakiraan", "kebakaran", "el nino", "la nina", "meteorolog", "hujan es", "petir"]
};

function decodeEntities(text) {
  return text.replace(/&(#?[a-zA-Z0-9]+);/g, (match, name) => {
    const mapped = ENTITIES[name] || ENTITIES[name.toLowerCase()];
    if (mapped) return mapped;
    if (name.startsWith("#x") || name.startsWith("#X")) {
      const code = Number.parseInt(name.slice(2), 16);
      return Number.isFinite(code) && code > 31 && code < 0x110000 ? String.fromCodePoint(code) : " ";
    }
    if (name.startsWith("#")) {
      const code = Number.parseInt(name.slice(1), 10);
      return Number.isFinite(code) && code > 31 && code < 0x110000 ? String.fromCodePoint(code) : " ";
    }
    return match;
  });
}

function stripTags(text) {
  return text
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ");
}

function clean(text, max) {
  const plain = decodeEntities(stripTags(String(text || ""))).replace(CONTROL_CHARS, " ").replace(/\s+/g, " ").trim();
  return Array.from(plain).slice(0, max).join("");
}

function tag(block, names) {
  for (const name of names) {
    const match = block.match(new RegExp("<" + name + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + name + ">", "i"));
    if (match && match[1]) return match[1];
  }
  return "";
}

function attr(block, tagName, attribute) {
  const match = block.match(new RegExp("<" + tagName + "\\s[^>]*" + attribute + "=[\"']([^\"']+)[\"']", "i"));
  return match ? decodeEntities(match[1]) : "";
}

function hashId(text) {
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ code, 0x01000193) >>> 0;
    h2 = Math.imul(h2 + code, 0x85ebca6b) >>> 0;
  }
  return h1.toString(16).padStart(8, "0") + h2.toString(16).padStart(8, "0");
}

function safeUrl(raw) {
  try {
    const url = new URL(decodeEntities(String(raw || "")).trim());
    if (url.protocol !== "https:" && url.protocol !== "http:") return "";
    if (url.href.length > 512) return "";
    return url.href;
  } catch (err) {
    return "";
  }
}

function imageFrom(block, description) {
  const enclosure = attr(block, "enclosure", "url");
  if (enclosure) return safeUrl(enclosure);
  const media = attr(block, "media:content", "url") || attr(block, "media:thumbnail", "url");
  if (media) return safeUrl(media);
  const img = String(description || "").match(/<img[^>]+src=[\"']([^\"']+)[\"']/i);
  if (img) return safeUrl(img[1]);
  return "";
}

function languageKey(lang) {
  return String(lang || "en").split("-")[0].toLowerCase();
}

function keywordsFor(lang) {
  const key = languageKey(lang);
  return KEYWORDS[key] || KEYWORDS.en;
}

function relevant(article, meta) {
  if (meta.strict) return true;
  const title = article.title.toLowerCase();
  return keywordsFor(meta.lang).some((word) => {
    const term = word.toLowerCase();
    if (/[\uac00-\ud7af\u0400-\u04ff]/.test(term)) return title.includes(term);
    const pattern = new RegExp("(^|[^\\p{L}])" + term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "u");
    return pattern.test(title);
  });
}

function parseFeed(xml, meta) {
  const blocks = xml.match(/<item(?:\s[^>]*)?>[\s\S]*?<\/item>|<entry(?:\s[^>]*)?>[\s\S]*?<\/entry>/gi) || [];
  const out = [];
  for (const block of blocks.slice(0, ITEM_LIMIT)) {
    const rawTitle = tag(block, ["title"]);
    const rawDescription = tag(block, ["description", "summary", "content:encoded", "content"]);
    let title = clean(rawTitle, TITLE_MAX);
    if (!title) continue;
    const publisher = clean(tag(block, ["source"]), 80) || meta.source;
    if (meta.google && publisher && title.toLowerCase().endsWith(" - " + publisher.toLowerCase())) {
      title = title.slice(0, title.length - publisher.length - 3).trim();
    }
    let link = clean(tag(block, ["link", "guid"]), 512);
    if (!link || !/^https?:/i.test(link)) link = attr(block, "link", "href");
    const url = safeUrl(link);
    if (!url) continue;
    const dateText = clean(tag(block, ["pubDate", "published", "updated", "dc:date"]), 64);
    const parsed = Date.parse(dateText);
    const publishedAt = Number.isFinite(parsed) ? parsed : Date.now();
    const article = {
      id: hashId(url),
      title,
      summary: meta.google ? "" : clean(rawDescription, SUMMARY_MAX),
      source: publisher,
      publishedAt,
      imageUrl: meta.google ? "" : imageFrom(block, rawDescription),
      url,
      lang: meta.lang,
      region: meta.region
    };
    if (!relevant(article, meta)) continue;
    out.push(article);
  }
  return out;
}

async function fetchFeed(meta) {
  try {
    const response = await fetch(meta.url, {
      headers: { "User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/xml;q=0.9" },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
      cf: { cacheTtl: 600, cacheEverything: true }
    });
    if (!response.ok) return [];
    const text = await response.text();
    if (text.length > 1200000) return [];
    return parseFeed(text, meta);
  } catch (err) {
    return [];
  }
}


function sourcesFor(lang, focus = "") {
  const google = GOOGLE[lang] || GOOGLE.en;
  const query = clean(focus, MAX_QUERY) || google.query;
  const encodedQuery = encodeURIComponent(query);
  const googleUrl = "https://news.google.com/rss/search?q=" + encodedQuery + "&hl=" + encodeURIComponent(google.hl) + "&gl=" + google.gl + "&ceid=" + google.gl + ":" + google.hl.slice(0, 2);
  const list = [{
    url: googleUrl,
    source: "Google News",
    region: google.gl,
    lang,
    strict: false,
    google: true
  }];
  for (const item of CURATED[lang] || []) list.push(Object.assign({ lang, google: false }, item));
  return list;
}

async function feedFor(env, lang, focus = "") {
  const scopedFocus = clean(focus, MAX_QUERY).toLowerCase();
  const key = FEED_KEY + lang + ":" + hashId(scopedFocus);
  try {
    const cached = await env.APP_KV.get(key);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (err) {
    return [];
  }
  const lists = await Promise.all(sourcesFor(lang, scopedFocus).map((meta) => fetchFeed(meta)));
  const merged = [];
  const seenIds = new Set();
  const seenTitles = new Set();
  for (const list of lists) {
    for (const article of list) {
      const titleKey = article.title.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
      if (seenIds.has(article.id) || (titleKey && seenTitles.has(titleKey))) continue;
      seenIds.add(article.id);
      if (titleKey) seenTitles.add(titleKey);
      merged.push(article);
    }
  }
  merged.sort((a, b) => b.publishedAt - a.publishedAt);
  const capped = merged.slice(0, 150);
  if (capped.length > 0) {
    try {
      await env.APP_KV.put(key, JSON.stringify(capped), { expirationTtl: CACHE_TTL_S });
    } catch (err) {
      return capped;
    }
  }
  return capped;
}

async function likeIndex(env) {
  if (env.NEWS_DB) {
    try {
      const result = await env.NEWS_DB.prepare(
        "SELECT article_id, COUNT(*) AS likes FROM news_likes GROUP BY article_id"
      ).all();
      return Object.fromEntries((result.results || []).map((row) => [row.article_id, Number(row.likes) || 0]));
    } catch (err) {
      return {};
    }
  }
  try {
    const raw = await env.APP_KV.get(LIKES_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (err) {
    return {};
  }
}

function emptyCounts() {
  const counts = {};
  for (const reaction of REACTION_LIST) counts[reaction] = 0;
  return counts;
}

function normalizeCounts(value) {
  const counts = emptyCounts();
  if (!value || typeof value !== "object") return counts;
  for (const reaction of REACTION_LIST) counts[reaction] = Math.max(0, Math.min(MAX_LIKES, Number(value[reaction]) || 0));
  return counts;
}

function placeholdersFor(ids) {
  return ids.map(() => "?").join(",");
}

async function socialState(env, ids, owner) {
  const reactions = {};
  const mine = {};
  const liked = {};
  if (!ids.length) return { reactions, mine, liked };
  const marks = placeholdersFor(ids);
  if (env.NEWS_DB) {
    try {
      const counts = await env.NEWS_DB.prepare(
        "SELECT article_id, reaction, COUNT(*) AS votes FROM news_reaction_votes WHERE article_id IN (" + marks + ") GROUP BY article_id, reaction"
      ).bind(...ids).all();
      for (const row of counts.results || []) {
        const articleId = String(row.article_id || "");
        if (!articleId || !REACTIONS.has(row.reaction)) continue;
        reactions[articleId] = reactions[articleId] || emptyCounts();
        reactions[articleId][row.reaction] = Math.max(0, Math.min(MAX_LIKES, Number(row.votes) || 0));
      }
      if (owner) {
        const votes = await env.NEWS_DB.prepare(
          "SELECT article_id, reaction FROM news_reaction_votes WHERE voter_id = ? AND article_id IN (" + marks + ")"
        ).bind(owner, ...ids).all();
        for (const row of votes.results || []) {
          if (REACTIONS.has(row.reaction)) mine[String(row.article_id || "")] = row.reaction;
        }
        const likes = await env.NEWS_DB.prepare(
          "SELECT article_id FROM news_likes WHERE voter_id = ? AND article_id IN (" + marks + ")"
        ).bind(owner, ...ids).all();
        for (const row of likes.results || []) liked[String(row.article_id || "")] = true;
      }
      return { reactions, mine, liked };
    } catch (err) {
      return { reactions, mine, liked };
    }
  }
  try {
    const results = await Promise.all(ids.map(async (articleId) => {
      const raw = await env.APP_KV.get(REACTION_COUNTS_KEY + articleId);
      const parsed = raw ? JSON.parse(raw) : null;
      const vote = owner ? await env.APP_KV.get(REACTION_KEY + articleId + ":" + owner) : null;
      const like = owner ? await env.APP_KV.get(VOTE_KEY + articleId + ":" + owner) : null;
      return { articleId, parsed, vote, like };
    }));
    for (const entry of results) {
      if (entry.parsed) reactions[entry.articleId] = normalizeCounts(entry.parsed);
      if (entry.vote && REACTIONS.has(entry.vote)) mine[entry.articleId] = entry.vote;
      if (entry.like) liked[entry.articleId] = true;
    }
  } catch (err) {
    return { reactions, mine, liked };
  }
  return { reactions, mine, liked };
}

async function reactionCountsFor(env, articleId) {
  if (env.NEWS_DB) {
    try {
      const rows = await env.NEWS_DB.prepare(
        "SELECT reaction, COUNT(*) AS votes FROM news_reaction_votes WHERE article_id = ? GROUP BY reaction"
      ).bind(articleId).all();
      const counts = emptyCounts();
      for (const row of rows.results || []) {
        if (REACTIONS.has(row.reaction)) counts[row.reaction] = Math.max(0, Math.min(MAX_LIKES, Number(row.votes) || 0));
      }
      return counts;
    } catch (err) {
      return null;
    }
  }
  try {
    const raw = await env.APP_KV.get(REACTION_COUNTS_KEY + articleId);
    return raw ? normalizeCounts(JSON.parse(raw)) : emptyCounts();
  } catch (err) {
    return null;
  }
}

async function likeTotalFor(env, articleId) {
  const row = await env.NEWS_DB.prepare("SELECT COUNT(*) AS total FROM news_likes WHERE article_id = ?").bind(articleId).first();
  return Math.max(0, Math.min(MAX_LIKES, Number(row?.total) || 0));
}

async function setReactionWithDatabase(env, articleId, voterId, reaction) {
  const now = Date.now();
  try {
    if (reaction) {
      await env.NEWS_DB.prepare(
        "INSERT INTO news_reaction_votes(article_id, voter_id, reaction, created_at) VALUES (?, ?, ?, ?) " +
          "ON CONFLICT(article_id, voter_id) DO UPDATE SET reaction = excluded.reaction, created_at = excluded.created_at " +
          "WHERE news_reaction_votes.reaction <> excluded.reaction"
      ).bind(articleId, voterId, reaction, now).run();
    } else {
      await env.NEWS_DB.prepare(
        "DELETE FROM news_reaction_votes WHERE article_id = ? AND voter_id = ?"
      ).bind(articleId, voterId).run();
    }
    const stored = await env.NEWS_DB.prepare(
      "SELECT reaction FROM news_reaction_votes WHERE article_id = ? AND voter_id = ?"
    ).bind(articleId, voterId).first();
    const counts = await reactionCountsFor(env, articleId);
    if (!counts) return { error: "unavailable" };
    return { reaction: stored && REACTIONS.has(stored.reaction) ? stored.reaction : "", counts };
  } catch (err) {
    return { error: "unavailable" };
  }
}

async function setReactionWithKv(env, articleId, voterId, reaction) {
  const voteKey = REACTION_KEY + articleId + ":" + voterId;
  const countsKey = REACTION_COUNTS_KEY + articleId;
  try {
    const previous = await env.APP_KV.get(voteKey);
    if (reaction) await env.APP_KV.put(voteKey, reaction, { expirationTtl: VOTE_TTL_S });
    else await env.APP_KV.delete(voteKey);
    const raw = await env.APP_KV.get(countsKey);
    const counts = raw ? normalizeCounts(JSON.parse(raw)) : emptyCounts();
    if (previous && REACTIONS.has(previous)) counts[previous] = Math.max(0, counts[previous] - 1);
    if (reaction) counts[reaction] = Math.min(MAX_LIKES, counts[reaction] + 1);
    await env.APP_KV.put(countsKey, JSON.stringify(counts), { expirationTtl: INDEX_TTL_S });
    return { reaction: reaction || "", counts };
  } catch (err) {
    return { error: "unavailable" };
  }
}

async function setLikeWithDatabase(env, articleId, voterId, liked) {
  const now = Date.now();
  try {
    if (liked) {
      await env.NEWS_DB.prepare(
        "INSERT OR IGNORE INTO news_likes(article_id, voter_id, created_at) SELECT ?, ?, ? " +
          "WHERE (SELECT COUNT(*) FROM news_likes WHERE article_id = ?) < ?"
      ).bind(articleId, voterId, now, articleId, MAX_LIKES).run();
    } else {
      await env.NEWS_DB.prepare(
        "DELETE FROM news_likes WHERE article_id = ? AND voter_id = ?"
      ).bind(articleId, voterId).run();
    }
    const mine = await env.NEWS_DB.prepare(
      "SELECT 1 AS present FROM news_likes WHERE article_id = ? AND voter_id = ?"
    ).bind(articleId, voterId).first();
    return { likes: await likeTotalFor(env, articleId), liked: Boolean(mine && mine.present) };
  } catch (err) {
    return { error: "unavailable" };
  }
}

async function setLikeWithKv(env, articleId, voterId, liked) {
  const voteKey = VOTE_KEY + articleId + ":" + voterId;
  const index = await likeIndex(env);
  const current = Number(index[articleId]) || 0;
  try {
    const voted = await env.APP_KV.get(voteKey);
    if (liked && voted) return { likes: current, liked: true };
    if (!liked && !voted) return { likes: current, liked: false };
    if (liked && current >= MAX_LIKES) return { likes: current, liked: false };
    const updated = liked ? Math.min(MAX_LIKES, current + 1) : Math.max(0, current - 1);
    index[articleId] = updated;
    await env.APP_KV.put(LIKES_KEY, JSON.stringify(index), { expirationTtl: INDEX_TTL_S });
    if (liked) await env.APP_KV.put(voteKey, "1", { expirationTtl: VOTE_TTL_S });
    else await env.APP_KV.delete(voteKey);
    return { likes: updated, liked };
  } catch (err) {
    return { error: "unavailable" };
  }
}

function encodeCursor(boundary) {
  const encoded = btoa(JSON.stringify(boundary)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  return encoded.slice(0, MAX_CURSOR);
}

function decodeCursor(raw) {
  if (!raw || typeof raw !== "string" || raw.length > MAX_CURSOR || !/^[A-Za-z0-9_-]+$/.test(raw)) return null;
  try {
    const padded = raw.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((raw.length + 3) % 4);
    const value = JSON.parse(atob(padded));
    if (!value || !Number.isFinite(value.likes) || !Number.isFinite(value.publishedAt) || typeof value.id !== "string") return null;
    return { likes: value.likes, publishedAt: value.publishedAt, id: value.id, regional: value.regional === 1 ? 1 : 0, lang: typeof value.lang === "string" ? value.lang : "", region: typeof value.region === "string" ? value.region : "", query: typeof value.query === "string" ? value.query : "", event: typeof value.event === "string" ? value.event : "" };
  } catch (err) {
    return null;
  }
}

async function readBody(request) {
  const declared = Number(request.headers.get("Content-Length") || 0);
  if (declared > MAX_BODY_BYTES || !request.body) return null;
  const chunks = [];
  let received = 0;
  try {
    const reader = request.body.getReader();
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
    const parsed = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(merged));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch (err) {
    return null;
  }
}

export async function handleNews(request, env, h) {
  if (!env.APP_KV) return h.jsonResponse({ error: "unavailable" }, 503);
  const url = new URL(request.url);
  let owner = "";
  try {
    const identity = await h.identify(request, env);
    if (identity && !identity.error) owner = identity.hasDevice ? identity.deviceHash : identity.ipHash;
  } catch (err) {
    owner = "";
  }
  const rawLang = url.searchParams.get("lang") || "en";
  const lang = LANG_PATTERN.test(rawLang) ? rawLang : "en";
  const rawRegion = url.searchParams.get("region") || "";
  const region = REGION_PATTERN.test(rawRegion) ? rawRegion : "";
  const rawQuery = (url.searchParams.get("q") || "").trim().toLowerCase();
  const query = Array.from(rawQuery.replace(CONTROL_CHARS, " ")).slice(0, MAX_QUERY).join("");
  const event = Array.from((url.searchParams.get("event") || "").trim().toLowerCase().replace(CONTROL_CHARS, " ")).slice(0, MAX_EVENT).join("");
  const pageSize = event ? 10 : PAGE_SIZE;
  const cursor = decodeCursor(url.searchParams.get("cursor") || "");
  if (url.searchParams.get("cursor") && !cursor) return h.jsonResponse({ error: "invalid_cursor" }, 400);
  if (cursor && (cursor.lang !== lang || cursor.region !== region || cursor.query !== query || cursor.event !== event)) return h.jsonResponse({ error: "cursor_scope_mismatch" }, 400);
  const articles = await feedFor(env, lang, event || query);
  if (articles.length === 0) {
    return h.jsonResponse({ articles: [], cursor: "", total: 0 }, 200, { "Cache-Control": "no-store" });
  }
  const index = await likeIndex(env);
  const filtered = articles.filter((item) => {
    const text = (item.title + " " + item.summary + " " + item.source).toLowerCase();
    return (!query || text.includes(query)) && (!event || text.includes(event));
  });
  const scored = filtered.map((item) => {
    const likes = Number(index[item.id]) || 0;
    const regional = region && item.region === region ? 1 : 0;
    return { item, likes, regional };
  });
  scored.sort((a, b) => {
    if (b.likes !== a.likes) return b.likes - a.likes;
    if (b.regional !== a.regional) return b.regional - a.regional;
    if (b.item.publishedAt !== a.item.publishedAt) return b.item.publishedAt - a.item.publishedAt;
    return a.item.id.localeCompare(b.item.id);
  });
  const after = cursor ? scored.filter((entry) => {
    if (entry.likes !== cursor.likes) return entry.likes < cursor.likes;
    if (entry.regional !== cursor.regional) return entry.regional < cursor.regional;
    if (entry.item.publishedAt !== cursor.publishedAt) return entry.item.publishedAt < cursor.publishedAt;
    return entry.item.id > cursor.id;
  }) : scored;
  const page = after.slice(0, pageSize);
  const last = page[page.length - 1];
  const next = after.length > pageSize && last ? encodeCursor({ likes: last.likes, regional: last.regional, publishedAt: last.item.publishedAt, id: last.item.id, lang, region, query, event }) : "";
  const social = await socialState(env, page.map((entry) => entry.item.id), owner);
  const payload = page.map((entry) => ({
    id: entry.item.id,
    title: entry.item.title,
    summary: entry.item.summary,
    source: entry.item.source,
    publishedAt: entry.item.publishedAt,
    imageUrl: entry.item.imageUrl,
    url: entry.item.url,
    lang: entry.item.lang,
    region: entry.item.region,
    likes: entry.likes,
    liked: social.liked[entry.item.id] === true,
    reactions: social.reactions[entry.item.id] || emptyCounts(),
    myReaction: social.mine[entry.item.id] || ""
  }));
  return h.jsonResponse({ articles: payload, cursor: next, total: scored.length }, 200, { "Cache-Control": "no-store" });
}

async function consumeActionBudget(env, keys, nowMs) {
  if (!env.NEWS_DB) return { allowed: false, retryAfter: ACTION_BLOCK_S };
  const now = Math.floor(nowMs / 1000);
  const windowStart = Math.floor(now / ACTION_WINDOW_S);
  const retryAfter = ACTION_BLOCK_S;
  try {
    for (const key of keys) {
      const row = await env.NEWS_DB.prepare(
        "INSERT INTO news_action_limits(key, window_start, used, blocked_until) VALUES (?, ?, 1, 0) " +
          "ON CONFLICT(key) DO UPDATE SET " +
          "used = CASE " +
            "WHEN news_action_limits.blocked_until > ? THEN news_action_limits.used " +
            "WHEN news_action_limits.window_start = ? AND news_action_limits.used >= ? THEN news_action_limits.used " +
            "WHEN news_action_limits.window_start = ? THEN news_action_limits.used + 1 " +
            "ELSE 1 END, " +
          "window_start = CASE " +
            "WHEN news_action_limits.blocked_until > ? THEN news_action_limits.window_start " +
            "WHEN news_action_limits.window_start = ? THEN news_action_limits.window_start " +
            "ELSE ? END, " +
          "blocked_until = CASE " +
            "WHEN news_action_limits.blocked_until > ? THEN news_action_limits.blocked_until " +
            "WHEN news_action_limits.window_start = ? AND news_action_limits.used >= ? THEN ? " +
            "ELSE 0 END " +
          "RETURNING used, blocked_until"
      ).bind(
        key, windowStart,
        now, windowStart, ACTION_MAX, windowStart,
        now, windowStart, windowStart,
        now, windowStart, ACTION_MAX, now + ACTION_BLOCK_S
      ).first();
      const blockedUntil = Number(row?.blocked_until) || 0;
      if (blockedUntil > now) {
        return { allowed: false, retryAfter: Math.max(1, blockedUntil - now) };
      }
    }
    await env.NEWS_DB.prepare(
      "DELETE FROM news_action_limits WHERE blocked_until < ? AND window_start < ?"
    ).bind(now, windowStart - 2).run();
    return { allowed: true, retryAfter: 0 };
  } catch (err) {
    return { allowed: false, retryAfter };
  }
}

function budgetKeys(prefix, identity) {
  const keys = [prefix + ":" + identity.hash];
  if (identity.ipHash && identity.ipHash !== identity.hash) keys.push(prefix + "-ip:" + identity.ipHash);
  return keys;
}

export async function handleNewsReaction(request, env, h) {
  if (!env.APP_KV || !env.NEWS_DB) return h.jsonResponse({ error: "unavailable" }, 503);
  const identity = await h.identify(request, env);
  if (!identity || identity.error) return h.jsonResponse({ error: "bad_request" }, 400);
  const body = await readBody(request);
  if (!body) return h.jsonResponse({ error: "bad_request" }, 400);
  const articleId = typeof body.id === "string" ? body.id.trim().toLowerCase() : "";
  if (!ID_PATTERN.test(articleId)) return h.jsonResponse({ error: "bad_request" }, 400);
  const raw = typeof body.reaction === "string" ? body.reaction : "";
  const reaction = raw === "" ? "" : REACTIONS.has(raw) ? raw : null;
  if (reaction === null) return h.jsonResponse({ error: "bad_request" }, 400);
  const owner = identity.hasDevice ? identity.deviceHash : identity.ipHash;
  const allowed = await consumeActionBudget(env, budgetKeys("reaction", identity), Date.now());
  if (!allowed.allowed) return h.jsonResponse({ error: "rate_limited", retryAfterSeconds: allowed.retryAfter }, 429, { "Retry-After": String(allowed.retryAfter), "X-Lockout-Seconds": String(allowed.retryAfter), "Cache-Control": "no-store" });
  try {
    const result = await setReactionWithDatabase(env, articleId, owner, reaction);
    if (result.error) return h.jsonResponse({ error: result.error }, 503);
    return h.jsonResponse({ id: articleId, reaction: result.reaction, reactions: result.counts }, 200);
  } catch (err) {
    return h.jsonResponse({ error: "unavailable" }, 503);
  }
}

export async function handleNewsLike(request, env, h) {
  if (!env.APP_KV || !env.NEWS_DB) return h.jsonResponse({ error: "unavailable" }, 503);
  const identity = await h.identify(request, env);
  if (!identity || identity.error) return h.jsonResponse({ error: "bad_request" }, 400);
  const body = await readBody(request);
  if (!body) return h.jsonResponse({ error: "bad_request" }, 400);
  const articleId = typeof body.id === "string" ? body.id.trim().toLowerCase() : "";
  if (!ID_PATTERN.test(articleId)) return h.jsonResponse({ error: "bad_request" }, 400);
  const liked = typeof body.liked === "boolean" ? body.liked : body.unlike === true ? false : true;
  const owner = identity.hasDevice ? identity.deviceHash : identity.ipHash;
  const allowed = await consumeActionBudget(env, budgetKeys("like", identity), Date.now());
  if (!allowed.allowed) return h.jsonResponse({ error: "rate_limited", retryAfterSeconds: allowed.retryAfter }, 429, { "Retry-After": String(allowed.retryAfter), "X-Lockout-Seconds": String(allowed.retryAfter), "Cache-Control": "no-store" });
  try {
    const result = await setLikeWithDatabase(env, articleId, owner, liked);
    if (result.error) return h.jsonResponse({ error: result.error }, 503);
    return h.jsonResponse({ id: articleId, likes: result.likes, liked: result.liked }, 200);
  } catch (err) {
    return h.jsonResponse({ error: "unavailable" }, 503);
  }
}

export { relevant, encodeCursor, decodeCursor };
