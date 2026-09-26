package com.personal.tools.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.personal.tools.R

/**
 * Hosts the floating control bar and the draggable point selector. Both are
 * added as [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] windows from
 * the accessibility service, so they need **no** SYSTEM_ALERT_WINDOW permission
 * and are not blocked by OEM overlay gates (e.g. MIUI). This mirrors how
 * production autoclickers avoid the overlay permission entirely.
 */
class OverlayController(private val service: ClickerAccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater = LayoutInflater.from(service)
    private var barView: View? = null
    private var selectorView: View? = null
    private var toggleButton: Button? = null

    val isBarShown: Boolean get() = barView != null

    // --- floating control bar ---

    fun showBar() {
        if (barView != null) return
        val view = inflater.inflate(R.layout.overlay_bar, null)
        toggleButton = view.findViewById<Button>(R.id.btn_toggle).apply {
            text = service.getString(if (service.isRunning) R.string.overlay_stop else R.string.overlay_go)
            setOnClickListener { service.toggle() }
        }
        view.findViewById<Button>(R.id.btn_pick).setOnClickListener { startPicker() }

        val params = params(fullScreen = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }
        view.findViewById<TextView>(R.id.handle).setOnTouchListener(dragListener(params))
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
    }

    private fun dragListener(params: WindowManager.LayoutParams) = object : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    barView?.let { wm.updateViewLayout(it, params) }
                }
            }
            return false
        }
    }

    // --- draggable point selector ---

    /**
     * Full-screen selector with a draggable crosshair. Touch rawX/rawY are
     * absolute display coordinates — exactly what dispatchGesture expects — so
     * the confirmed point maps 1:1 to where taps will land.
     */
    fun startPicker() {
        if (selectorView != null) return
        Toast.makeText(service, R.string.tap_to_add, Toast.LENGTH_SHORT).show()
        val view = inflater.inflate(R.layout.overlay_selector, null)
        val crosshair = view.findViewById<View>(R.id.crosshair)
        val coord = view.findViewById<TextView>(R.id.coord)

        var selX = -1
        var selY = -1
        fun moveTo(rawX: Int, rawY: Int) {
            selX = rawX; selY = rawY
            crosshair.x = rawX - crosshair.width / 2f
            crosshair.y = rawY - crosshair.height / 2f
            coord.text = service.getString(R.string.coord_fmt, rawX, rawY)
        }

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    moveTo(e.rawX.toInt(), e.rawY.toInt())
            }
            true
        }
        view.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            if (selX < 0) {
                Toast.makeText(service, R.string.tap_to_add, Toast.LENGTH_SHORT).show()
            } else {
                service.repo.addPointToActive(selX, selY)
                Toast.makeText(service, "Added ($selX, $selY)", Toast.LENGTH_SHORT).show()
                stopPicker()
            }
        }
        view.findViewById<Button>(R.id.btn_cancel).setOnClickListener { stopPicker() }

        val params = params(fullScreen = true)
        wm.addView(view, params)
        selectorView = view
        crosshair.post {
            val m = service.resources.displayMetrics
            moveTo(m.widthPixels / 2, m.heightPixels / 2)
        }
    }

    private fun stopPicker() {
        selectorView?.let { runCatching { wm.removeView(it) } }
        selectorView = null
    }

    fun teardown() {
        hideBar()
        stopPicker()
    }

    private fun params(fullScreen: Boolean): WindowManager.LayoutParams {
        val size = if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT
        else WindowManager.LayoutParams.WRAP_CONTENT
        // LAYOUT_IN_SCREEN makes the window span the full display (incl. status
        // bar), so touch rawX/rawY match dispatchGesture's display coordinates.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }
}
