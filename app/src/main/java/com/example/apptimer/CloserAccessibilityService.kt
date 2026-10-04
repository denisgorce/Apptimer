package com.example.apptimer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CloserAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: CloserAccessibilityService? = null
        private val STOP_LABELS = listOf("forcer l'arrêt", "forcer l’arrêt", "force stop", "forcer arrêt")
        private val CONFIRM_LABELS = STOP_LABELS + listOf("ok", "oui", "yes")
        private val CANCEL_LABELS = listOf("annuler", "cancel", "non", "no")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var armed = false
    private var stage = 0 // 0 = page infos de l'app, 1 = boîte de confirmation

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    /** Ouvre la page « Infos de l'appli » puis clique sur « Forcer l'arrêt » + confirmation. */
    fun forceStop(pkg: String) {
        armed = true
        stage = 0
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        handler.postDelayed({ finish() }, 12_000) // sécurité
    }

    private fun finish() {
        if (!armed) return
        armed = false
        stage = 0
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!armed) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString()?.contains("settings", true) != true) return

        if (stage == 0) {
            val btn = findExact(root, STOP_LABELS).firstOrNull() ?: return
            if (!btn.isEnabled) { finish(); return } // app déjà arrêtée
            if (clickUp(btn)) stage = 1
        } else {
            // La boîte de confirmation contient un bouton « Annuler »
            if (findExact(root, CANCEL_LABELS).isEmpty()) return
            val ok = findExact(root, CONFIRM_LABELS).firstOrNull() ?: return
            if (clickUp(ok)) handler.postDelayed({ finish() }, 800)
        }
    }

    private fun findExact(root: AccessibilityNodeInfo, labels: List<String>): List<AccessibilityNodeInfo> =
        labels.flatMap { root.findAccessibilityNodeInfosByText(it) }
            .filter { it.text?.toString()?.trim()?.lowercase() in labels }

    private fun clickUp(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.isEnabled) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }
}
