package dk.opmeddig

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var clock: TextView
    private lateinit var timeText: TextView
    private lateinit var refImage: ImageView
    private lateinit var status: TextView
    private lateinit var armBtn: Button
    private lateinit var countBtns: Map<Int, Button>
    private lateinit var dayBtns: List<Button>
    private lateinit var langBtns: Map<String, Button>

    private var hour = 7
    private var minute = 0
    private var count = 5

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    private val takeRef = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            PhotoMatch.saveRef(this, bmp)
            refImage.setImageBitmap(bmp)
            refresh()
        }
    }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        clock = findViewById(R.id.clock)
        timeText = findViewById(R.id.timeText)
        refImage = findViewById(R.id.refImage)
        status = findViewById(R.id.status)
        armBtn = findViewById(R.id.armBtn)
        refImage.clipToOutline = true

        hour = Prefs.hour(this)
        minute = Prefs.minute(this)
        count = Prefs.count(this)

        timeText.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h; minute = m
                saveSettings(Prefs.armed(this))
                if (Prefs.armed(this)) Scheduler.scheduleNext(this)
                refresh()
            }, hour, minute, true).show()
        }

        countBtns = mapOf(
            3 to findViewById(R.id.count3),
            5 to findViewById(R.id.count5),
            8 to findViewById(R.id.count8)
        )
        countBtns.forEach { (n, b) ->
            b.setOnClickListener { count = n; saveSettings(Prefs.armed(this)); refresh() }
        }

        // Ugedage, mandag først
        val daysRow = findViewById<LinearLayout>(R.id.days)
        val gap = (6 * resources.displayMetrics.density).toInt()
        dayBtns = resources.getStringArray(R.array.day_letters).toList().mapIndexed { i, label ->
            Button(this, null, 0, R.style.OMD_Button_Ghost).apply {
                text = label
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, (48 * resources.displayMetrics.density).toInt(), 1f)
                    .apply { if (i > 0) marginStart = gap }
                setOnClickListener {
                    val now = Prefs.days(this@MainActivity) xor (1 shl i)
                    if (now == 0) {
                        toast(getString(R.string.toast_one_day))
                    } else {
                        Prefs.setDays(this@MainActivity, now)
                        if (Prefs.armed(this@MainActivity)) Scheduler.scheduleNext(this@MainActivity)
                        refresh()
                    }
                }
                daysRow.addView(this)
            }
        }

        PhotoMatch.loadRef(this)?.let { refImage.setImageBitmap(it) }
        findViewById<Button>(R.id.refBtn).setOnClickListener { takeRef.launch(null) }

        armBtn.setOnClickListener {
            if (Prefs.armed(this)) {
                Prefs.setArmed(this, false)
                Scheduler.cancel(this)
            } else if (ready()) {
                saveSettings(true)
                Scheduler.scheduleNext(this)
            }
            refresh()
        }

        findViewById<Button>(R.id.testBtn).setOnClickListener {
            if (ready()) {
                saveSettings(Prefs.armed(this))
                Scheduler.scheduleAt(this, System.currentTimeMillis() + 60_000)
                status.text = getString(R.string.status_test)
            }
        }

        findViewById<Button>(R.id.setupBtn).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        // Sprogvalg: AppCompat gemmer valget og viser skærmen igen på det nye sprog
        langBtns = mapOf("da" to findViewById(R.id.langDa), "en" to findViewById(R.id.langEn))
        langBtns.forEach { (tag, b) ->
            b.setOnClickListener {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }

        if (!SetupActivity.isDone(this)) startActivity(Intent(this, SetupActivity::class.java))
        else if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        refresh()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun saveSettings(armed: Boolean) = Prefs.save(this, hour, minute, count, armed)

    private fun ready(): Boolean {
        if (PhotoMatch.loadRef(this) == null) {
            toast(getString(R.string.toast_need_ref))
            return false
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            toast(getString(R.string.toast_fullscreen))
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun locale(): Locale = resources.configuration.locales[0]

    private fun styleToggle(b: Button, on: Boolean, fg: Int) {
        b.setBackgroundResource(if (on) R.drawable.bg_button else R.drawable.bg_button_ghost)
        b.setTextColor(if (on) 0xFFFFFFFF.toInt() else fg)
    }

    private fun refresh() {
        timeText.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

        val accent = ContextCompat.getColor(this, R.color.accent)
        val fg = ContextCompat.getColor(this, R.color.fg)
        countBtns.forEach { (n, b) -> styleToggle(b, n == count, fg) }

        val days = Prefs.days(this)
        dayBtns.forEachIndexed { i, b -> styleToggle(b, days and (1 shl i) != 0, fg) }

        val lang = if (locale().language == "da") "da" else "en"
        langBtns.forEach { (tag, b) -> styleToggle(b, tag == lang, fg) }

        if (Prefs.armed(this)) {
            val f = SimpleDateFormat(getString(R.string.next_format), locale())
            val hex = String.format("#%06X", 0xFFFFFF and accent)
            status.text = Html.fromHtml(
                getString(R.string.status_armed, hex, f.format(Date(Scheduler.nextTime(this)))),
                Html.FROM_HTML_MODE_LEGACY
            )
            armBtn.text = getString(R.string.btn_disarm)
        } else {
            status.text = getString(
                if (PhotoMatch.loadRef(this) == null) R.string.status_need_ref else R.string.status_none
            )
            armBtn.text = getString(R.string.btn_arm)
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
