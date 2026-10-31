package com.catsblock.weather.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
        val checkIns = stringSetPreferencesKey("check_in_days")
        val coldest = stringPreferencesKey("coldest")
        val hottest = stringPreferencesKey("hottest")
        fun days(condition: Condition) = stringSetPreferencesKey("condition_days_" + condition.name)
    }

    private val rainKinds = setOf(Condition.Drizzle, Condition.Rain, Condition.Storm)
    private val snowKinds = setOf(Condition.Snow, Condition.HeavySnow, Condition.Blizzard)

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

    suspend fun checkIn() {
        val today = LocalDate.now().toString()
        context.activityData.edit { prefs ->
            val days = prefs[Keys.checkIns].orEmpty()
            if (today !in days) prefs[Keys.checkIns] = days + today
        }
    }

    suspend fun record(city: City, bundle: WeatherBundle) {
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
