package com.example.mindshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class TextExtractorService : AccessibilityService() {
    companion object {
        const val ACTION_EXTRACTED_TEXT = "com.example.mindshield.ACTION_EXTRACTED_TEXT"
        const val EXTRA_EXTRACTED_TEXT = "extracted_text"
        val SUPPORTED_APPS = setOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.instagram.android"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!SUPPORTED_APPS.contains(packageName)) return
        val rootNode = rootInActiveWindow ?: return
        val extractedText = extractTextFromNode(rootNode)
        if (extractedText.isNotBlank()) {
            val intent = Intent(ACTION_EXTRACTED_TEXT).apply {
                putExtra(EXTRA_EXTRACTED_TEXT, extractedText)
                setClass(this@TextExtractorService, com.example.mindshield.service.ContentAnalyzerReceiver::class.java)
            }
            sendBroadcast(intent)
            Log.d("TextExtractorService", "Broadcasted extracted text: ${extractedText.take(100)}...")
        }
    }

    override fun onInterrupt() {}

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            sb.append(extractTextFromNode(node.getChild(i)))
        }
        return sb.toString().trim()
    }
} 