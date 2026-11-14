package app.still.protection

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import app.still.core.Bounds

class ButtonMasks(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private val views = mutableListOf<View>()
    private var current = emptyList<Bounds>()

    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    fun sync(bounds: List<Bounds>): Boolean {
        if (current == bounds) return true
        clear()
        return try {
            bounds.forEach { rect ->
                val dark = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val view = View(service).apply {
                    setBackgroundColor(if (dark) Color.rgb(24, 24, 24) else Color.rgb(245, 245, 245))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setOnTouchListener { _, _ -> true }
                }
                val params = WindowManager.LayoutParams(
                    rect.width, rect.height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE,
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
                }
                manager.addView(view, params)
                views += view
            }
            current = bounds
            true
        } catch (_: RuntimeException) { clear(); false }
    }

    fun clear() {
        views.forEach { runCatching { manager.removeViewImmediate(it) } }
        views.clear()
        current = emptyList()
    }
}
