package com.personal.tools.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification's Start/Stop and Hide actions. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> ClickerAccessibilityService.instance?.toggle()
            ACTION_HIDE -> ClickerAccessibilityService.instance?.hideControls()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.personal.tools.TOGGLE"
        const val ACTION_HIDE = "com.personal.tools.HIDE"
    }
}
