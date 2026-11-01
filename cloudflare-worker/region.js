const DEFAULT_BLOCKED_COUNTRIES = ["JP", "IR", "CU", "KP", "SY", "CN", "RU"];
const BLOCKED_SUBDIVISIONS = { UA: ["43", "40", "14", "09"] };

export const REGION_MESSAGE = "Our service is not available in your region.";

export function resolveRegion(request) {
  const cf = request.cf || {};
  const raw = typeof cf.country === "string" && cf.country ? cf.country : request.headers.get("CF-IPCountry") || "";
  return {
    country: raw.trim().toUpperCase(),
    subdivision: String(cf.regionCode || "").trim().toUpperCase(),
  };
}

function blockedCountries(env) {
  const configured = env && typeof env.BLOCKED_COUNTRIES === "string" ? env.BLOCKED_COUNTRIES : "";
  const list = configured
    .split(",")
    .map((item) => item.trim().toUpperCase())
    .filter((item) => /^[A-Z]{2}$/.test(item));
  return list.length > 0 ? list : DEFAULT_BLOCKED_COUNTRIES;
}

export function isRegionBlocked(request, env) {
  const region = resolveRegion(request);
  if (!/^[A-Z]{2}$/.test(region.country)) return false;
  if (blockedCountries(env).includes(region.country)) return true;
  const subdivisions = BLOCKED_SUBDIVISIONS[region.country];
  return Boolean(subdivisions && subdivisions.includes(region.subdivision));
}
