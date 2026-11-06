package dk.opmeddig

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/** Én alarm. days er en bitmaske: bit 0 = mandag … bit 6 = søndag. */
data class Alarm(
    val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val enabled: Boolean
) {
    fun timeText() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** Næste tidspunkt denne alarm ringer, efter `from`. */
    fun nextAfter(from: Long): Long {
        val d = days.takeIf { it != 0 } ?: ALL_DAYS
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        repeat(7) {
            if (d and (1 shl Scheduler.dayIndex(cal)) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun toJson() = JSONObject().put("id", id).put("name", name).put("hour", hour)
        .put("minute", minute).put("days", days).put("enabled", enabled)

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        fun fromJson(o: JSONObject) = Alarm(
            o.getInt("id"), o.optString("name", ""), o.getInt("hour"), o.getInt("minute"),
            o.optInt("days", ALL_DAYS), o.optBoolean("enabled", true)
        )
    }
}

/** Alle alarmer, gemt som JSON i appens indstillinger. */
object Alarms {
    private fun p(c: Context) = c.getSharedPreferences("opmeddig", Context.MODE_PRIVATE)

    fun all(c: Context): List<Alarm> {
        val raw = p(c).getString("alarms", null)
        if (raw == null) return migrate(c)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun get(c: Context, id: Int) = all(c).firstOrNull { it.id == id }

    private fun saveAll(c: Context, list: List<Alarm>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        p(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun save(c: Context, a: Alarm) = saveAll(c, all(c).filter { it.id != a.id } + a)

    fun delete(c: Context, id: Int) = saveAll(c, all(c).filter { it.id != id })

    fun newId(c: Context) = (all(c).maxOfOrNull { it.id } ?: 0) + 1

    /** Første gang efter opdatering: lav én alarm ud af de gamle indstillinger. */
    private fun migrate(c: Context): List<Alarm> {
        val list = listOf(
            Alarm(1, "", Prefs.hour(c), Prefs.minute(c), Prefs.days(c), Prefs.armed(c))
        )
        saveAll(c, list)
        return list
    }

    /** "Hverdage", "Weekend", "Hver dag" eller fx "man, ons, fre". */
    fun daysText(c: Context, days: Int): String = when (days) {
        Alarm.ALL_DAYS -> c.getString(R.string.days_all)
        Alarm.WEEKDAYS -> c.getString(R.string.days_weekdays)
        Alarm.WEEKEND -> c.getString(R.string.days_weekend)
        else -> {
            val names = DateFormatSymbols(c.resources.configuration.locales[0]).shortWeekdays
            // shortWeekdays er indekseret med Calendar.SUNDAY = 1 … SATURDAY = 7
            val order = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
            order.filterIndexed { i, _ -> days and (1 shl i) != 0 }
                .joinToString(", ") { names[it].trimEnd('.') }
        }
    }
}
