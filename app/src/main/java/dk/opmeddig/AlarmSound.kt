package dk.opmeddig

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Alarmlyden. Valgmuligheder:
 *  - en af sangene der følger med appen (res/raw/song_*.m4a)
 *  - "custom": en lydfil brugeren selv har valgt (kopieres ind i appen)
 *  - "phone": telefonens egen alarmtone
 */
object AlarmSound {
    /** Mulige indbyggede sange: valg-id → ressourcenavn og visningsnavn. */
    private val CANDIDATES = listOf(
        Triple("good_morning", "song_good_morning", "Good morning"),
        Triple("barbie_girl", "song_barbie_girl", "Barbie girl")   // kun i GitHub-udgaven
    )

    /** Sange der faktisk følger med denne udgave: valg-id → rå ressource og visningsnavn. */
    lateinit var BUILT_IN: List<Triple<String, Int, String>>
        private set

    fun init(c: Context) {
        if (::BUILT_IN.isInitialized) return
        BUILT_IN = CANDIDATES.mapNotNull { (id, res, name) ->
            val rid = c.resources.getIdentifier(res, "raw", c.packageName)
            if (rid != 0) Triple(id, rid, name) else null
        }
    }
    const val CUSTOM = "custom"
    const val PHONE = "phone"

    private fun file(c: Context) = File(c.filesDir, "alarm_sound")
    private fun prefs(c: Context) = c.getSharedPreferences("opmeddig", Context.MODE_PRIVATE)

    fun hasCustom(c: Context) = file(c).exists() && file(c).length() > 0
    fun customName(c: Context) = prefs(c).getString("soundName", "") ?: ""

    /** Nuværende valg. Standard er den første indbyggede sang. */
    fun choice(c: Context): String {
        init(c)
        val v = prefs(c).getString("soundChoice", null)
            ?.takeIf { it == CUSTOM || it == PHONE || BUILT_IN.any { b -> b.first == it } }
        // Ældre version: havde man valgt en fil, bruges den stadig
        return v ?: if (hasCustom(c)) CUSTOM else BUILT_IN.first().first
    }

    fun setChoice(c: Context, v: String) = prefs(c).edit().putString("soundChoice", v).apply()

    /** Kopiér den valgte fil ind i appen og vælg den. Returnerer false hvis den ikke kan afspilles. */
    fun importFrom(c: Context, uri: Uri): Boolean {
        val tmp = File(c.filesDir, "alarm_sound.tmp")
        c.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return false
        val ok = try {
            MediaPlayer().apply { setDataSource(tmp.path); prepare(); release() }
            true
        } catch (e: Exception) { false }
        if (!ok) { tmp.delete(); return false }
        file(c).delete()
        tmp.renameTo(file(c))
        prefs(c).edit().putString("soundName", displayName(c, uri)).putString("soundChoice", CUSTOM).apply()
        return true
    }

    /** Navn på det nuværende valg til forsiden. */
    fun label(c: Context): String = when (val ch = choice(c)) {
        CUSTOM -> if (hasCustom(c)) customName(c) else c.getString(R.string.sound_default)
        PHONE -> c.getString(R.string.sound_default)
        else -> BUILT_IN.firstOrNull { it.first == ch }?.third ?: c.getString(R.string.sound_default)
    }

    /**
     * Gør en MediaPlayer klar med det valgte. Fejler det, bruges telefonens alarmtone.
     * Kaldes før prepare().
     */
    fun setSource(c: Context, mp: MediaPlayer) {
        val ch = choice(c)
        try {
            when {
                ch == CUSTOM && hasCustom(c) -> { mp.setDataSource(file(c).path); return }
                ch != PHONE -> {
                    val raw = BUILT_IN.firstOrNull { it.first == ch }?.second
                    if (raw != null) {
                        c.resources.openRawResourceFd(raw).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            mp.reset(); mp.setAudioAttributes(alarmAttributes())
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mp.setDataSource(c, uri)
    }

    fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun displayName(c: Context, uri: Uri): String =
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Egen lyd"
}
