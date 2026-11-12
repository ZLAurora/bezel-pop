package com.bezelpop

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            @Suppress("UNUSED_VARIABLE") val tk = ui.tick
            BezelTheme(ui.cfg.ui) {
                AppBackground {
                    BackHandler(enabled = ui.showChecklist || ui.editing != null) {
                        if (ui.showChecklist) ui.showChecklist = false else ui.editing = null
                    }
                    val editing = ui.editing
                    Box(Modifier.fillMaxSize()) {
                        when {
                            ui.showChecklist -> ChecklistScreen(ui)
                            editing != null -> EditorScreen(ui, editing)
                            else -> HomeScreen(ui)
                        }
                        if (!ui.showChecklist) {
                            BottomBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) {
                                if (editing == null) BarBtn(Ic.PLUS) { ui.addDialog = true }
                                BarBtn(Ic.UNDO, ui.canUndo) { ui.undo() }
                                BarBtn(Ic.REDO, ui.canRedo) { ui.redo() }
                                if (editing != null) BarBtn(Ic.PLAY) { ui.replayTick++ }
                            }
                        }
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
        KeyService.keyListener = { id, down ->
            runOnUiThread {
                if (down) {
                    ui.litId = id
                    window.decorView.postDelayed({ if (ui.litId == id) ui.litId = null }, 2500)
                } else if (ui.litId == id) ui.litId = null
            }
        }
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        KeyService.keyListener = null
    }

    override fun onStop() {
        super.onStop()
        KeyService.instance?.unpin()
    }
}
