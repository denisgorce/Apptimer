package com.example.apptimer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private data class AppItem(val label: String, val pkg: String)
    private var apps = listOf<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(main, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun lp() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                apps.map { it.label }
            )
        }
        val minutes = EditText(this).apply {
            hint = "Durée en minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("30")
        }
        val start = Button(this).apply { text = "Lancer l'app et démarrer la minuterie" }
        val stop = Button(this).apply { text = "Annuler la minuterie" }
        val access = Button(this).apply { text = "Activer le service d'accessibilité" }
        val info = TextView(this).apply {
            text = "À la fin du délai, l'app est fermée via « Forcer l'arrêt » " +
                "(service d'accessibilité requis)."
        }

        start.setOnClickListener {
            val m = minutes.text.toString().toIntOrNull()
            if (m == null || m < 1 || apps.isEmpty()) {
                Toast.makeText(this, "Durée invalide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val app = apps[spinner.selectedItemPosition]
            startForegroundService(
                Intent(this, TimerService::class.java)
                    .putExtra(TimerService.EXTRA_PKG, app.pkg)
                    .putExtra(TimerService.EXTRA_MIN, m)
            )
            pm.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
        }
        stop.setOnClickListener {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_STOP))
        }
        access.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        listOf(spinner, minutes, start, stop, access, info).forEach { root.addView(it, lp()) }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
