package com.bezelpop

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var cfg: Config
    private var cur = ProfileId.PORTRAIT
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var preview: PreviewView? = null
    private var statusText: TextView? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "learn_id") runOnUiThread { cfg = Store.load(this); render() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prof() = cfg.profiles.getValue(cur)
    private fun save() = Store.save(this, cfg)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cfg = Store.load(this)
        scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(48))
        }
        scroll.addView(content)
        setContentView(scroll)
        val p = Store.prefs(this)
        if (!p.getBoolean("checklist_seen", false)) {
            p.edit().putBoolean("checklist_seen", true).apply()
            startActivity(Intent(this, ChecklistActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    // ---------- small UI helpers ----------

    private fun heading(t: String) = TextView(this).apply {
        text = t; textSize = 20f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(24), 0, dp(8))
    }

    private fun sub(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(12), 0, dp(2))
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 13f }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun toggle(t: String, on: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; isChecked = on
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun slider(
        label: String, min: Float, max: Float, value: Float, fmt: String, onChange: (Float) -> Unit
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4))
        }
        val tv = TextView(this).apply { text = "$label: ${fmt.format(value)}"; textSize = 13f }
        val sb = SeekBar(this)
        sb.max = 1000
        sb.progress = (((value - min) / (max - min)) * 1000f).toInt().coerceIn(0, 1000)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = min + (max - min) * p / 1000f
                tv.text = "$label: ${fmt.format(v)}"
                onChange(v)
                preview?.invalidate()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { save() }
        })
        box.addView(tv)
        box.addView(sb)
        return box
    }

    private fun <T> spinner(
        label: String, items: List<T>, sel: T, name: (T) -> String, onSel: (T) -> Unit
    ): View {
        val tv = TextView(this).apply { text = label; textSize = 13f }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map(name))
        var idx = items.indexOf(sel)
        sp.setSelection(idx)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == idx) return
                idx = pos
                onSel(items[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv); addView(sp)
        }
    }

    // ---------- screen ----------

    private fun refreshStatus() {
        val on = Checks.serviceEnabled(this)
        statusText?.text = (if (on) "Service: ON" else "Service: OFF (enable it in Accessibility settings)") +
            "\nPhone is currently in: ${currentProfileId(this).label}"
    }

    private fun render() {
        val sy = scroll.scrollY
        content.removeAllViews()

        content.addView(TextView(this).apply { text = "BezelPop"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        statusText = TextView(this).also { content.addView(it) }
        content.addView(row(
            button("Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            button("Setup checklist") { startActivity(Intent(this, ChecklistActivity::class.java)) }
        ))
        refreshStatus()

        content.addView(heading("Profile"))
        content.addView(label("Each orientation has its own shape and button positions."))
        content.addView(row(*ProfileId.values().map { pid ->
            button(pid.label) { cur = pid; render() }.apply { isEnabled = pid != cur }
        }.toTypedArray()))
        content.addView(button("Copy shape from another profile…") {
            val others = ProfileId.values().filter { it != cur }
            AlertDialog.Builder(this).setTitle("Copy shape from")
                .setItems(others.map { it.label }.toTypedArray()) { _, i ->
                    prof().shape = Shape.from(cfg.profiles.getValue(others[i]).shape.toJson())
                    save(); render()
                }.show()
        })

        val pv = PreviewView(this)
        pv.shape = prof().shape
        preview = pv
        content.addView(pv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)).apply { topMargin = dp(12) })

        shapeSection()
        triggerSection()
        animSection()
        scroll.post { scroll.scrollTo(0, sy) }
    }

    private fun shapeSection() {
        val s = prof().shape
        content.addView(heading("Shape"))
        content.addView(slider("Depth (how far it sticks out)", 1f, 40f, s.depth, "%.1f dp") { s.depth = it })
        content.addView(slider("Length (along the edge)", 20f, 400f, s.length, "%.0f dp") { s.length = it })

        content.addView(label("Colour (#RRGGBB or #AARRGGBB)"))
        val et = EditText(this).apply {
            setText(String.format("#%08X", s.color)); setSingleLine()
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                try {
                    s.color = Color.parseColor(e.toString().trim())
                    preview?.invalidate(); save()
                } catch (ex: IllegalArgumentException) { }
            }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        content.addView(et)
        content.addView(row(
            button("Black") { et.setText("#FF000000") },
            button("White") { et.setText("#FFFFFFFF") },
            button("Grey") { et.setText("#FF3A3A3C") }
        ))

        content.addView(toggle("Smooth (Apple-style) corners", s.smooth) { s.smooth = it; save(); render() })
        if (s.smooth) {
            content.addView(slider("Smoothing (control point)", 0f, 1f, s.smoothing, "%.2f") { s.smoothing = it })
        } else {
            content.addView(label("Plain corners: straight, quarter circle, straight."))
        }
        content.addView(toggle("Mirror first end onto second end", s.mirror) { s.mirror = it; save(); render() })

        content.addView(sub("First end (top / left)"))
        cornerUi("Concave corner (bezel side)", s.tConcave)
        cornerUi("Convex corner (flat-edge side)", s.tConvex)
        if (!s.mirror) {
            content.addView(sub("Second end (bottom / right)"))
            cornerUi("Concave corner (bezel side)", s.bConcave)
            cornerUi("Convex corner (flat-edge side)", s.bConvex)
        }
    }

    private fun cornerUi(title: String, c: Corner) {
        content.addView(label(title).apply { setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, 0) })
        content.addView(slider("Radius", 0f, 40f, c.radius, "%.1f dp") { c.radius = it })
        content.addView(slider("Stretch width (depth direction)", 0.2f, 4f, c.w, "%.2fx") { c.w = it })
        content.addView(slider("Stretch height (along edge)", 0.2f, 4f, c.h, "%.2fx") { c.h = it })
    }

    private fun triggerSection() {
        content.addView(heading("Buttons"))
        content.addView(label("Every button is optional. Positions below apply to the selected profile (${cur.label})."))
        val learning = Store.prefs(this).getString("learn_id", null)

        for (t in cfg.triggers.toList()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(12))
                setBackgroundColor(0x0F000000)
            }
            card.addView(toggle(t.name, t.enabled) { t.enabled = it; save() })
            val kc = if (t.keyCode == 0) "not set yet" else "${KeyEvent.keyCodeToString(t.keyCode)} (${t.keyCode})"
            card.addView(label("Key: $kc"))
            val isLearning = learning == t.id
            val btns = mutableListOf<View>(
                button(if (isLearning) "Cancel learning" else "Learn key") {
                    if (isLearning) {
                        Store.prefs(this).edit().remove("learn_id").apply()
                    } else {
                        if (KeyService.instance == null) toast("Enable the accessibility service first")
                        Store.prefs(this).edit().putString("learn_id", t.id).apply()
                        toast("Press the physical button now")
                    }
                    render()
                },
                button("Test") {
                    val svc = KeyService.instance
                    if (svc == null) toast("Enable the accessibility service first") else svc.demo(t.id, cur)
                }
            )
            if (!t.builtin) btns.add(button("Remove") {
                cfg.triggers.remove(t); save(); render()
            })
            card.addView(row(*btns.toTypedArray()))

            val pl = cfg.placement(cur, t.id)
            card.addView(spinner("Edge", Edge.values().toList(), pl.edge, { it.label }) { pl.edge = it; save() })
            card.addView(slider("Position along edge", 0f, 100f, pl.pos, "%.1f%%") { pl.pos = it })

            if (t.power) {
                card.addView(sub("Power key options"))
                card.addView(label("Android usually does not pass the power key to accessibility services, so hold detection may be impossible on your phone. These are the fallbacks:"))
                card.addView(spinner("Power key mode", PowerKeyMode.values().toList(), cfg.powerMode, { it.label }) {
                    cfg.powerMode = it; save()
                })
                card.addView(toggle("Also pulse when the screen turns on/off", cfg.pulseOnScreen) { cfg.pulseOnScreen = it; save() })
                card.addView(toggle("Experimental: show while the power menu is open (long press)", cfg.menuWatch) { cfg.menuWatch = it; save() })
            }
            content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }

        content.addView(button("Add custom button…") {
            val input = EditText(this).apply { hint = "Name (e.g. Bixby, Essential Key, Trigger)" }
            AlertDialog.Builder(this).setTitle("New button").setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val name = input.text.toString().trim().ifEmpty { "Custom button" }
                    val t = Trigger("custom_" + System.currentTimeMillis(), name, 0)
                    cfg.triggers.add(t)
                    save()
                    Store.prefs(this).edit().putString("learn_id", t.id).apply()
                    toast("Press the physical button now (service must be on)")
                    render()
                }.setNegativeButton("Cancel", null).show()
        })
        content.addView(label("Custom buttons only work if your phone sends the button as a normal key event. Bixby / Essential / gaming triggers are often handled by the system and cannot be caught."))
    }

    private fun animSection() {
        val a = cfg.anim
        content.addView(heading("Animation"))
        content.addView(slider("Expand time", 30f, 1500f, a.expandMs, "%.0f ms") { a.expandMs = it })
        content.addView(slider("Contract time", 30f, 1500f, a.contractMs, "%.0f ms") { a.contractMs = it })
        content.addView(slider("Pulse hold time (power pulse / test)", 0f, 2000f, a.pulseHoldMs, "%.0f ms") { a.pulseHoldMs = it })
        content.addView(slider("Safety timeout if release is missed", 2f, 60f, a.maxHoldSec, "%.0f s") { a.maxHoldSec = it })
        easeUi("Expand easing", a.expandEase)
        easeUi("Contract easing", a.contractEase)
    }

    private fun easeUi(title: String, e: Ease) {
        content.addView(sub(title))
        content.addView(spinner("Curve", EaseType.values().toList(), e.type, { it.label }) {
            e.type = it; save(); render()
        })
        if (e.type.hasStrength) {
            content.addView(slider("Strength", 0.5f, 5f, e.strength, "%.2f") { e.strength = it })
        }
        if (e.type == EaseType.BEZIER) {
            content.addView(slider("x1", 0f, 1f, e.bz[0], "%.2f") { e.bz[0] = it })
            content.addView(slider("y1", -1f, 2f, e.bz[1], "%.2f") { e.bz[1] = it })
            content.addView(slider("x2", 0f, 1f, e.bz[2], "%.2f") { e.bz[2] = it })
            content.addView(slider("y2", -1f, 2f, e.bz[3], "%.2f") { e.bz[3] = it })
        }
    }
}
