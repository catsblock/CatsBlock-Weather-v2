package com.catsblock.weather.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.text.Normalizer
import java.util.Locale

private val Context.activityData by preferencesDataStore(name = "activity_stats")

data class TemperatureRecord(
    val celsius: Double,
    val date: LocalDate,
    val place: String
)

data class ActivityStats(
    val installedAtMillis: Long = 0L,
    val checkInDays: Int = 0,
    val observed: Map<Condition, Int> = emptyMap(),
    val coldest: TemperatureRecord? = null,
    val hottest: TemperatureRecord? = null,
    val rainyDays: Int = 0,
    val snowyDays: Int = 0,
    val hailDays: Int = 0
)

class ActivityStore(private val context: Context) {

    private object Keys {
        val activityName = stringPreferencesKey("activity_city_name")
        val activityRegion = stringPreferencesKey("activity_city_region")
        val activityCountry = stringPreferencesKey("activity_city_country")
        val activityCode = stringPreferencesKey("activity_city_code")
        val activityLat = doublePreferencesKey("activity_city_lat")
        val activityLon = doublePreferencesKey("activity_city_lon")
        val checkIns = stringSetPreferencesKey("check_in_days")
        val coldest = stringPreferencesKey("coldest")
        val hottest = stringPreferencesKey("hottest")
        fun days(condition: Condition) = stringSetPreferencesKey("condition_days_" + condition.name)
    }

    val configuredCity: Flow<City?> = context.activityData.data.map { prefs ->
        val lat = prefs[Keys.activityLat] ?: return@map null
        val lon = prefs[Keys.activityLon] ?: return@map null
        City(prefs[Keys.activityName] ?: return@map null, prefs[Keys.activityRegion].orEmpty(),
            prefs[Keys.activityCountry].orEmpty(), prefs[Keys.activityCode].orEmpty(), lat, lon)
    }

    suspend fun setConfiguredCity(city: City) {
        if (!validCity(city)) return
        context.activityData.edit { prefs ->
            prefs[Keys.activityName] = city.name.trim()
            prefs[Keys.activityRegion] = city.region.trim()
            prefs[Keys.activityCountry] = city.country.trim()
            prefs[Keys.activityCode] = city.code.trim().uppercase(Locale.ROOT)
            prefs[Keys.activityLat] = city.lat
            prefs[Keys.activityLon] = city.lon
        }
    }

    suspend fun isConfiguredCity(city: City?): Boolean {
        val configured = configuredCity.first() ?: return false
        return matchesActivityCity(configured, city)
    }

    private fun validCity(city: City): Boolean = city.name.isNotBlank() && city.code.trim().length == 2 &&
        city.lat.isFinite() && city.lon.isFinite() && city.lat in -90.0..90.0 && city.lon in -180.0..180.0

    private fun canonical(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "").lowercase(Locale.ROOT).replace("[^\\p{L}\\p{N}]+".toRegex(), "")

    private val rainKinds = setOf(Condition.Drizzle, Condition.Rain, Condition.HeavyRain, Condition.Storm,
        Condition.ScatteredShowers, Condition.ScatteredStorms, Condition.IsolatedStorms, Condition.StrongStorms
    )
    private val snowKinds = setOf(
        Condition.Snow, Condition.HeavySnow, Condition.Blizzard, Condition.Flurries, Condition.BlowingSnow,
        Condition.ScatteredSnow, Condition.SnowShowers
    )

    val stats: Flow<ActivityStats> = context.activityData.data.map { prefs ->
        val observed = Condition.entries
            .associateWith { prefs[Keys.days(it)]?.size ?: 0 }
            .filterValues { it > 0 }
        ActivityStats(
            installedAtMillis = installTime(),
            checkInDays = prefs[Keys.checkIns]?.size ?: 0,
            observed = observed,
            coldest = readRecord(prefs, Keys.coldest),
            hottest = readRecord(prefs, Keys.hottest),
            rainyDays = unionSize(prefs, rainKinds),
            snowyDays = unionSize(prefs, snowKinds),
            hailDays = prefs[Keys.days(Condition.Hail)]?.size ?: 0
        )
    }

    suspend fun checkIn(city: City?) {
        if (!isConfiguredCity(city)) return
        val today = LocalDate.now().toString()
        context.activityData.edit { prefs ->
            val days = prefs[Keys.checkIns].orEmpty()
            if (today !in days) prefs[Keys.checkIns] = days + today
        }
    }

    suspend fun record(city: City, bundle: WeatherBundle) {
        if (!isConfiguredCity(city)) return
        val now = bundle.current.now
        val today = now.toLocalDate()
        val key = today.toString()
        val past = bundle.hours.filter { it.time.toLocalDate() == today && !it.time.isAfter(now) }
        val kinds = (past.map { it.kind } + bundle.current.kind).toSet()
        val temps = past.map { it.temp } + bundle.current.temp
        val low = temps.min()
        val high = temps.max()
        context.activityData.edit { prefs ->
            kinds.forEach { kind ->
                val days = prefs[Keys.days(kind)].orEmpty()
                if (key !in days) prefs[Keys.days(kind)] = days + key
            }
            val cold = readRecord(prefs, Keys.coldest)
            if (cold == null || low < cold.celsius) prefs[Keys.coldest] = encode(TemperatureRecord(low, today, city.name))
            val hot = readRecord(prefs, Keys.hottest)
            if (hot == null || high > hot.celsius) prefs[Keys.hottest] = encode(TemperatureRecord(high, today, city.name))
        }
    }

    private fun unionSize(prefs: Preferences, kinds: Set<Condition>): Int =
        kinds.flatMap { prefs[Keys.days(it)].orEmpty() }.toSet().size

    private fun installTime(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)

    private fun encode(record: TemperatureRecord): String =
        listOf(record.celsius.toString(), record.date.toString(), record.place).joinToString("|")

    private fun readRecord(prefs: Preferences, key: Preferences.Key<String>): TemperatureRecord? {
        val parts = prefs[key]?.split("|", limit = 3) ?: return null
        if (parts.size < 3) return null
        return runCatching {
            TemperatureRecord(parts[0].toDouble(), LocalDate.parse(parts[1]), parts[2])
        }.getOrNull()
    }
}

internal fun matchesActivityCity(configured: City?, candidate: City?): Boolean {
    if (configured == null || candidate == null) return false
    if (!validActivityCity(configured) || !validActivityCity(candidate)) return false
    if (configured.code.trim().uppercase(Locale.ROOT) != candidate.code.trim().uppercase(Locale.ROOT)) return false
    if (canonicalActivityField(configured.name) != canonicalActivityField(candidate.name)) return false
    if (canonicalActivityField(configured.region) != canonicalActivityField(candidate.region)) return false
    val scale = kotlin.math.cos(Math.toRadians((configured.lat + candidate.lat) / 2.0)).coerceAtLeast(0.1)
    val north = (configured.lat - candidate.lat) * 111_320.0
    val east = (configured.lon - candidate.lon) * 111_320.0 * scale
    return north * north + east * east <= 5_000.0 * 5_000.0
}

private fun validActivityCity(city: City): Boolean = city.name.isNotBlank() && city.code.trim().length == 2 &&
    city.lat.isFinite() && city.lon.isFinite() && city.lat in -90.0..90.0 && city.lon in -180.0..180.0

private fun canonicalActivityField(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "").lowercase(Locale.ROOT).replace("[^\\p{L}\\p{N}]+".toRegex(), "")
