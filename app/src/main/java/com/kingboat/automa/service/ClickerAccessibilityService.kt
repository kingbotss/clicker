package com.kingboat.automa.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.kingboat.automa.data.ClipboardStore
import com.kingboat.automa.R
import com.kingboat.automa.data.ClickRepository

/**
 * The single no-root workhorse:
 *  - performs automated taps via [ClickEngine] (dispatchGesture),
 *  - listens for clipboard changes and saves copied text to history, and
 *  - hosts the floating control bar / point selector via [OverlayController]
 *    (TYPE_ACCESSIBILITY_OVERLAY — no SYSTEM_ALERT_WINDOW needed).
 *
 * A static [instance] lets the UI command it directly. Android guarantees one
 * accessibility service instance while enabled.
 */
class ClickerAccessibilityService : AccessibilityService() {

    private lateinit var engine: ClickEngine
    lateinit var repo: ClickRepository
        private set
    private lateinit var clipStore: ClipboardStore
    private lateinit var overlay: OverlayController
    private var clipboard: ClipboardManager? = null

    var stateListener: ((Boolean) -> Unit)? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { saveCurrentClip() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ClickEngine(this)
        repo = ClickRepository(this)
        clipStore = ClipboardStore(this)
        overlay = OverlayController(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        if (::engine.isInitialized) engine.destroy()
        if (::overlay.isInitialized) overlay.teardown()
        cancelNotification()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() { /* not used */ }

    /**
     * Physical kill switch: while the tap loop is running, a volume key press
     * stops it immediately and is consumed. This always works even if the
     * floating bar was moved off-screen/hidden and the taps are flooding the UI.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isRunning && event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            stopClicking()
            Toast.makeText(this, R.string.stopped_by_volume, Toast.LENGTH_LONG).show()
            return true
        }
        return super.onKeyEvent(event)
    }

    // --- clicking control ---

    val isRunning: Boolean get() = ::engine.isInitialized && engine.isRunning

    fun startClicking(): Boolean {
        val points = repo.getActivePoints()
        if (points.isEmpty()) return false
        engine.start(points) { onRunningChanged(false) }
        onRunningChanged(true)
        return true
    }

    /** Dispatches one tap at ([x],[y]) to verify the service can inject touches. */
    fun testTap(x: Int, y: Int, onResult: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) = onResult(true)
                override fun onCancelled(d: GestureDescription?) = onResult(false)
            },
            null,
        )
        if (!accepted) onResult(false)
    }

    fun stopClicking() {
        if (!isRunning) return
        engine.stop()
    }

    /** Returns the new running state (false when there was nothing to start). */
    fun toggle(): Boolean {
        if (isRunning) {
            stopClicking()
            return false
        }
        return startClicking()
    }

    private fun onRunningChanged(running: Boolean) {
        stateListener?.invoke(running)
        if (::overlay.isInitialized) overlay.onRunningChanged(running)
        updateNotification()
    }

    // --- control surfaces (bar or notification, per chosen mode) ---

    var controlsShown = false
        private set

    fun showControls() {
        controlsShown = true
        if (repo.controlMode == ClickRepository.MODE_OVERLAY) overlay.showBar() else overlay.hideBar()
        overlay.showMarkers()
        updateNotification()
    }

    fun hideControls() {
        controlsShown = false
        overlay.teardown()
        updateNotification()
    }

    /**
     * Keep a notification with a Stop action whenever the tap loop is running
     * (any control mode), so there is always a reachable way to stop even if
     * the floating bar is gone. Otherwise only show it for notification-mode
     * controls, and cancel it when nothing needs it.
     */
    private fun updateNotification() {
        val needed = isRunning ||
            (controlsShown && repo.controlMode == ClickRepository.MODE_NOTIFICATION)
        if (needed) postNotification(isRunning) else cancelNotification()
    }

    fun startPicker() {
        if (::overlay.isInitialized) overlay.startPicker()
    }

    /** Re-render on-screen point markers after the point set changes. */
    fun refreshOverlayPoints() {
        if (::overlay.isInitialized && controlsShown) overlay.refreshMarkers()
    }

    private fun postNotification(running: Boolean) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(ControlNotification.NOTIF_ID, ControlNotification.build(this, running))
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(ControlNotification.NOTIF_ID)
    }

    // --- clipboard capture ---

    // Best-effort only: Android 10+ blocks background clipboard reads, so this
    // may return stale data or throw. The foreground Activity capture in
    // MainActivity is the reliable path.
    private fun saveCurrentClip() {
        runCatching {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            clipStore.add(text)
        }.onFailure { e ->
            Log.w(TAG, "Clipboard read denied: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ClickerA11y"

        @Volatile
        var instance: ClickerAccessibilityService? = null
            private set
    }
}
