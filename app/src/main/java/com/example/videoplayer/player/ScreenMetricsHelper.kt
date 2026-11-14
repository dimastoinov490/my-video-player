package com.example.videoplayer.player

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ScreenMetricsHelper {
    fun getScreenParams(context: Context): Map<String, Any> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val resMetrics = context.resources.displayMetrics
        val metrics = DisplayMetrics().apply { setTo(resMetrics) }

        var widthPx = metrics.widthPixels
        var heightPx = metrics.heightPixels

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            try {
                val bounds = wm.maximumWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    widthPx = bounds.width()
                    heightPx = bounds.height()
                }
            } catch (_: Throwable) {
            }
        } else {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            @Suppress("DEPRECATION")
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: wm?.defaultDisplay
            if (display != null) {
                try {
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(metrics)
                    widthPx = metrics.widthPixels
                    heightPx = metrics.heightPixels
                } catch (_: Throwable) {
                }
            }
        }

        val configuration = context.resources.configuration
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Вычисляем примерную диагональ в дюймах по формуле пиксели / DPI
        val xdpi = if (metrics.xdpi > 1f) metrics.xdpi else metrics.densityDpi.coerceAtLeast(160).toFloat()
        val ydpi = if (metrics.ydpi > 1f) metrics.ydpi else metrics.densityDpi.coerceAtLeast(160).toFloat()
        val widthInches = widthPx / xdpi
        val heightInches = heightPx / ydpi
        val diagonalInches = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())
        val diagonalRounded = if (diagonalInches.isFinite()) {
            String.format(Locale.US, "%.1f", diagonalInches).toDoubleOrNull()
                ?: ((diagonalInches * 10.0).roundToInt() / 10.0)
        } else {
            0.0
        }

        return mapOf(
            "widthPx" to widthPx,
            "heightPx" to heightPx,
            "densityDpi" to metrics.densityDpi,
            "density" to metrics.density,
            "diagonalInches" to diagonalRounded,
            "isLandscape" to isLandscape
        )
    }
}
