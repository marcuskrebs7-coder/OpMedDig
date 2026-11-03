package dk.opmeddig

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Vises første gang appen åbnes (og kan åbnes igen fra forsiden).
 * En tjekliste over det, man skal være obs på, med knapper der løser det.
 */
class SetupActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val pad = dp(20)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(28), pad, pad) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); addView(list) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        list.addView(text(getString(R.string.setup_title), 26f, bold = true))
        list.addView(text(getString(R.string.setup_intro), 15f, muted = true))

        if (Build.VERSION.SDK_INT >= 33) item(
            R.string.setup_notif_t, R.string.setup_notif_b,
            ok = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            button = R.string.setup_notif_btn
        ) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }

        if (Build.VERSION.SDK_INT >= 34) item(
            R.string.setup_fs_t, R.string.setup_fs_b,
            ok = getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            button = R.string.setup_fs_btn
        ) { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }

        item(
            R.string.setup_batt_t, R.string.setup_batt_b,
            ok = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            button = R.string.setup_batt_btn
        ) { requestBatteryExemption() }

        item(R.string.setup_vol_t, R.string.setup_vol_b)
        item(R.string.setup_ref_t, R.string.setup_ref_b)
        item(R.string.setup_test_t, R.string.setup_test_b)
        item(R.string.setup_nosnooze_t, R.string.setup_nosnooze_b)
        item(R.string.setup_upd_t, R.string.setup_upd_b)

        list.addView(Button(this).apply {
            text = getString(R.string.setup_go)
            layoutParams = LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(20) }
            setOnClickListener { markDone(this@SetupActivity); finish() }
        })
    }

    private fun item(title: Int, body: Int, ok: Boolean? = null, button: Int? = null, action: (() -> Unit)? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        val mark = when (ok) { true -> "✅ "; false -> "⚠️ "; null -> "• " }
        box.addView(text(mark + getString(title), 18f, bold = true))
        box.addView(text(getString(body), 15f, muted = true))
        if (ok == false && button != null && action != null) {
            box.addView(Button(this).apply {
                text = getString(button)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
                setOnClickListener { action() }
            })
        } else if (ok == true) {
            box.addView(text(getString(R.string.setup_done_ok), 14f, bold = true, color = getColor(R.color.ok)))
        }
        list.addView(box)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun text(t: String, size: Float, bold: Boolean = false, muted: Boolean = false, color: Int? = null) =
        TextView(this).apply {
            text = t
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(color ?: getColor(if (muted) R.color.muted else R.color.fg))
            setPadding(0, dp(4), 0, 0)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private fun prefs(c: Context) = c.getSharedPreferences("opmeddig", Context.MODE_PRIVATE)
        fun isDone(c: Context) = prefs(c).getBoolean("setupDone", false)
        fun markDone(c: Context) = prefs(c).edit().putBoolean("setupDone", true).apply()
    }
}
