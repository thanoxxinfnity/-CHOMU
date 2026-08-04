package com.chomu.aiagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONObject

class AgentAutomationService : AccessibilityService() {

    private val TAG = "AgentAutomationSvc"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false

    companion object {
        var instance: AgentAutomationService? = null
        var nodeTree: String = ""
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.chomu.aiagent.AUTOMATION_ACTION" -> {
                    val actionType = intent.getStringExtra("action_type") ?: return
                    val targetId = intent.getStringExtra("target_id")
                    val textInput = intent.getStringExtra("text_input")
                    val scrollDir = intent.getStringExtra("scroll_direction")
                    scope.launch { executeAction(actionType, targetId, textInput, scrollDir) }
                }
                "com.chomu.aiagent.STOP_AUTOMATION" -> {
                    isRunning = false
                    Log.d(TAG, "Automation stopped by user")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter().apply {
            addAction("com.chomu.aiagent.AUTOMATION_ACTION")
            addAction("com.chomu.aiagent.STOP_AUTOMATION")
        }
        registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "AgentAutomationService connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Capture node tree on window changes for screen perception
        event ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val root = rootInActiveWindow ?: return@launch
                nodeTree = serializeNodeTree(root, 0)
                root.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "Node tree capture failed: ${e.message}")
            }
        }
    }

    override fun onInterrupt() { Log.d(TAG, "Accessibility service interrupted") }

    private suspend fun executeAction(
        action: String,
        targetId: String?,
        textInput: String?,
        scrollDirection: String?
    ) = withContext(Dispatchers.Main) {
        if (!isRunning && action != "FINISH_TASK") isRunning = true
        Log.d(TAG, "Executing: $action target=$targetId text=$textInput")

        when (action.uppercase()) {
            "CLICK" -> findAndClick(targetId)
            "LONG_CLICK" -> findAndLongClick(targetId)
            "SET_TEXT" -> findAndSetText(targetId, textInput ?: "")
            "SCROLL" -> performScroll(scrollDirection ?: "down")
            "GLOBAL_BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "GLOBAL_HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "GLOBAL_RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "TAKE_SCREENSHOT" -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            "FINISH_TASK" -> { isRunning = false; Log.d(TAG, "Task finished") }
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun findAndClick(targetId: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root, targetId) ?: run {
            root.recycle()
            return false
        }
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        root.recycle()
        return clicked
    }

    private fun findAndLongClick(targetId: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root, targetId) ?: run {
            root.recycle()
            return false
        }
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        node.recycle()
        root.recycle()
        return clicked
    }

    private fun findAndSetText(targetId: String?, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root, targetId) ?: run {
            root.recycle()
            return false
        }
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        root.recycle()
        return result
    }

    private fun performScroll(direction: String) {
        val root = rootInActiveWindow ?: return
        val action = when (direction.lowercase()) {
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        // Try to find scrollable node
        val scrollable = findScrollableNode(root)
        scrollable?.performAction(action)
        scrollable?.recycle()
        root.recycle()
    }

    private fun findNode(root: AccessibilityNodeInfo, targetId: String?): AccessibilityNodeInfo? {
        if (targetId.isNullOrBlank()) {
            // Return first clickable node as fallback
            return findFirstClickable(root)
        }
        // Try by resource ID
        val byId = root.findAccessibilityNodeInfosByViewId(targetId)
        if (byId.isNotEmpty()) return byId[0]

        // Try by text/content description
        val byText = root.findAccessibilityNodeInfosByText(targetId)
        if (byText.isNotEmpty()) return byText[0]

        return null
    }

    private fun findFirstClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstClickable(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun serializeNodeTree(node: AccessibilityNodeInfo, depth: Int): String {
        if (depth > 8) return ""
        val sb = StringBuilder()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val obj = JSONObject().apply {
            put("id", node.viewIdResourceName ?: "")
            put("text", node.text?.toString() ?: "")
            put("desc", node.contentDescription?.toString() ?: "")
            put("cls", node.className?.toString()?.substringAfterLast('.') ?: "")
            put("clickable", node.isClickable)
            put("scrollable", node.isScrollable)
            put("enabled", node.isEnabled)
            put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        }
        sb.appendLine("  ".repeat(depth) + obj.toString())
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(serializeNodeTree(child, depth + 1))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
