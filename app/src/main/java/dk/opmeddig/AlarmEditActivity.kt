package dk.opmeddig

import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Opret eller ret én alarm: kaldenavn, tidspunkt og ugedage. */
class AlarmEditActivity : AppCompatActivity() {
    private var id = -1
    private var hour = 7
    private var minute = 0
    private var days = Alarm.WEEKDAYS
    private var enabled = true
    private lateinit var nameField: EditText
    private lateinit var timeText: TextView
    private lateinit var dayBtns: List<Button>
    private lateinit var presetBtns: Map<Int, Button>

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        id = intent.getIntExtra(EXTRA_ID, -1)
        val existing = if (id >= 0) Alarms.get(this, id) else null
        existing?.let { hour = it.hour; minute = it.minute; days = it.days; enabled = it.enabled }
        if (s != null) { hour = s.getInt("h"); minute = s.getInt("m"); days = s.getInt("d") }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); addView(list) })

        list.addView(text(getString(if (existing == null) R.string.edit_new else R.string.edit_title), 26f, bold = true))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        list.addView(card)

        // Kaldenavn
        card.addView(label(R.string.edit_name))
        nameField = EditText(this).apply {
            hint = getString(R.string.edit_name_hint)
            setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 18f
            setTextColor(getColor(R.color.fg)); setHintTextColor(getColor(R.color.muted))
            setBackgroundResource(R.drawable.bg_field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(nameField)

        // Tidspunkt
        card.addView(label(R.string.label_time, top = 16))
        timeText = TextView(this).apply {
            textSize = 40f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setTextColor(getColor(R.color.fg))
            setBackgroundResource(R.drawable.bg_field)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                TimePickerDialog(this@AlarmEditActivity, { _, h, m -> hour = h; minute = m; refresh() }, hour, minute, true).show()
            }
        }
        card.addView(timeText)

        // Ugedage
        card.addView(label(R.string.label_days, top = 16))
        val presetRow = row()
        card.addView(presetRow)
        presetBtns = listOf(
            Alarm.WEEKDAYS to R.string.days_weekdays, Alarm.WEEKEND to R.string.days_weekend, Alarm.ALL_DAYS to R.string.days_all
        ).mapIndexed { i, (mask, res) ->
            mask to toggle(getString(res), presetRow, i) { days = mask; refresh() }
        }.toMap()
        val dayRow = row().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) }
        card.addView(dayRow)
        dayBtns = resources.getStringArray(R.array.day_letters).toList().mapIndexed { i, l ->
            toggle(l, dayRow, i) {
                val now = days xor (1 shl i)
                if (now == 0) toast(R.string.toast_one_day) else { days = now; refresh() }
            }
        }

        // Gem / slet
        list.addView(Button(this).apply {
            text = getString(R.string.edit_save)
            layoutParams = LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(20) }
            setOnClickListener { save() }
        })
        if (existing != null) {
            list.addView(Button(this, null, 0, R.style.OMD_Button_Ghost).apply {
                text = getString(R.string.edit_delete)
                setTextColor(0xFFC8102E.toInt())
                layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) }
                setOnClickListener {
                    Alarms.delete(this@AlarmEditActivity, id)
                    Scheduler.scheduleNext(this@AlarmEditActivity)
                    finish()
                }
            })
        }
        refresh()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("h", hour); out.putInt("m", minute); out.putInt("d", days)
    }

    private fun save() {
        val a = Alarm(
            if (id >= 0) id else Alarms.newId(this),
            nameField.text.toString().trim(), hour, minute, days,
            enabled = if (id >= 0) enabled else true
        )
        Alarms.save(this, a)
        Scheduler.scheduleNext(this)
        finish()
    }

    private fun refresh() {
        timeText.text = String.format("%02d:%02d", hour, minute)
        val fg = getColor(R.color.fg)
        dayBtns.forEachIndexed { i, b -> style(b, days and (1 shl i) != 0, fg) }
        presetBtns.forEach { (mask, b) -> style(b, days == mask, fg) }
    }

    private fun style(b: Button, on: Boolean, fg: Int) {
        b.setBackgroundResource(if (on) R.drawable.bg_button else R.drawable.bg_button_ghost)
        b.setTextColor(if (on) 0xFFFFFFFF.toInt() else fg)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun toggle(label: String, row: LinearLayout, i: Int, onClick: () -> Unit) =
        Button(this, null, 0, R.style.OMD_Button_Ghost).apply {
            text = label; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i > 0) marginStart = dp(6) }
            setOnClickListener { onClick() }
            row.addView(this)
        }

    private fun label(res: Int, top: Int = 0) = TextView(this).apply {
        text = getString(res); textSize = 13f; setTextColor(getColor(R.color.muted))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top); bottomMargin = dp(6) }
    }

    private fun text(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(getColor(R.color.fg))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun toast(res: Int) = Toast.makeText(this, getString(res), Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_ID = "alarmId" }
}
