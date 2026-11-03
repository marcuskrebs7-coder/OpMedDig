package dk.opmeddig

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c, 1, Intent(c, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Mandag = 0 … søndag = 6 */
    fun dayIndex(cal: Calendar) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    /** Næste gang klokken er den valgte tid på en af de valgte ugedage. */
    fun nextTime(c: Context): Long {
        val days = Prefs.days(c).takeIf { it != 0 } ?: 0b1111111
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Prefs.hour(c))
            set(Calendar.MINUTE, Prefs.minute(c))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        repeat(7) {
            if (days and (1 shl dayIndex(cal)) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** setAlarmClock ringer præcist, også i dvaletilstand, og viser alarm-ikonet i statuslinjen. */
    fun scheduleAt(c: Context, millis: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pending(c))
    }

    fun scheduleNext(c: Context) {
        if (Prefs.armed(c)) scheduleAt(c, nextTime(c))
    }

    fun cancel(c: Context) = c.getSystemService(AlarmManager::class.java).cancel(pending(c))
}
