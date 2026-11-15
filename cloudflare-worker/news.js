const PAGE_SIZE = 15;
const CACHE_TTL_S = 900;
const MAX_LIKES = 10000;
const MAX_QUERY = 60;
const MAX_CURSOR = 8;
const FETCH_TIMEOUT_MS = 12000;
const MAX_BODY_BYTES = 2048;
const LIKE_WINDOW_S = 600;
const LIKE_MAX = 40;
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

function sourcesFor(lang) {
  const google = GOOGLE[lang];
  const list = [];
  if (google) {
    const url = "https://news.google.com/rss/search?q=" + encodeURIComponent(google.query) +
      "&hl=" + encodeURIComponent(google.hl) + "&gl=" + encodeURIComponent(google.gl) +
      "&ceid=" + encodeURIComponent(google.gl + ":" + google.hl);
    list.push({ url, source: "Google News", region: google.gl, lang, strict: true, google: true });
  }
  for (const item of CURATED[lang] || []) {
    list.push(Object.assign({ lang, google: false }, item));
  }
  return list;
}

async function feedFor(env, lang) {
  const key = FEED_KEY + lang;
  try {
    const cached = await env.APP_KV.get(key);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (err) {
    return [];
  }
  const lists = await Promise.all(sourcesFor(lang).map((meta) => fetchFeed(meta)));
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
  try {
    const raw = await env.APP_KV.get(LIKES_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (err) {
    return {};
  }
}

function encodeCursor(offset) {
  return String(offset).padStart(MAX_CURSOR, "0");
}

function decodeCursor(raw) {
  if (typeof raw !== "string" || !/^\d{1,8}$/.test(raw)) return 0;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) && value >= 0 ? value : 0;
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
  const rawLang = url.searchParams.get("lang") || "en";
  const lang = LANG_PATTERN.test(rawLang) ? rawLang : "en";
  const rawRegion = url.searchParams.get("region") || "";
  const region = REGION_PATTERN.test(rawRegion) ? rawRegion : "";
  const rawQuery = (url.searchParams.get("q") || "").trim().toLowerCase();
  const query = Array.from(rawQuery.replace(CONTROL_CHARS, " ")).slice(0, MAX_QUERY).join("");
  const cursor = decodeCursor(url.searchParams.get("cursor") || "");
  const articles = await feedFor(env, lang);
  if (articles.length === 0) {
    return h.jsonResponse({ articles: [], cursor: "", total: 0 }, 200, { "Cache-Control": "no-store" });
  }
  const index = await likeIndex(env);
  const filtered = query
    ? articles.filter((item) => (item.title + " " + item.summary).toLowerCase().includes(query))
    : articles;
  const scored = filtered.map((item) => {
    const likes = Number(index[item.id]) || 0;
    const regional = region && item.region === region ? 1 : 0;
    return { item, likes, regional };
  });
  scored.sort((a, b) => {
    if (b.likes !== a.likes) return b.likes - a.likes;
    if (b.regional !== a.regional) return b.regional - a.regional;
    return b.item.publishedAt - a.item.publishedAt;
  });
  const page = scored.slice(cursor, cursor + PAGE_SIZE);
  const next = cursor + PAGE_SIZE < scored.length ? encodeCursor(cursor + PAGE_SIZE) : "";
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
    likes: entry.likes
  }));
  return h.jsonResponse({ articles: payload, cursor: next, total: scored.length }, 200, { "Cache-Control": "no-store" });
}

export async function handleNewsLike(request, env, h) {
  if (!env.APP_KV) return h.jsonResponse({ error: "unavailable" }, 503);
  const id = await h.identify(request, env);
  if (!id || id.error) return h.jsonResponse({ error: "bad_request" }, 400);
  const body = await readBody(request);
  const articleId = body && typeof body.id === "string" ? body.id.trim().toLowerCase() : "";
  if (!ID_PATTERN.test(articleId)) return h.jsonResponse({ error: "bad_request" }, 400);
  const owner = id.hasDevice ? id.deviceHash : id.ipHash;
  const windowStart = Math.floor(Date.now() / (LIKE_WINDOW_S * 1000));
  const rateKey = RATE_KEY + owner + ":" + windowStart;
  try {
    const used = Number((await env.APP_KV.get(rateKey)) || 0);
    if (used >= LIKE_MAX) return h.jsonResponse({ error: "rate_limited" }, 429);
    await env.APP_KV.put(rateKey, String(used + 1), { expirationTtl: LIKE_WINDOW_S * 2 });
  } catch (err) {
    return h.jsonResponse({ error: "unavailable" }, 503);
  }
  const voteKey = VOTE_KEY + articleId + ":" + owner;
  const index = await likeIndex(env);
  const current = Number(index[articleId]) || 0;
  try {
    const voted = await env.APP_KV.get(voteKey);
    if (voted) return h.jsonResponse({ error: "already_liked", likes: current }, 409);
  } catch (err) {
    return h.jsonResponse({ error: "unavailable" }, 503);
  }
  if (current >= MAX_LIKES) return h.jsonResponse({ error: "limit_reached", likes: current }, 409);
  const updated = current + 1;
  index[articleId] = updated;
  try {
    await env.APP_KV.put(LIKES_KEY, JSON.stringify(index), { expirationTtl: INDEX_TTL_S });
    await env.APP_KV.put(voteKey, "1", { expirationTtl: VOTE_TTL_S });
  } catch (err) {
    return h.jsonResponse({ error: "unavailable" }, 503);
  }
  return h.jsonResponse({ id: articleId, likes: updated }, 200);
}
