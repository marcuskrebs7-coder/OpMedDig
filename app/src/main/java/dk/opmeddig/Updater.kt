package dk.opmeddig

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Selvopdatering fra GitHub-releases.
 * Tjekker https://api.github.com/repos/<REPO>/releases/latest, og hvis tag'et (fx "v1.4")
 * er nyere end den installerede version, hentes OpMedDig.apk og installeres via PackageInstaller.
 * På Android 12+ sker det uden spørgsmål, når appen selv stod for forrige installation.
 */
object Updater {
    private const val TAG = "OpMedDigUpdate"
    private const val REPO = "marcuskrebs7-coder/OpMedDig"
    private const val ASSET = "OpMedDig.apk"
    private const val CHANNEL = "updates"

    /** Planlæg tjek: hver 12. time + én gang nu. Kræver internet. */
    fun schedule(c: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork(
            "update-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).setConstraints(net).build()
        )
        wm.enqueueUniqueWork(
            "update-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(net).build()
        )
    }

    fun installedVersion(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"

    /** "1.10" > "1.9" */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Kører på baggrundstråd. Returnerer true hvis en installation blev startet. */
    fun checkAndInstall(c: Context): Boolean {
        if (Prefs.ringing(c)) return false   // aldrig midt i en alarm

        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpMedDig-Android")
            connectTimeout = 15_000; readTimeout = 15_000
        }
        if (conn.responseCode != 200) { Log.w(TAG, "GitHub svarede ${conn.responseCode}"); return false }
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val tag = json.getString("tag_name")
        Prefs.setLatestTag(c, tag)
        if (!isNewer(tag, installedVersion(c))) return false

        val assets = json.getJSONArray("assets")
        val url = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name") == ASSET }?.getString("browser_download_url") ?: return false

        val apk = File(c.cacheDir, "update.apk")
        URL(url).openStream().use { input -> apk.outputStream().use { input.copyTo(it) } }
        install(c, apk)
        return true
    }

    private fun install(c: Context, apk: File) {
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("app", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                c, 10, Intent(c, UpdateResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(result.intentSender)
        }
    }

    /** Når Android kræver et tryk (første gang), vises en notifikation. */
    fun notifyNeedsTap(c: Context, confirm: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.upd_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(
            c, 11, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            2, NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(c.getString(R.string.upd_title))
                .setContentText(c.getString(R.string.upd_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}

class UpdateWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = try {
        Updater.checkAndInstall(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Log.w("OpMedDigUpdate", "Opdatering fejlede", e)
        Result.retry()
    }
}

class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else i.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) Updater.notifyNeedsTap(c, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // appen genstartes; PackageReplaced sætter alarmen igen
            else -> Log.w("OpMedDigUpdate", "Installation: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
