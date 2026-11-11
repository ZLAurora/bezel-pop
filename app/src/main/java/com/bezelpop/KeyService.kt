package com.bezelpop

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class KeyService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: KeyService? = null
    }

    private lateinit var cfg: Config
    private val overlays = HashMap<String, BulgeOverlay>()
    private val handler = Handler(Looper.getMainLooper())
    private var swallowUp = -1
    private var receiverOn = false
    private var watchOn = false
    private var menuShown = false

    private var pinnedId: String? = null
    private var pinnedProfile = ProfileId.P0
    private var pinnedColor: Int? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Store.CONFIG_KEY) reload()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            cfg.triggers.filter { it.power && it.enabled }.forEach { overlay(it.id).pulse(cfg) }
        }
    }

    private fun overlay(id: String) = overlays.getOrPut(id) { BulgeOverlay(this, id) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        cfg = Store.load(this)
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        syncInfo()
        syncReceiver()
        syncPrewarm()
    }

    private val settle = Runnable {
        syncPrewarm()
        pinnedId?.let { overlay(it).pin(cfg, pinnedProfile, pinnedColor) }
    }

    private fun reload() {
        cfg = Store.load(this)
        syncInfo()
        syncReceiver()
        // debounce window rebuilds so dragging a slider doesn't thrash the overlay windows
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, 35)
    }

    private fun syncPrewarm() {
        cfg.triggers.forEach { t ->
            val ov = overlay(t.id)
            if (cfg.anim.lowLatency && t.enabled) ov.prewarm(cfg) else ov.setPersistent(false)
        }
    }

    /** Only subscribe to window events when the experimental power-menu option is on. */
    private fun syncInfo() {
        val want = cfg.menuWatch
        if (want == watchOn) return
        val info = serviceInfo ?: return
        info.eventTypes = if (want) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        serviceInfo = info
        watchOn = want
    }

    private fun syncReceiver() {
        if (cfg.pulseOnScreen && !receiverOn) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, f)
            receiverOn = true
        } else if (!cfg.pulseOnScreen && receiverOn) {
            try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
            receiverOn = false
        }
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val prefs = Store.prefs(this)
        val learn = prefs.getString("learn_id", null)
        if (learn != null) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val c = Store.load(this)
                c.trigger(learn)?.keyCode = e.keyCode
                Store.save(this, c)
                prefs.edit().remove("learn_id").apply()
                swallowUp = e.keyCode
                Toast.makeText(
                    this, "Learned ${KeyEvent.keyCodeToString(e.keyCode)} (${e.keyCode})",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return true
        }
        if (e.action == KeyEvent.ACTION_UP && e.keyCode == swallowUp) {
            swallowUp = -1
            return true
        }

        val c = cfg
        for (t in c.triggers) {
            if (t.keyCode != e.keyCode) continue
            if (e.action == KeyEvent.ACTION_DOWN) {
                if (!t.enabled || e.repeatCount > 0) continue
                if (t.power) {
                    when (c.powerMode) {
                        PowerKeyMode.HOLD -> overlay(t.id).press(c)
                        PowerKeyMode.PULSE -> overlay(t.id).pulse(c)
                        PowerKeyMode.OFF -> {}
                    }
                } else {
                    overlay(t.id).press(c)
                }
            } else if (e.action == KeyEvent.ACTION_UP) {
                if (t.power && c.powerMode != PowerKeyMode.HOLD) continue
                overlays[t.id]?.release(c)
            }
        }
        return false // never consume: the real button action (e.g. volume) still happens
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!cfg.menuWatch || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName == packageName) return
        val cn = event.className?.toString()?.lowercase() ?: ""
        val isMenu = cn.contains("globalactions") || cn.contains("powermenu") ||
            cn.contains("sidekey") || cn.contains("poweroff")
        if (isMenu && !menuShown) {
            menuShown = true
            cfg.triggers.filter { it.power && it.enabled }.forEach { overlay(it.id).press(cfg) }
        } else if (!isMenu && menuShown) {
            menuShown = false
            cfg.triggers.filter { it.power }.forEach { overlays[it.id]?.release(cfg) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // rotation: drop windows, rebuild for the new profile once the display has settled
        overlays.values.forEach { it.destroy() }
        handler.postDelayed({ syncPrewarm() }, 350)
    }

    // ---------- editor hooks ----------

    /** Edit mode: keep this button's bulge fully out (optionally with a different colour). */
    fun pin(id: String, profile: ProfileId, color: Int?) {
        val old = pinnedId
        if (old != null && old != id) overlays[old]?.unpin(cfg)
        pinnedId = id
        pinnedProfile = profile
        pinnedColor = color
        overlay(id).pin(cfg, profile, color)
    }

    fun unpin() {
        pinnedId?.let { overlays[it]?.unpin(cfg) }
        pinnedId = null
    }

    /** Plays one expand/hold/contract cycle using the saved settings, then re-pins if editing. */
    fun replay(id: String, profile: ProfileId, color: Int?) {
        val keepPinned = pinnedId != null
        unpin()
        overlay(id).pulse(cfg, profile, 700L)
        if (keepPinned) {
            val a = cfg.animFor(id)
            val total = (a.expandMs + a.pulseHoldMs + 700f + a.contractMs + 250f).toLong()
            handler.postDelayed({ pin(id, profile, color) }, total)
        }
    }

    fun demo(id: String, profile: ProfileId) {
        overlay(id).pulse(cfg, profile, 900L)
    }

    override fun onInterrupt() {
        overlays.values.forEach { it.release(cfg) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        overlays.values.forEach { it.destroy() }
        overlays.clear()
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (e: Exception) { }
        if (receiverOn) {
            try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
            receiverOn = false
        }
    }
}
