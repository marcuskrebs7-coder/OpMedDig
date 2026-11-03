package dk.opmeddig

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Skærmen der vises, når alarmen ringer: først regnestykker, så et billede af referencestedet. */
class RingActivity : AppCompatActivity() {
    private lateinit var question: TextView
    private lateinit var answer: EditText
    private lateinit var error: TextView
    private lateinit var progress: TextView
    private lateinit var okBtn: Button
    private lateinit var dots: LinearLayout
    private var flash: ValueAnimator? = null
    private var solved = 0
    private var expected = 0
    private var photoPhase = false

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
        question = findViewById(R.id.question)
        answer = findViewById(R.id.answer)
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

        okBtn.setOnClickListener { if (photoPhase) takePhoto.launch(null) else check() }
        answer.setOnEditorActionListener { _, _, _ -> check(); true }

        if (s != null) {
            solved = s.getInt("solved")
            photoPhase = s.getBoolean("photoPhase")
        }
        if (photoPhase) startPhotoPhase() else newProblem()
    }

    override fun onDestroy() {
        flash?.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("solved", solved)
        out.putBoolean("photoPhase", photoPhase)
    }

    /** Lydknapperne gør ingenting på alarmskærmen. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean =
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
            android.view.KeyEvent.KEYCODE_VOLUME_UP,
            android.view.KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.onKeyDown(keyCode, event)
        }

    private fun total() = Prefs.count(this)

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

    private fun newProblem() {
        val a = Random.nextInt(3, 13)
        val b = Random.nextInt(6, 14)
        val c = Random.nextInt(11, 61)
        expected = a * b + c
        question.text = "$a × $b + $c"
        answer.setText("")
        progress.text = getString(R.string.progress, solved + 1, total())
        renderDots()
    }

    private fun check() {
        if (answer.text.toString().trim().toIntOrNull() == expected) {
            solved++
            error.text = ""
            if (solved >= total()) startPhotoPhase() else newProblem()
        } else {
            error.text = getString(R.string.wrong)
            newProblem()
        }
    }

    private fun startPhotoPhase() {
        photoPhase = true
        renderDots()
        answer.visibility = View.GONE
        progress.text = getString(R.string.last_step)
        question.textSize = 22f
        question.text = getString(R.string.photo_task)
        okBtn.text = getString(R.string.btn_photo)
    }

    private fun finishAlarm() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        Scheduler.scheduleNext(this)
        Toast.makeText(this, getString(R.string.good_morning), Toast.LENGTH_LONG).show()
        finish()
    }
}
