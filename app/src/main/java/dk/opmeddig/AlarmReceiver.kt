package dk.opmeddig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.startForegroundService(Intent(c, AlarmService::class.java))
    }
}

/** Sætter alarmen igen efter genstart af telefonen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Prefs.setRinging(c, false)
            Scheduler.scheduleNext(c)
        }
    }
}
