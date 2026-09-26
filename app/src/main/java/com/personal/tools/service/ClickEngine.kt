package com.personal.tools.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.personal.tools.model.ClickPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(points: List<ClickPoint>, onStopped: () -> Unit) {
        if (isRunning || points.isEmpty()) return
        isRunning = true
        job = scope.launch {
            while (isActive) {
                for (point in points) {
                    if (!isActive) break
                    tap(point)
                    delay(point.delayMs.coerceAtLeast(MIN_DELAY_MS))
                }
            }
        }
        job?.invokeOnCompletion {
            isRunning = false
            onStopped()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        isRunning = false
    }

    private fun tap(point: ClickPoint) {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val TAP_DURATION_MS = 40L
        private const val MIN_DELAY_MS = 20L
    }
}
