package dk.opmeddig

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skærmen der vises, når alarmen ringer: et antal opgaver (regning, prikker, vendespil, skriv),
 * derefter sænkes lyden til 50 %, og til sidst skal der tages et billede af referencestedet.
 */
class RingActivity : AppCompatActivity() {
    private lateinit var taskArea: FrameLayout
    private lateinit var question: TextView
    private lateinit var error: TextView
    private lateinit var progress: TextView
    private lateinit var okBtn: Button
    private lateinit var dots: LinearLayout
    private var flash: ValueAnimator? = null
    private var solved = 0
    private var photoPhase = false
    private var plan: List<ChallengeType> = emptyList()

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        val ref = PhotoMatch.loadRef(this)
        when {
            bmp == null -> error.text = getString(R.string.no_photo)
            ref == null -> finishAlarm()
            else -> {
                val score = PhotoMatch.score(ref, bmp)
                if (score >= PhotoMatch.THRESHOLD) finishAlarm()
                else error.text = getString(R.string.photo_mismatch, (score * 100).toInt())
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_ring)
        taskArea = findViewById(R.id.taskArea)
        question = findViewById(R.id.question)
        error = findViewById(R.id.error)
        progress = findViewById(R.id.progress)
        okBtn = findViewById(R.id.okBtn)
        dots = findViewById(R.id.dots)
        findViewById<TextView>(R.id.ringClock).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Baggrunden blinker mellem rød og mørk
        val root = findViewById<View>(R.id.root)
        flash = ValueAnimator.ofObject(ArgbEvaluator(), 0xFFFF2D55.toInt(), 0xFF14162A.toInt()).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val c = it.animatedValue as Int
                root.setBackgroundColor(c)
                window.statusBarColor = c
                window.navigationBarColor = c
            }
            start()
        }

        // Tilbage-knappen gør ingenting
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        okBtn.setOnClickListener { takePhoto.launch(null) }

        if (s != null) {
            solved = s.getInt("solved")
            photoPhase = s.getBoolean("photoPhase")
            plan = s.getIntArray("plan")?.map { ChallengeType.values()[it] } ?: emptyList()
        }
        if (plan.isEmpty()) plan = makePlan()
        if (photoPhase) startPhotoPhase() else showTask()
    }

    override fun onDestroy() {
        flash?.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("solved", solved)
        out.putBoolean("photoPhase", photoPhase)
        out.putIntArray("plan", plan.map { it.ordinal }.toIntArray())
    }

    /** Lydknapperne gør ingenting på alarmskærmen. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.onKeyDown(keyCode, event)
        }

    /** Tilfældig rækkefølge af de valgte opgavetyper, helst ikke den samme to gange i træk. */
    private fun makePlan(): List<ChallengeType> {
        val types = ChallengeType.from(Prefs.challenges(this))
        val out = mutableListOf<ChallengeType>()
        repeat(Prefs.count(this)) {
            val options = if (types.size > 1) types.filter { it != out.lastOrNull() } else types
            out += options.random()
        }
        return out
    }

    private fun total() = plan.size

    private fun renderDots() {
        dots.removeAllViews()
        val size = (12 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()
        for (i in 0 until total() + 1) {   // sidste prik = billedet
            val done = i < solved
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = if (i == total()) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
                    cornerRadius = size / 4f
                    setColor(if (done) 0xFF1AA35A.toInt() else 0xFFCCCCDD.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(size, size).apply { if (i > 0) marginStart = gap }
            })
        }
    }

    private fun showTask() {
        error.text = ""
        progress.text = getString(R.string.progress, solved + 1, total())
        renderDots()
        taskArea.removeAllViews()
        taskArea.addView(
            Challenges.build(plan[solved], this,
                onDone = {
                    solved++
                    if (solved >= total()) startPhotoPhase() else showTask()
                },
                onWrong = { msg -> error.text = msg }
            )
        )
    }

    private fun startPhotoPhase() {
        photoPhase = true
        // Opgaverne er klaret: lyden ned på 50 %
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_QUIET))
        renderDots()
        taskArea.removeAllViews()
        error.text = ""
        progress.text = getString(R.string.last_step)
        question.text = getString(R.string.photo_task)
        question.visibility = View.VISIBLE
        okBtn.visibility = View.VISIBLE
    }

    private fun finishAlarm() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        Scheduler.scheduleNext(this)
        Toast.makeText(this, getString(R.string.good_morning), Toast.LENGTH_LONG).show()
        finish()
    }
}
