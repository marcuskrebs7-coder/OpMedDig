package dk.opmeddig

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Groft billedsammenligning uden internet: en lille gråtoneversion (motivets opbygning)
 * plus et farvehistogram. Ikke perfekt, men et billede af dynen eller loftet
 * ligner ikke køkkenbordet. Sænk THRESHOLD, hvis den er for streng.
 */
object PhotoMatch {
    const val THRESHOLD = 0.62

    fun refFile(c: Context) = File(c.filesDir, "reference.png")

    fun loadRef(c: Context): Bitmap? =
        refFile(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun saveRef(c: Context, b: Bitmap) =
        refFile(c).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** 0..1, hvor 1 er ens. */
    fun score(a: Bitmap, b: Bitmap): Double = 0.6 * structure(a, b) + 0.4 * histogram(a, b)

    private fun gray(b: Bitmap, n: Int): DoubleArray {
        val small = Bitmap.createScaledBitmap(b, n, n, true)
        val v = DoubleArray(n * n) { i ->
            val p = small.getPixel(i % n, i / n)
            0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
        }
        val mean = v.average()
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size).coerceAtLeast(1.0)
        return DoubleArray(v.size) { (v[it] - mean) / sd }
    }

    private fun structure(a: Bitmap, b: Bitmap): Double {
        val x = gray(a, 24)
        val y = gray(b, 24)
        val corr = x.indices.sumOf { x[it] * y[it] } / x.size
        return ((corr + 1) / 2).coerceIn(0.0, 1.0)
    }

    private fun hist(b: Bitmap): DoubleArray {
        val small = Bitmap.createScaledBitmap(b, 48, 48, true)
        val h = DoubleArray(64)
        for (yy in 0 until 48) for (xx in 0 until 48) {
            val p = small.getPixel(xx, yy)
            h[(Color.red(p) / 64) * 16 + (Color.green(p) / 64) * 4 + Color.blue(p) / 64] += 1.0
        }
        return DoubleArray(64) { h[it] / (48 * 48) }
    }

    private fun histogram(a: Bitmap, b: Bitmap): Double {
        val x = hist(a)
        val y = hist(b)
        return 1 - x.indices.sumOf { abs(x[it] - y[it]) } / 2
    }
}
