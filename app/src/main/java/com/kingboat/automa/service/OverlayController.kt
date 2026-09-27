package com.kingboat.automa.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.kingboat.automa.R
import kotlin.math.abs

/**
 * Hosts the floating control bar, the pass-through point picker and the on-screen
 * point markers. Every window is a [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY],
 * so they need **no** SYSTEM_ALERT_WINDOW permission and aren't blocked by OEM
 * overlay gates (e.g. MIUI).
 *
 * The picker deliberately does NOT cover the whole screen: only the small
 * crosshair and the top toolbar catch touches, so the user can still reach
 * recents / launch the target app while positioning the point.
 */
class OverlayController(private val service: ClickerAccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater = LayoutInflater.from(service)
    private val density = service.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var barView: View? = null
    private var toggleButton: Button? = null

    private var crosshairView: View? = null
    private var pickbarView: View? = null
    private var crosshairParams: WindowManager.LayoutParams? = null

    private val markerViews = mutableListOf<View>()

    val isBarShown: Boolean get() = barView != null

    private fun dp(v: Int) = (v * density).toInt()

    // --- floating control bar ---

    fun showBar() {
        if (barView != null) return
        val view = inflater.inflate(R.layout.overlay_bar, null)
        toggleButton = view.findViewById<Button>(R.id.btn_toggle).apply {
            text = service.getString(if (service.isRunning) R.string.overlay_stop else R.string.overlay_go)
            setOnClickListener {
                if (!service.isRunning && service.repo.getActivePoints().isEmpty()) {
                    Toast.makeText(service, "Add at least one point", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                service.toggle()
            }
        }
        view.findViewById<Button>(R.id.btn_pick).setOnClickListener { startPicker() }

        val params = params().apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(120)
        }
        // The handle drags the whole bar window.
        makeDraggable(view, view.findViewById(R.id.handle), params)
        wm.addView(view, params)
        barView = view
    }

    fun hideBar() {
        toggleButton = null
        barView?.let { runCatching { wm.removeView(it) } }
        barView = null
    }

    fun onRunningChanged(running: Boolean) {
        toggleButton?.post {
            toggleButton?.text =
                service.getString(if (running) R.string.overlay_stop else R.string.overlay_go)
        }
        // Re-render markers so they become draggable when stopped / inert when running.
        refreshMarkers()
    }

    /**
     * Drags [windowView]'s window when [handle] is touched. Returns true so the
     * framework keeps delivering MOVE/UP to us (returning false on DOWN would
     * silently cancel the drag). [onMove] fires after each reposition.
     */
    private fun makeDraggable(
        windowView: View,
        handle: View,
        params: WindowManager.LayoutParams,
        onMove: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
        onLongPress: (() -> Unit)? = null,
    ) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var touchX = 0f; private var touchY = 0f
            private var longFired = false
            private val longRunnable = Runnable { longFired = true; onLongPress?.invoke() }

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = e.rawX; touchY = e.rawY
                        longFired = false
                        if (onLongPress != null) mainHandler.postDelayed(longRunnable, LONG_PRESS_MS)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // A real drag cancels the pending long-press.
                        if (abs(e.rawX - touchX) > touchSlop || abs(e.rawY - touchY) > touchSlop) {
                            mainHandler.removeCallbacks(longRunnable)
                        }
                        if (longFired) return true
                        // Clamp within the display so a window (esp. the control
                        // bar) can never be dragged fully off-screen and lost.
                        val m = service.resources.displayMetrics
                        val maxX = (m.widthPixels - windowView.width).coerceAtLeast(0)
                        val maxY = (m.heightPixels - windowView.height).coerceAtLeast(0)
                        params.x = (startX + (e.rawX - touchX).toInt()).coerceIn(0, maxX)
                        params.y = (startY + (e.rawY - touchY).toInt()).coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(windowView, params) }
                        onMove?.invoke()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longRunnable)
                        if (!longFired) onEnd?.invoke()
                        return true
                    }
                }
                return false
            }
        })
    }

    // --- pass-through point picker ---

    /**
     * Shows a draggable crosshair + a small top toolbar. The rest of the screen
     * stays touchable, so the user can open the target app, then position the
     * crosshair and confirm. The picked point is the crosshair window's centre
     * in display coordinates (LAYOUT_IN_SCREEN), which matches dispatchGesture.
     */
    fun startPicker() {
        if (crosshairView != null) return
        showMarkers() // let the user see existing points while adding
        Toast.makeText(service, R.string.tap_to_add, Toast.LENGTH_SHORT).show()

        val cross = inflater.inflate(R.layout.overlay_crosshair, null)
        val m = service.resources.displayMetrics
        val size = dp(72)
        val cp = params().apply {
            gravity = Gravity.TOP or Gravity.START
            x = m.widthPixels / 2 - size / 2
            y = m.heightPixels / 2 - size / 2
        }

        val bar = inflater.inflate(R.layout.overlay_pickbar, null)
        val coord = bar.findViewById<TextView>(R.id.coord)

        fun centre(): Pair<Int, Int> {
            val w = crossSize(cross.width, size)
            val h = crossSize(cross.height, size)
            return (cp.x + w / 2) to (cp.y + h / 2)
        }
        fun updateCoord() {
            val (cx, cy) = centre()
            coord.text = service.getString(R.string.coord_fmt, cx, cy)
        }

        makeDraggable(cross, cross, cp) { updateCoord() }

        bar.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            val (cx, cy) = centre()
            service.repo.addPointToActive(cx, cy)
            Toast.makeText(service, "Added ($cx, $cy)", Toast.LENGTH_SHORT).show()
            stopPicker()
            showMarkers()
        }
        bar.findViewById<Button>(R.id.btn_cancel).setOnClickListener { stopPicker() }

        val bp = params().apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(24)
        }

        wm.addView(cross, cp)
        wm.addView(bar, bp)
        crosshairView = cross
        crosshairParams = cp
        pickbarView = bar
        cross.post { updateCoord() }
    }

    private fun crossSize(measured: Int, fallback: Int) = if (measured > 0) measured else fallback

    private fun stopPicker() {
        pickbarView?.let { runCatching { wm.removeView(it) } }
        crosshairView?.let { runCatching { wm.removeView(it) } }
        pickbarView = null
        crosshairView = null
        crosshairParams = null
    }

    // --- on-screen point markers (numbered colored dots) ---

    /**
     * Draws a numbered colored dot at each active-profile point. While the
     * clicker is stopped the dots are draggable so the user can fine-tune a
     * point on-screen; the new position is persisted on release. While running
     * they are non-touchable so they never intercept the taps being dispatched.
     */
    fun showMarkers() {
        clearMarkers()
        val dot = dp(30)
        val editable = !service.isRunning
        service.repo.getActivePoints().forEachIndexed { i, p ->
            val v = inflater.inflate(R.layout.overlay_marker, null)
            v.findViewById<TextView>(R.id.marker_label).text = (i + 1).toString()
            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (!editable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // Fixed dot x dot window so the visual centre always matches the
            // stored point (WRAP_CONTENT would drift with measured size).
            val mp = WindowManager.LayoutParams(
                dot,
                dot,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = p.x - dot / 2
                y = p.y - dot / 2
            }
            if (editable) {
                makeDraggable(
                    v, v, mp,
                    onEnd = {
                        service.repo.updatePoint(p.id, p.label, mp.x + dot / 2, mp.y + dot / 2, p.delayMs)
                    },
                    onLongPress = {
                        service.repo.deletePoint(p.id)
                        Toast.makeText(service, "Deleted ${p.label}", Toast.LENGTH_SHORT).show()
                        showMarkers()
                    },
                )
            }
            runCatching { wm.addView(v, mp) }.onSuccess { markerViews += v }
        }
    }

    fun clearMarkers() {
        markerViews.forEach { runCatching { wm.removeView(it) } }
        markerViews.clear()
    }

    /** Re-render markers if any overlay UI is currently visible. */
    fun refreshMarkers() {
        if (barView != null || crosshairView != null || markerViews.isNotEmpty()) showMarkers()
    }

    fun teardown() {
        hideBar()
        stopPicker()
        clearMarkers()
    }

    /** Base params for a touchable, non-focusable overlay window. LAYOUT_IN_SCREEN
     *  makes touch rawX/rawY match dispatchGesture's display coordinates. */
    private fun params(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }

    companion object {
        private const val LONG_PRESS_MS = 600L
    }
}
