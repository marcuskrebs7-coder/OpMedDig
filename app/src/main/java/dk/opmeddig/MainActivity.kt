package dk.opmeddig

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var clock: TextView
    private lateinit var refImage: ImageView
    private lateinit var status: TextView
    private lateinit var alarmList: LinearLayout
    private lateinit var countBtns: Map<Int, Button>
    private lateinit var typeBtns: Map<ChallengeType, Button>
    private lateinit var langBtns: Map<String, Button>

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
        refImage = findViewById(R.id.refImage)
        status = findViewById(R.id.status)
        alarmList = findViewById(R.id.alarmList)
        refImage.clipToOutline = true

        findViewById<Button>(R.id.addAlarmBtn).setOnClickListener {
            if (ready()) startActivity(Intent(this, AlarmEditActivity::class.java))
        }

        // Antal opgaver
        val countRow = findViewById<LinearLayout>(R.id.counts)
        countBtns = listOf(1, 2, 3, 5, 8).mapIndexed { i, n ->
            n to toggleButton(n.toString(), countRow, i) { Prefs.setCount(this, n); refresh() }
        }.toMap()

        // Opgavetyper, to pr. række
        val typeLabels = listOf(
            ChallengeType.MATH to R.string.ch_name_math, ChallengeType.DOTS to R.string.ch_name_dots,
            ChallengeType.MEMORY to R.string.ch_name_memory, ChallengeType.TYPING to R.string.ch_name_typing
        )
        typeBtns = typeLabels.mapIndexed { i, (type, label) ->
            val row = findViewById<LinearLayout>(if (i < 2) R.id.taskTypes1 else R.id.taskTypes2)
            type to toggleButton(getString(label), row, i % 2) {
                val now = Prefs.challenges(this) xor type.bit
                if (now == 0) toast(getString(R.string.toast_one_task))
                else { Prefs.setChallenges(this, now); refresh() }
            }
        }.toMap()

        PhotoMatch.loadRef(this)?.let { refImage.setImageBitmap(it) }
        findViewById<Button>(R.id.refBtn).setOnClickListener { takeRef.launch(null) }

        findViewById<Button>(R.id.testBtn).setOnClickListener {
            if (ready()) {
                Scheduler.scheduleAt(this, System.currentTimeMillis() + 60_000, getString(R.string.test_name))
                status.text = getString(R.string.status_test)
            }
        }

        findViewById<Button>(R.id.setupBtn).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        // Sprogvalg: AppCompat gemmer valget og viser skærmen igen på det nye sprog
        langBtns = mapOf("da" to findViewById(R.id.langDa), "en" to findViewById(R.id.langEn))
        langBtns.forEach { (tag, b) ->
            b.setOnClickListener { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        }

        // Opdateringer: tjek i baggrunden; knappen vises, hvis der ligger en nyere version
        Updater.schedule(this)
        findViewById<Button>(R.id.updateBtn).setOnClickListener {
            if (!packageManager.canRequestPackageInstalls()) {
                toast(getString(R.string.upd_need_permission))
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else {
                it.isEnabled = false
                (it as Button).text = getString(R.string.upd_downloading)
                Thread {
                    val started = try { Updater.checkAndInstall(this) } catch (e: Exception) { false }
                    runOnUiThread { if (!started) { it.isEnabled = true; refresh() } }
                }.start()
            }
        }

        // Sørg for at systemalarmen passer med listen (fx efter opdatering fra en ældre version)
        Scheduler.scheduleNext(this)

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

    /** Lille knap der deler rækken ligeligt med sine naboer. */
    private fun toggleButton(label: String, row: LinearLayout, index: Int, onClick: () -> Unit): Button =
        Button(this, null, 0, R.style.OMD_Button_Ghost).apply {
            text = label
            minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (index > 0) marginStart = dp(8) }
            setOnClickListener { onClick() }
            row.addView(this)
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun locale(): Locale = resources.configuration.locales[0]

    private fun styleToggle(b: Button, on: Boolean, fg: Int) {
        b.setBackgroundResource(if (on) R.drawable.bg_button else R.drawable.bg_button_ghost)
        b.setTextColor(if (on) 0xFFFFFFFF.toInt() else fg)
    }

    /** Ét kort pr. alarm: tid, navn og dage til venstre (tryk for at rette), tænd/sluk til højre. */
    private fun renderAlarms() {
        alarmList.removeAllViews()
        val fg = getColor(R.color.fg)
        val muted = getColor(R.color.muted)
        val alarms = Alarms.all(this)
        if (alarms.isEmpty()) {
            alarmList.addView(TextView(this).apply {
                text = getString(R.string.no_alarms); setTextColor(muted); textSize = 15f
            })
            return
        }
        alarms.forEach { a ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding(dp(18), dp(14), dp(12), dp(14))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
                alpha = if (a.enabled) 1f else 0.55f
            }
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, AlarmEditActivity::class.java).putExtra(AlarmEditActivity.EXTRA_ID, a.id))
                }
            }
            left.addView(TextView(this).apply {
                text = a.timeText(); textSize = 34f; setTextColor(fg)
                typeface = Typeface.create("monospace", Typeface.BOLD)
            })
            val sub = listOf(a.name, Alarms.daysText(this, a.days)).filter { it.isNotBlank() }.joinToString(" · ")
            left.addView(TextView(this).apply { text = sub; textSize = 14f; setTextColor(muted) })
            card.addView(left)
            card.addView(SwitchCompat(this).apply {
                isChecked = a.enabled
                setOnCheckedChangeListener { btn, on ->
                    if (on && !ready()) { btn.isChecked = false; return@setOnCheckedChangeListener }
                    Alarms.save(this@MainActivity, a.copy(enabled = on))
                    Scheduler.scheduleNext(this@MainActivity)
                    refresh()
                }
            })
            alarmList.addView(card)
        }
    }

    private fun refresh() {
        val latest = Prefs.latestTag(this)
        val updateBtn = findViewById<Button>(R.id.updateBtn)
        val hasUpdate = latest.isNotEmpty() && Updater.isNewer(latest, Updater.installedVersion(this))
        updateBtn.visibility = if (hasUpdate) View.VISIBLE else View.GONE
        if (hasUpdate && updateBtn.isEnabled) updateBtn.text = getString(R.string.upd_button, latest.trimStart('v'))

        val accent = ContextCompat.getColor(this, R.color.accent)
        val fg = ContextCompat.getColor(this, R.color.fg)
        val count = Prefs.count(this)
        countBtns.forEach { (n, b) -> styleToggle(b, n == count, fg) }
        val types = Prefs.challenges(this)
        typeBtns.forEach { (t, b) -> styleToggle(b, types and t.bit != 0, fg) }
        val lang = if (locale().language == "da") "da" else "en"
        langBtns.forEach { (tag, b) -> styleToggle(b, tag == lang, fg) }

        renderAlarms()

        val next = Scheduler.next(this)
        status.text = when {
            next != null -> {
                val f = SimpleDateFormat(getString(R.string.next_format), locale())
                val hex = String.format("#%06X", 0xFFFFFF and accent)
                val whenText = f.format(Date(next.second)) +
                    (if (next.first.name.isNotBlank()) " (${next.first.name})" else "")
                Html.fromHtml(getString(R.string.status_next, hex, whenText), Html.FROM_HTML_MODE_LEGACY)
            }
            PhotoMatch.loadRef(this) == null -> getString(R.string.status_need_ref)
            else -> getString(R.string.status_none)
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
