package com.kingboat.automa.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kingboat.automa.model.ClickPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the tap loop. On each iteration it dispatches a short tap gesture for
 * every [ClickPoint] in order, waiting each point's delay, then repeats until
 * stopped. Taps are performed with [AccessibilityService.dispatchGesture] —
 * the only no-root way to inject touches on Android 9+.
 */
class ClickEngine(private val service: AccessibilityService) {

    // dispatchGesture must be called on the main thread; the delay() itself
    // is suspending so running the loop on Main never blocks.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var onStopped: (() -> Unit)? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(points: List<ClickPoint>, onStopped: () -> Unit) {
        if (isRunning || points.isEmpty()) return
        isRunning = true
        this.onStopped = onStopped
        job = scope.launch {
            while (isActive) {
                for (point in points) {
                    if (!isActive) break
                    tap(point)
                    delay(point.delayMs.coerceAtLeast(MIN_DELAY_MS))
                }
            }
        }
    }

    fun stop() {
        if (!isRunning && job == null) return
        job?.cancel()
        job = null
        isRunning = false
        onStopped?.invoke()
        onStopped = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun tap(point: ClickPoint) {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchTap(gesture, point)
        } else {
            gestureHandler.post { dispatchTap(gesture, point) }
        }
    }

    private fun dispatchTap(gesture: GestureDescription, point: ClickPoint) {
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCancelled(g: GestureDescription?) {
                    Log.w(TAG, "Tap cancelled at (${point.x}, ${point.y})")
                }
            },
            null,
        )
        if (!accepted) Log.w(TAG, "Tap rejected at (${point.x}, ${point.y})")
    }

    companion object {
        private const val TAG = "ClickEngine"
        private const val TAP_DURATION_MS = 40L
        private const val MIN_DELAY_MS = 20L
    }
}
