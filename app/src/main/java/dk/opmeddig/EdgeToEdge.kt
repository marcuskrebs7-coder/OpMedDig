package dk.opmeddig

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Fra Android 15 tegner apps under status- og navigationsbjælken. Giver skærmens indhold
 * luft til bjælkerne (og tastaturet), og vælger mørke eller lyse ikoner i bjælkerne.
 * Kaldes lige efter setContentView. darkBars = true på skærme med mørk/farvet baggrund.
 */
fun Activity.applySystemBarInsets(darkBars: Boolean = false) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val root = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val b = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(b.left, b.top, b.right, b.bottom)
        WindowInsetsCompat.CONSUMED
    }
    val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    WindowCompat.getInsetsController(window, root).apply {
        isAppearanceLightStatusBars = !night && !darkBars
        isAppearanceLightNavigationBars = !night && !darkBars
    }
}
