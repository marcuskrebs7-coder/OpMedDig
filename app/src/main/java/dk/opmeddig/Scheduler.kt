package dk.opmeddig

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Der er kun én systemalarm ad gangen: den næste af alle aktive alarmer.
 * Når den har ringet og er slukket, planlægges den næste igen.
 */
object Scheduler {
    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c, 1, Intent(c, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Mandag = 0 … søndag = 6 */
    fun dayIndex(cal: Calendar) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    /** Den næste alarm der skal ringe, og hvornår. null hvis ingen er slået til. */
    fun next(c: Context): Pair<Alarm, Long>? {
        val now = System.currentTimeMillis()
        return Alarms.all(c).filter { it.enabled }
            .map { it to it.nextAfter(now) }
            .minByOrNull { it.second }
    }

    /** setAlarmClock ringer præcist, også i dvaletilstand, og viser alarm-ikonet i statuslinjen. */
    fun scheduleAt(c: Context, millis: Long, name: String, followUp: Boolean = false) {
        Prefs.setPendingName(c, name)
        Prefs.setNextIsFollowUp(c, followUp)
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pending(c))
    }

    /** Planlæg det der kommer først: en ventende ekstra vækning eller den næste almindelige alarm. */
    fun scheduleNext(c: Context) {
        val n = next(c)
        val f = Prefs.followUpAt(c)
        when {
            f > System.currentTimeMillis() && (n == null || f <= n.second) ->
                scheduleAt(c, f, Prefs.pendingName(c), followUp = true)
            n == null -> { Prefs.setNextIsFollowUp(c, false); cancel(c) }
            else -> scheduleAt(c, n.second, n.first.name)
        }
    }

    const val FOLLOW_UP_MS = 15 * 60_000L

    fun cancel(c: Context) = c.getSystemService(AlarmManager::class.java).cancel(pending(c))
}
