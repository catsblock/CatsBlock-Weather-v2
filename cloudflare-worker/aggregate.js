export const CONSENSUS_RATIO = 0.75;
export const PROVIDER_ORDER = ["openmeteo", "weather", "openweather", "visual"];

const WET_HOUR_MM = 0.1;
const WET_DAY_MM = 1.0;
const WET_PROB_FLOOR = 75;
const DRY_PROB_CEILING = 49;
const CURRENT_SOURCES = 2;
const MAX_HOURS = 400;
const MAX_DAYS = 16;
const HOUR_KEY = /^\d{4}-\d{2}-\d{2}T\d{2}:00$/;
const DAY_KEY = /^\d{4}-\d{2}-\d{2}$/;
const STAMP = /^(\d{4}-\d{2}-\d{2})[ T](\d{1,2}):(\d{2})/;

export function requiredVotes(participants) {
  return participants <= 2 ? Math.max(1, participants) : participants - 1;
}

function finite(value, fallback) {
  if (value === null || value === undefined || value === "") return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function mean(values, fallback) {
  const valid = values.filter((v) => Number.isFinite(v));
  if (!valid.length) return fallback;
  return valid.reduce((a, b) => a + b, 0) / valid.length;
}

function wetCode(code) {
  return (code >= 51 && code <= 67) || (code >= 71 && code <= 77) || (code >= 80 && code <= 86) || (code >= 95 && code <= 99);
}

function mode(codes, fallback) {
  const counts = new Map();
  for (const code of codes) counts.set(code, (counts.get(code) || 0) + 1);
  let best = fallback;
  let bestCount = 0;
  for (const [code, count] of counts) {
    if (count > bestCount || (count === bestCount && code > best)) {
      best = code;
      bestCount = count;
    }
  }
  return best;
}

function weatherApiCode(raw) {
  if (raw === 1000) return 0;
  if (raw === 1003) return 2;
  if (raw === 1006 || raw === 1009) return 3;
  if (raw === 1030 || raw === 1135 || raw === 1147) return 45;
  if (raw === 1150 || raw === 1153) return 51;
  if (raw === 1072 || raw === 1168) return 56;
  if (raw === 1171) return 57;
  if (raw === 1063 || raw === 1180 || raw === 1183) return 61;
  if (raw === 1186 || raw === 1189) return 63;
  if (raw === 1192 || raw === 1195) return 65;
  if (raw === 1198) return 66;
  if ([1069, 1201, 1204, 1207, 1249, 1252].includes(raw)) return 67;
  if ([1066, 1210, 1213, 1255].includes(raw)) return 71;
  if (raw === 1216 || raw === 1219) return 73;
  if ([1114, 1117, 1222, 1225, 1258].includes(raw)) return 75;
  if ([1237, 1261, 1264].includes(raw)) return 77;
  if (raw === 1240) return 80;
  if (raw === 1243) return 81;
  if (raw === 1246) return 82;
  if ([1087, 1273, 1276, 1279, 1282].includes(raw)) return 95;
  return 3;
}

function openWeatherCode(id) {
  if (id >= 200 && id <= 232) return 95;
  if (id >= 300 && id <= 321) return 51;
  if (id === 500) return 61;
  if (id === 501) return 63;
  if (id >= 502 && id <= 504) return 65;
  if (id === 511) return 67;
  if (id === 520) return 80;
  if (id === 521) return 81;
  if (id === 522 || id === 531) return 82;
  if (id === 600) return 71;
  if (id === 601) return 73;
  if (id === 602) return 75;
  if ([611, 612, 613, 615, 616].includes(id)) return 67;
  if (id === 620 || id === 621) return 85;
  if (id === 622) return 86;
  if ([701, 711, 721, 731, 741, 751, 761, 762].includes(id)) return 45;
  if (id === 781) return 95;
  if (id === 800) return 0;
  if (id === 801) return 1;
  if (id === 802) return 2;
  return 3;
}

function crossingCode(icon, precip, daily) {
  const text = typeof icon === "string" ? icon : "";
  const light = daily ? 2.0 : 0.5;
  const heavy = daily ? 10.0 : 4.0;
  const wet = precip < light ? 61 : precip < heavy ? 63 : 65;
  if (text.startsWith("clear")) return 0;
  if (text.startsWith("partly-cloudy")) return 2;
  if (text === "cloudy") return 3;
  if (text === "wind") return 2;
  if (text === "fog") return 45;
  if (text === "freezing-rain") return 66;
  if (text === "sleet") return 67;
  if (text === "hail") return 96;
  if (text.startsWith("thunder")) return 95;
  if (text.startsWith("snow-showers")) return 85;
  if (text === "snow") return precip >= heavy ? 75 : 73;
  if (text.startsWith("showers")) return 80;
  if (text === "rain") return wet;
  return 3;
}

function hourKey(stamp) {
  const m = STAMP.exec(String(stamp));
  if (!m) return "";
  const key = m[1] + "T" + m[2].padStart(2, "0") + ":00";
  return HOUR_KEY.test(key) ? key : "";
}

function dayKey(value) {
  const key = String(value).slice(0, 10);
  return DAY_KEY.test(key) ? key : "";
}

function localIso(epoch, offsetSeconds) {
  const date = new Date((epoch + offsetSeconds) * 1000);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString().slice(0, 16);
}

function parseOpenMeteo(raw) {
  const out = { current: null, hours: new Map(), days: new Map() };
  const h = raw && raw.hourly;
  const d = raw && raw.daily;
  const c = raw && raw.current;
  if (!h || !d || !c || !Array.isArray(h.time) || !Array.isArray(d.time)) throw new Error("shape");
  const pick = (arr, i) => (Array.isArray(arr) ? finite(arr[i], null) : null);
  for (let i = 0; i < h.time.length && i < MAX_HOURS; i++) {
    const key = hourKey(h.time[i]);
    if (!key) continue;
    out.hours.set(key, {
      temp: pick(h.temperature_2m, i),
      prob: pick(h.precipitation_probability, i),
      precip: pick(h.precipitation, i),
      code: pick(h.weather_code, i),
      humidity: pick(h.relative_humidity_2m, i),
      wind: pick(h.wind_speed_10m, i),
      pressure: pick(h.pressure_msl, i),
      cloud: pick(h.cloud_cover, i),
      uv: pick(h.uv_index, i),
    });
  }
  for (let i = 0; i < d.time.length && i < MAX_DAYS; i++) {
    const key = dayKey(d.time[i]);
    if (!key) continue;
    out.days.set(key, {
      max: pick(d.temperature_2m_max, i),
      min: pick(d.temperature_2m_min, i),
      code: pick(d.weather_code, i),
      precip: pick(d.precipitation_sum, i),
      prob: pick(d.precipitation_probability_max, i),
    });
  }
  const time = String(c.time || "");
  if (!STAMP.test(time)) throw new Error("shape");
  out.current = {
    time,
    temp: finite(c.temperature_2m, null),
    feels: finite(c.apparent_temperature, null),
    humidity: finite(c.relative_humidity_2m, null),
    precip: finite(c.precipitation, 0),
    code: finite(c.weather_code, 3),
    wind: finite(c.wind_speed_10m, null),
    isDay: finite(c.is_day, 1),
  };
  return out;
}

function parseWeatherApi(root) {
  const out = { current: null, hours: new Map(), days: new Map() };
  const location = root && root.location;
  const cur = root && root.current;
  const forecast = root && root.forecast && root.forecast.forecastday;
  if (!location || !cur || !Array.isArray(forecast)) throw new Error("shape");
  for (const entry of forecast.slice(0, MAX_DAYS)) {
    const key = dayKey(entry.date);
    const day = entry.day || {};
    if (key) {
      out.days.set(key, {
        max: finite(day.maxtemp_c, null),
        min: finite(day.mintemp_c, null),
        code: weatherApiCode(finite(day.condition && day.condition.code, 0)),
        precip: finite(day.totalprecip_mm, null),
        prob: Math.max(finite(day.daily_chance_of_rain, 0), finite(day.daily_chance_of_snow, 0)),
      });
    }
    for (const o of (Array.isArray(entry.hour) ? entry.hour : []).slice(0, 48)) {
      const hk = hourKey(o.time);
      if (!hk) continue;
      out.hours.set(hk, {
        temp: finite(o.temp_c, null),
        prob: Math.max(finite(o.chance_of_rain, 0), finite(o.chance_of_snow, 0)),
        precip: finite(o.precip_mm, null),
        code: weatherApiCode(finite(o.condition && o.condition.code, 0)),
        humidity: finite(o.humidity, null),
        wind: finite(o.wind_kph, null),
        pressure: finite(o.pressure_mb, null),
        cloud: finite(o.cloud, null),
        uv: finite(o.uv, null),
      });
    }
  }
  const m = STAMP.exec(String(location.localtime));
  if (!m) throw new Error("shape");
  out.current = {
    time: m[1] + "T" + m[2].padStart(2, "0") + ":" + m[3],
    temp: finite(cur.temp_c, null),
    feels: finite(cur.feelslike_c, null),
    humidity: finite(cur.humidity, null),
    precip: finite(cur.precip_mm, 0),
    code: weatherApiCode(finite(cur.condition && cur.condition.code, 0)),
    wind: finite(cur.wind_kph, null),
    isDay: finite(cur.is_day, 1),
  };
  return out;
}

function parseOpenWeather(root) {
  const out = { current: null, hours: new Map(), days: new Map() };
  const cur = root && root.current;
  if (!cur) throw new Error("shape");
  const offset = finite(root.timezone_offset, 0);
  const id = (o) => finite(o && Array.isArray(o.weather) && o.weather[0] && o.weather[0].id, 804);
  const wet = (o) => finite(o && o.rain && o.rain["1h"], 0) + finite(o && o.snow && o.snow["1h"], 0);
  for (const o of (Array.isArray(root.hourly) ? root.hourly : []).slice(0, MAX_HOURS)) {
    const key = hourKey(localIso(finite(o.dt, 0), offset));
    if (!key) continue;
    out.hours.set(key, {
      temp: finite(o.temp, null),
      prob: o.pop === undefined ? null : Math.round(finite(o.pop, 0) * 100),
      precip: wet(o),
      code: openWeatherCode(id(o)),
      humidity: finite(o.humidity, null),
      wind: o.wind_speed === undefined ? null : finite(o.wind_speed, 0) * 3.6,
      pressure: finite(o.pressure, null),
      cloud: finite(o.clouds, null),
      uv: finite(o.uvi, null),
    });
  }
  for (const d of (Array.isArray(root.daily) ? root.daily : []).slice(0, 8)) {
    const key = dayKey(localIso(finite(d.dt, 0), offset));
    if (!key) continue;
    out.days.set(key, {
      max: finite(d.temp && d.temp.max, null),
      min: finite(d.temp && d.temp.min, null),
      code: openWeatherCode(id(d)),
      precip: finite(d.rain, 0) + finite(d.snow, 0),
      prob: Math.round(finite(d.pop, 0) * 100),
    });
  }
  const time = localIso(finite(cur.dt, 0), offset);
  if (!STAMP.test(time)) throw new Error("shape");
  out.current = {
    time,
    temp: finite(cur.temp, null),
    feels: finite(cur.feels_like, null),
    humidity: finite(cur.humidity, null),
    precip: wet(cur),
    code: openWeatherCode(id(cur)),
    wind: cur.wind_speed === undefined ? null : finite(cur.wind_speed, 0) * 3.6,
    isDay: 1,
  };
  return out;
}

function parseVisual(root) {
  const out = { current: null, hours: new Map(), days: new Map() };
  const cur = root && root.currentConditions;
  if (!cur || !Array.isArray(root.days)) throw new Error("shape");
  const offset = finite(root.tzoffset, 0) * 3600;
  for (const d of root.days.slice(0, 8)) {
    const key = dayKey(d.datetime);
    if (!key) continue;
    const precip = finite(d.precip, 0);
    out.days.set(key, {
      max: finite(d.tempmax, null),
      min: finite(d.tempmin, null),
      code: crossingCode(d.icon, precip, true),
      precip,
      prob: Math.round(finite(d.precipprob, 0)),
    });
    for (const o of (Array.isArray(d.hours) ? d.hours : []).slice(0, 48)) {
      const hk = hourKey(key + "T" + String(o.datetime || "00:00").slice(0, 5));
      if (!hk) continue;
      const hourPrecip = finite(o.precip, 0);
      out.hours.set(hk, {
        temp: finite(o.temp, null),
        prob: o.precipprob === undefined ? null : Math.round(finite(o.precipprob, 0)),
        precip: hourPrecip,
        code: crossingCode(o.icon, hourPrecip, false),
        humidity: finite(o.humidity, null),
        wind: finite(o.windspeed, null),
        pressure: finite(o.pressure, null),
        cloud: finite(o.cloudcover, null),
        uv: finite(o.uvindex, null),
      });
    }
  }
  const time = localIso(finite(cur.datetimeEpoch, 0), offset);
  if (!STAMP.test(time)) throw new Error("shape");
  out.current = {
    time,
    temp: finite(cur.temp, null),
    feels: finite(cur.feelslike, null),
    humidity: finite(cur.humidity, null),
    precip: finite(cur.precip, 0),
    code: crossingCode(cur.icon, finite(cur.precip, 0), false),
    wind: finite(cur.windspeed, null),
    isDay: 1,
  };
  return out;
}

const PARSERS = {
  openmeteo: parseOpenMeteo,
  weather: parseWeatherApi,
  openweather: parseOpenWeather,
  visual: parseVisual,
};

export function normalizeProvider(provider, text) {
  const parser = PARSERS[provider];
  if (!parser || typeof text !== "string") return null;
  try {
    return parser(JSON.parse(text));
  } catch (err) {
    return null;
  }
}

function collect(sources, field) {
  const keys = new Set();
  for (const source of sources) for (const key of source[field].keys()) keys.add(key);
  return Array.from(keys).sort();
}

function combineHour(items) {
  const wet = items.filter((i) => (i.precip || 0) >= WET_HOUR_MM || wetCode(i.code === null ? 0 : i.code));
  const dry = items.filter((i) => !wet.includes(i));
  const rainy = wet.length >= requiredVotes(items.length);
  const voters = rainy ? wet : dry;
  const prob = mean(items.map((i) => i.prob), null);
  const precip = mean(voters.map((i) => i.precip || 0), 0);
  const probOut = prob === null ? (rainy ? WET_PROB_FLOOR : 0) : Math.round(prob);
  return {
    temp: mean(items.map((i) => i.temp), 0),
    prob: rainy ? Math.max(probOut, WET_PROB_FLOOR) : Math.min(probOut, DRY_PROB_CEILING),
    precip: rainy ? Math.max(WET_HOUR_MM, precip) : precip,
    code: mode(voters.map((i) => (i.code === null ? 3 : i.code)), 3),
    humidity: mean(items.map((i) => i.humidity), 0),
    wind: mean(items.map((i) => i.wind), 0),
    pressure: mean(items.map((i) => i.pressure), 1013),
    cloud: mean(items.map((i) => i.cloud), 0),
    uv: mean(items.map((i) => i.uv), 0),
  };
}

function combineDay(items) {
  const wet = items.filter((i) => (i.precip || 0) >= WET_DAY_MM || wetCode(i.code === null ? 0 : i.code));
  const dry = items.filter((i) => !wet.includes(i));
  const rainy = wet.length >= requiredVotes(items.length);
  const voters = rainy ? wet : dry;
  const prob = Math.round(mean(items.map((i) => i.prob), 0));
  const precip = mean(voters.map((i) => i.precip || 0), 0);
  return {
    max: mean(items.map((i) => i.max), 0),
    min: mean(items.map((i) => i.min), 0),
    code: mode(voters.map((i) => (i.code === null ? 3 : i.code)), 3),
    precip: rainy ? Math.max(WET_HOUR_MM, precip) : precip,
    prob: rainy ? Math.max(prob, WET_PROB_FLOOR) : Math.min(prob, DRY_PROB_CEILING),
  };
}

function combineCurrent(items) {
  const wet = items.filter((i) => (i.precip || 0) >= WET_HOUR_MM || wetCode(i.code));
  const rainy = wet.length >= requiredVotes(items.length);
  const chosen = (rainy ? wet : items.filter((i) => !wet.includes(i)))[0] || items[0];
  return {
    time: chosen.time,
    temperature_2m: mean(items.map((i) => i.temp), 0),
    apparent_temperature: mean(items.map((i) => i.feels), 0),
    relative_humidity_2m: mean(items.map((i) => i.humidity), 0),
    precipitation: rainy ? Math.max(WET_HOUR_MM, mean(wet.map((i) => i.precip || 0), 0)) : mean(items.filter((i) => !wet.includes(i)).map((i) => i.precip || 0), 0),
    weather_code: chosen.code,
    wind_speed_10m: mean(items.map((i) => i.wind), 0),
    is_day: chosen.isDay,
  };
}

export function aggregateForAgent(entries) {
  const ordered = PROVIDER_ORDER.map((name) => entries.find((e) => e && e.provider === name)).filter(Boolean);
  if (!ordered.length) throw new Error("no_provider");
  const sources = ordered.map((e) => e.data);
  const hourKeys = collect(sources, "hours");
  const dayKeys = collect(sources, "days");
  const hours = hourKeys.map((key) => combineHour(sources.filter((s) => s.hours.has(key)).map((s) => s.hours.get(key))));
  const days = dayKeys.map((key) => combineDay(sources.filter((s) => s.days.has(key)).map((s) => s.days.get(key))));
  const currentItems = sources.slice(0, CURRENT_SOURCES).map((s) => s.current);
  return {
    current: combineCurrent(currentItems),
    hourly: {
      time: hourKeys,
      temperature_2m: hours.map((h) => h.temp),
      precipitation_probability: hours.map((h) => h.prob),
      precipitation: hours.map((h) => h.precip),
      weather_code: hours.map((h) => h.code),
      relative_humidity_2m: hours.map((h) => h.humidity),
      wind_speed_10m: hours.map((h) => h.wind),
      pressure_msl: hours.map((h) => h.pressure),
      cloud_cover: hours.map((h) => h.cloud),
      uv_index: hours.map((h) => h.uv),
    },
    daily: {
      time: dayKeys,
      weather_code: days.map((d) => d.code),
      temperature_2m_max: days.map((d) => d.max),
      temperature_2m_min: days.map((d) => d.min),
      precipitation_sum: days.map((d) => d.precip),
      precipitation_probability_max: days.map((d) => d.prob),
    },
    providers: ordered.length,
  };
}
