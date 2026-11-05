package dk.opmeddig

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Opgavetyperne på alarmskærmen. Hver opgave bygger sin egen visning og kalder
 * onDone(), når den er løst. onWrong(besked) bruges til at vise en fejl.
 */
enum class ChallengeType(val bit: Int) {
    MATH(1), DOTS(2), MEMORY(4), TYPING(8);

    companion object {
        const val ALL = 0b1111
        fun from(mask: Int) = values().filter { mask and it.bit != 0 }.ifEmpty { listOf(MATH) }
    }
}

private const val INK = 0xFF161A2E.toInt()
private const val MUTED = 0xFF5D6380.toInt()
private const val ACCENT = 0xFFFF5A1F.toInt()
private const val OK = 0xFF1AA35A.toInt()
private const val FIELD = 0xFFF1F2F8.toInt()

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Context.title(t: String) = TextView(this).apply {
    text = t; textSize = 16f; setTextColor(MUTED); gravity = Gravity.CENTER
}

private fun Context.input(hint: String, numeric: Boolean) = EditText(this).apply {
    this.hint = hint
    inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions = EditorInfo.IME_ACTION_DONE
    gravity = Gravity.CENTER; textSize = if (numeric) 26f else 18f
    setTextColor(INK); setHintTextColor(0xFF9096B5.toInt())
    background = GradientDrawable().apply { setColor(FIELD); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0xFFCCCCDD.toInt()) }
    layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) }
}

private fun Context.submit(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) }
    setOnClickListener { onClick() }
}

object Challenges {
    fun build(
        type: ChallengeType, c: Context,
        onDone: () -> Unit, onWrong: (String) -> Unit
    ): View = when (type) {
        ChallengeType.MATH -> math(c, onDone, onWrong)
        ChallengeType.DOTS -> dots(c, onDone, onWrong)
        ChallengeType.MEMORY -> memory(c, onDone)
        ChallengeType.TYPING -> typing(c, onDone, onWrong)
    }

    // ---------- Regnestykke ----------
    private fun math(c: Context, onDone: () -> Unit, onWrong: (String) -> Unit): View {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val q = TextView(c).apply {
            textSize = 34f; setTextColor(INK); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        }
        val a = c.input(c.getString(R.string.answer_hint), numeric = true)
        var expected = 0
        fun next() {
            val x = Random.nextInt(3, 13); val y = Random.nextInt(6, 14); val z = Random.nextInt(11, 61)
            expected = x * y + z; q.text = "$x × $y + $z"; a.setText("")
        }
        fun check() {
            if (a.text.toString().trim().toIntOrNull() == expected) onDone()
            else { onWrong(c.getString(R.string.wrong)); next() }
        }
        a.setOnEditorActionListener { _, _, _ -> check(); true }
        box.addView(c.title(c.getString(R.string.ch_math)))
        box.addView(q); box.addView(a); box.addView(c.submit(c.getString(R.string.btn_answer)) { check() })
        next()
        return box
    }

    // ---------- Forbind prikkerne ----------
    private fun dots(c: Context, onDone: () -> Unit, onWrong: (String) -> Unit): View {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        box.addView(c.title(c.getString(R.string.ch_dots)))
        box.addView(DotsView(c, count = 8, onDone = onDone, onWrong = { onWrong(c.getString(R.string.ch_dots_wrong)) }).apply {
            layoutParams = LinearLayout.LayoutParams(-1, c.dp(300)).apply { topMargin = c.dp(10) }
        })
        return box
    }

    // ---------- Vendespil ----------
    private fun memory(c: Context, onDone: () -> Unit): View {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        box.addView(c.title(c.getString(R.string.ch_memory)))
        val symbols = listOf("☕", "🌞", "🛏️", "⏰", "🍳", "🚿").let { (it + it).shuffled() }
        val grid = GridLayout(c).apply {
            columnCount = 4; rowCount = 3
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = c.dp(10) }
        }
        val cards = mutableListOf<TextView>()
        val matched = BooleanArray(symbols.size)
        var first = -1
        var busy = false
        val handler = Handler(Looper.getMainLooper())
        fun face(i: Int, up: Boolean) {
            val t = cards[i]
            t.text = if (up) symbols[i] else ""
            t.background = GradientDrawable().apply {
                cornerRadius = c.dp(12).toFloat()
                setColor(if (matched[i]) 0xFFDDF5E6.toInt() else if (up) Color.WHITE else ACCENT)
                setStroke(c.dp(1), 0xFFCCCCDD.toInt())
            }
        }
        symbols.indices.forEach { i ->
            val size = c.dp(66)
            val t = TextView(c).apply {
                textSize = 30f; gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply { width = size; height = size; setMargins(c.dp(4), c.dp(4), c.dp(4), c.dp(4)) }
                setOnClickListener {
                    if (busy || matched[i] || i == first) return@setOnClickListener
                    face(i, true)
                    if (first < 0) { first = i; return@setOnClickListener }
                    val f = first; first = -1
                    if (symbols[f] == symbols[i]) {
                        matched[f] = true; matched[i] = true; face(f, true); face(i, true)
                        if (matched.all { it }) handler.postDelayed(onDone, 400)
                    } else {
                        busy = true
                        handler.postDelayed({ face(f, false); face(i, false); busy = false }, 700)
                    }
                }
            }
            cards += t; grid.addView(t); face(i, false)
        }
        box.addView(grid)
        return box
    }

    // ---------- Skriv sætningen ----------
    private fun typing(c: Context, onDone: () -> Unit, onWrong: (String) -> Unit): View {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val phrase = c.resources.getStringArray(R.array.ch_phrases).random()
        box.addView(c.title(c.getString(R.string.ch_typing)))
        box.addView(TextView(c).apply {
            text = phrase; textSize = 22f; setTextColor(INK); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(10) }
        })
        val a = c.input(c.getString(R.string.ch_typing_hint), numeric = false)
        fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
        fun check() {
            if (norm(a.text.toString()) == norm(phrase)) onDone()
            else onWrong(c.getString(R.string.ch_typing_wrong))
        }
        a.setOnEditorActionListener { _, _, _ -> check(); true }
        box.addView(a); box.addView(c.submit(c.getString(R.string.btn_answer)) { check() })
        return box
    }
}

/** Tryk på de nummererede prikker i rækkefølge 1, 2, 3 … Forkert tryk starter forfra. */
@SuppressLint("ViewConstructor")
class DotsView(c: Context, private val count: Int, private val onDone: () -> Unit, private val onWrong: () -> Unit) : View(c) {
    private val pts = mutableListOf<Pair<Float, Float>>()
    private var next = 0
    private val r = c.resources.displayMetrics.density * 22
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OK; strokeWidth = r / 3; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = r; isFakeBoldText = true }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        pts.clear()
        var tries = 0
        while (pts.size < count && tries < 5000) {
            tries++
            val x = r + Random.nextFloat() * (w - 2 * r)
            val y = r + Random.nextFloat() * (h - 2 * r)
            if (pts.all { hypot(it.first - x, it.second - y) > r * 2.6f }) pts += x to y
        }
        next = 0
    }

    override fun onDraw(cv: Canvas) {
        for (i in 1 until next) cv.drawLine(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second, line)
        pts.forEachIndexed { i, (x, y) ->
            fill.color = if (i < next) OK else INK
            cv.drawCircle(x, y, r, fill)
            cv.drawText("${i + 1}", x, y + r / 3, label)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN || next >= pts.size) return true
        val hit = pts.indexOfFirst { hypot(it.first - e.x, it.second - e.y) < r * 1.3f }
        if (hit < 0) return true
        if (hit == next) {
            next++
            invalidate()
            if (next == pts.size) postDelayed(onDone, 300)
        } else if (hit >= next) {
            next = 0; invalidate(); onWrong()
        }
        return true
    }
}
