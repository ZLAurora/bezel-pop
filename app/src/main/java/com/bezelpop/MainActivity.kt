package com.bezelpop

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private lateinit var ui: UiState

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            ui.profile = currentProfileId(this@MainActivity)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "learn_id") runOnUiThread { ui.reloadFromStore() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = UiState(applicationContext)
        val p = Store.prefs(this)
        if (!p.getBoolean("checklist_seen2", false)) {
            p.edit().putBoolean("checklist_seen2", true).apply()
            ui.showChecklist = true
        }
        setContent {
            BezelTheme {
                GlowBackground {
                    BackHandler(enabled = ui.showChecklist || ui.editing != null) {
                        if (ui.showChecklist) ui.showChecklist = false else ui.editing = null
                    }
                    val editing = ui.editing
                    when {
                        ui.showChecklist -> ChecklistScreen(ui)
                        editing != null -> EditorScreen(ui, editing)
                        else -> HomeScreen(ui)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ui.profile = currentProfileId(this)
        ui.resumeTick++
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, null)
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStop() {
        super.onStop()
        KeyService.instance?.unpin()
    }
}
