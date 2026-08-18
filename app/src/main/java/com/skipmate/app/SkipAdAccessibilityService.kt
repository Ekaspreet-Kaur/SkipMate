package com.skipmate.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SkipAdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SkipMate"
        private const val COOLDOWN_MS = 4000L
        private val AD_SKIP_IDS = listOf(
            "com.google.android.youtube:id/skip_ad_button",
            "com.google.android.youtube:id/ad_skip_button",
            "com.google.android.youtube:id/skip_button",
        )
        private val AD_INDICATOR_IDS = listOf(
            "com.google.android.youtube:id/ad_progress_text",
            "com.google.android.youtube:id/ad_countdown",
            "com.google.android.youtube:id/visit_advertiser_button",
            "com.google.android.youtube:id/ad_badge",
        )
    }

    private var lastSkipTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "SkipMate Accessibility Service Connected")
        val intent = Intent(this, SkipMonitorService::class.java)
        startForegroundService(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.google.android.youtube") return

        val now = System.currentTimeMillis()
        if (now - lastSkipTime < COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        try {
            if (isAdPlaying(root)) {
                val skipped = trySkipWithViewId(root)
                if (!skipped) {
                    trySkipWithTextFallback(root)
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun isAdPlaying(root: AccessibilityNodeInfo): Boolean {
        return AD_INDICATOR_IDS.any { id ->
            root.findAccessibilityNodeInfosByViewId(id)
                ?.any { node -> node.isVisibleToUser.also { node.recycle() } } == true
        }
    }

    private fun trySkipWithViewId(root: AccessibilityNodeInfo): Boolean {
        for (id in AD_SKIP_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    if (clicked) {
                        lastSkipTime = System.currentTimeMillis()
                        Log.d(TAG, "Ad skipped via ViewID: $id")
                        return true
                    }
                }
                node.recycle()
            }
        }
        return false
    }

    private fun trySkipWithTextFallback(root: AccessibilityNodeInfo): Boolean {
        val result = traverseAndSkip(root)
        if (result) {
            lastSkipTime = System.currentTimeMillis()
            Log.d(TAG, "Ad skipped via text fallback")
        }
        return result
    }

    private fun traverseAndSkip(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val isSkipNode = (
            viewId.contains("skip_ad", true) ||
            viewId.contains("ad_skip", true) ||
            (text.equals("Skip", true) && node.isClickable) ||
            (desc.contains("Skip Ad", true) && node.isClickable)
        )
        if (isSkipNode && node.isVisibleToUser && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (traverseAndSkip(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "SkipMate interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SkipMate destroyed")
    }
}