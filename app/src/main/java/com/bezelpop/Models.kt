package com.bezelpop

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class Edge(val label: String) { LEFT("Left"), RIGHT("Right"), TOP("Top"), BOTTOM("Bottom") }

enum class ProfileId(val label: String) {
    P0("Portrait 0°"), L90("Landscape 90°"), P180("Portrait 180°"), L270("Landscape 270°")
}

enum class EaseType(val label: String, val hasStrength: Boolean) {
    LINEAR("Linear", false),
    EASE_OUT("Decelerate", true),
    EASE_IN("Accelerate", true),
    EASE_IN_OUT("Ease in-out", false),
    OVERSHOOT("Overshoot", true),
    ANTICIPATE("Anticipate", true),
    BOUNCE("Bounce", false),
    SPRING("Spring", true),
    BEZIER("Custom cubic-bezier", false)
}

enum class PowerKeyMode(val label: String) {
    PULSE("Pulse on press"),
    HOLD("Follow hold (only if phone delivers key)"),
    OFF("Ignore power key")
}

enum class QuickMode(val label: String) {
    CLASSIC("Classic (retract at once)"),
    MIN_REACH("Minimum reach"),
    QUANT("Quantised steps")
}

enum class HapticLevel(val label: String) {
    OFF("Off"), TICK("Tick"), CLICK("Click"), HEAVY("Heavy")
}

internal fun JSONObject.f(k: String, d: Float): Float = optDouble(k, d.toDouble()).toFloat()

internal fun <E : Enum<E>> enumOf(values: Array<E>, name: String?, d: E): E =
    values.firstOrNull { it.name == name } ?: d

class Corner(var radius: Float, var w: Float = 1f, var h: Float = 1f) {
    fun copy() = Corner(radius, w, h)
    fun toJson(): JSONObject =
        JSONObject().put("r", radius.toDouble()).put("w", w.toDouble()).put("h", h.toDouble())

    companion object {
        fun from(o: JSONObject?, d: Corner): Corner =
            if (o == null) d.copy() else Corner(o.f("r", d.radius), o.f("w", d.w), o.f("h", d.h))
    }
}

/**
 * Canonical frame: x = depth (0 at bezel), y = along the edge.
 * Sharp outline: A(0,0) -> B(wTop,hTop) -> C(wBot,length-hBot) -> D(0,length).
 * Corners A,D are concave (bezel side), B,C convex (flat-edge side). Rounded + smoothed by ShapeBuilder.
 */
class Shape(
    var length: Float = 110f,
    var wTop: Float = 5f,
    var wBot: Float = 5f,
    var hTop: Float = 38f,
    var hBot: Float = 38f,
    var cA: Corner = Corner(150f),
    var cB: Corner = Corner(150f),
    var cC: Corner = Corner(150f),
    var cD: Corner = Corner(150f),
    var linkWidths: Boolean = true,
    var linkHeights: Boolean = true,
    var mirrorCorners: Boolean = true,
    var smooth: Boolean = true,
    var smoothing: Float = 0.8f,
    var color: Int = Color.BLACK
) {
    val cornerD: Corner get() = if (mirrorCorners) cA else cD
    val cornerC: Corner get() = if (mirrorCorners) cB else cC

    fun copy(): Shape = from(toJson())

    fun scaled(k: Float, colorOverride: Int?): Shape {
        val s = copy()
        s.length *= k; s.wTop *= k; s.wBot *= k; s.hTop *= k; s.hBot *= k
        listOf(s.cA, s.cB, s.cC, s.cD).forEach { it.radius *= k }
        if (colorOverride != null) s.color = colorOverride
        return s
    }

    fun toJson(): JSONObject = JSONObject()
        .put("length", length.toDouble())
        .put("wTop", wTop.toDouble()).put("wBot", wBot.toDouble())
        .put("hTop", hTop.toDouble()).put("hBot", hBot.toDouble())
        .put("cA", cA.toJson()).put("cB", cB.toJson()).put("cC", cC.toJson()).put("cD", cD.toJson())
        .put("linkW", linkWidths).put("linkH", linkHeights).put("mirror", mirrorCorners)
        .put("smooth", smooth).put("smoothing", smoothing.toDouble()).put("color", color)

    companion object {
        fun from(o: JSONObject?): Shape {
            val d = Shape()
            if (o == null) return d
            return Shape(
                o.f("length", d.length), o.f("wTop", d.wTop), o.f("wBot", d.wBot),
                o.f("hTop", d.hTop), o.f("hBot", d.hBot),
                Corner.from(o.optJSONObject("cA"), d.cA), Corner.from(o.optJSONObject("cB"), d.cB),
                Corner.from(o.optJSONObject("cC"), d.cC), Corner.from(o.optJSONObject("cD"), d.cD),
                o.optBoolean("linkW", true), o.optBoolean("linkH", true), o.optBoolean("mirror", true),
                o.optBoolean("smooth", true), o.f("smoothing", d.smoothing), o.optInt("color", d.color)
            )
        }
    }
}

/** pos = centre of the bulge along the edge, 0..100 % (left→right or top→bottom). */
class Placement(var edge: Edge, var pos: Float, var offset: Float = 0f)

class Profile(
    var shape: Shape = Shape(),
    val placements: MutableMap<String, Placement> = mutableMapOf()
) {
    fun toJson(): JSONObject {
        val pl = JSONObject()
        placements.forEach { (k, v) ->
            pl.put(k, JSONObject().put("e", v.edge.name).put("p", v.pos.toDouble()).put("o", v.offset.toDouble()))
        }
        return JSONObject().put("shape", shape.toJson()).put("pl", pl)
    }

    companion object {
        fun from(o: JSONObject?): Profile {
            if (o == null) return Profile()
            val p = Profile(Shape.from(o.optJSONObject("shape")))
            val pl = o.optJSONObject("pl")
            if (pl != null) {
                for (k in pl.keys()) {
                    val po = pl.getJSONObject(k)
                    p.placements[k] = Placement(
                        enumOf(Edge.values(), po.optString("e"), Edge.RIGHT), po.f("p", 50f), po.f("o", 0f)
                    )
                }
            }
            return p
        }
    }
}

class Ease(
    var type: EaseType,
    var strength: Float = 2f,
    var bz: FloatArray = floatArrayOf(0.25f, 0.1f, 0.25f, 1f)
) {
    fun copy() = Ease(type, strength, bz.copyOf())

    fun toJson(): JSONObject = JSONObject().put("t", type.name).put("s", strength.toDouble())
        .put("b", JSONArray().apply { bz.forEach { put(it.toDouble()) } })

    companion object {
        fun from(o: JSONObject?, d: Ease): Ease {
            if (o == null) return d.copy()
            val b = o.optJSONArray("b")
            val bz = FloatArray(4) { i -> b?.optDouble(i, d.bz[i].toDouble())?.toFloat() ?: d.bz[i] }
            return Ease(enumOf(EaseType.values(), o.optString("t"), d.type), o.f("s", d.strength), bz)
        }
    }
}

class Anim(
    var expandMs: Float = 220f,
    var contractMs: Float = 260f,
    var expandEase: Ease = Ease(EaseType.EASE_OUT, 2f),
    var contractEase: Ease = Ease(EaseType.EASE_IN_OUT),
    var pulseHoldMs: Float = 250f,
    var maxHoldSec: Float = 15f,
    var latencyMs: Float = 12f,
    var lowLatency: Boolean = true,
    var hapticExpand: HapticLevel = HapticLevel.OFF,
    var hapticContract: HapticLevel = HapticLevel.OFF,
    var quickMode: QuickMode = QuickMode.CLASSIC,
    var minReach: Float = 60f,
    var steps: String = "40,70,100",
    var quickMs: Float = 140f,
    var measureLatency: Boolean = false
) {
    fun copy(): Anim = from(toJson())

    fun toJson(): JSONObject = JSONObject()
        .put("exp", expandMs.toDouble()).put("con", contractMs.toDouble())
        .put("expE", expandEase.toJson()).put("conE", contractEase.toJson())
        .put("pulse", pulseHoldMs.toDouble()).put("maxHold", maxHoldSec.toDouble())
        .put("lat", latencyMs.toDouble()).put("lowLat", lowLatency)
        .put("hExp", hapticExpand.name).put("hCon", hapticContract.name)
        .put("qm", quickMode.name).put("minR", minReach.toDouble()).put("steps", steps)
        .put("qMs", quickMs.toDouble()).put("measure", measureLatency)

    companion object {
        fun from(o: JSONObject?): Anim {
            val d = Anim()
            if (o == null) return d
            return Anim(
                o.f("exp", d.expandMs), o.f("con", d.contractMs),
                Ease.from(o.optJSONObject("expE"), d.expandEase),
                Ease.from(o.optJSONObject("conE"), d.contractEase),
                o.f("pulse", d.pulseHoldMs), o.f("maxHold", d.maxHoldSec),
                o.f("lat", d.latencyMs), o.optBoolean("lowLat", true),
                enumOf(HapticLevel.values(), o.optString("hExp"), HapticLevel.OFF),
                enumOf(HapticLevel.values(), o.optString("hCon"), HapticLevel.OFF),
                enumOf(QuickMode.values(), o.optString("qm"), QuickMode.CLASSIC),
                o.f("minR", d.minReach), o.optString("steps", d.steps), o.f("qMs", d.quickMs),
                o.optBoolean("measure", false)
            )
        }
    }
}

/** Per-button overrides. null / 1.0 = inherit the global value. */
class Overrides(
    var color: Int? = null,
    var scale: Float = 1f,
    var expandMs: Float? = null,
    var contractMs: Float? = null,
    var expandEase: Ease? = null,
    var contractEase: Ease? = null,
    var hapticExpand: HapticLevel? = null,
    var hapticContract: HapticLevel? = null
) {
    fun toJson(): JSONObject {
        val o = JSONObject().put("scale", scale.toDouble())
        color?.let { o.put("color", it) }
        expandMs?.let { o.put("exp", it.toDouble()) }
        contractMs?.let { o.put("con", it.toDouble()) }
        expandEase?.let { o.put("expE", it.toJson()) }
        contractEase?.let { o.put("conE", it.toJson()) }
        hapticExpand?.let { o.put("hExp", it.name) }
        hapticContract?.let { o.put("hCon", it.name) }
        return o
    }

    companion object {
        fun from(o: JSONObject?): Overrides {
            if (o == null) return Overrides()
            val base = Ease(EaseType.EASE_OUT)
            return Overrides(
                if (o.has("color")) o.optInt("color") else null,
                o.f("scale", 1f),
                if (o.has("exp")) o.f("exp", 220f) else null,
                if (o.has("con")) o.f("con", 260f) else null,
                if (o.has("expE")) Ease.from(o.optJSONObject("expE"), base) else null,
                if (o.has("conE")) Ease.from(o.optJSONObject("conE"), base) else null,
                if (o.has("hExp")) enumOf(HapticLevel.values(), o.optString("hExp"), HapticLevel.OFF) else null,
                if (o.has("hCon")) enumOf(HapticLevel.values(), o.optString("hCon"), HapticLevel.OFF) else null
            )
        }
    }
}

class Trigger(
    val id: String,
    var name: String,
    var keyCode: Int,
    var enabled: Boolean = true,
    val power: Boolean = false,
    val builtin: Boolean = false,
    var ov: Overrides = Overrides()
)

class UiPrefs(
    var editInvert: Boolean = true,
    var editColor: Int = 0xFFFF4081.toInt(),
    var angleMode: Boolean = true,
    var appearance: String = "sys",
    var accent: Int? = null,
    var grey: Boolean = false
)

class Chord(
    var enabled: Boolean = false,
    var a: String = "vol_up",
    var b: String = "vol_down",
    var screenshot: Boolean = true,
    var delayMs: Float = 350f
)

class Config(
    val triggers: MutableList<Trigger>,
    val profiles: MutableMap<ProfileId, Profile>,
    var anim: Anim = Anim(),
    var powerMode: PowerKeyMode = PowerKeyMode.PULSE,
    var pulseOnScreen: Boolean = false,
    var menuWatch: Boolean = false,
    var ui: UiPrefs = UiPrefs(),
    var chord: Chord = Chord()
) {
    /** Changes every time a Config object is created; overlays use it to know when to rebuild. */
    val rev: Int = ++counter

    fun trigger(id: String): Trigger? = triggers.firstOrNull { it.id == id }

    fun placement(pid: ProfileId, tid: String): Placement =
        profiles.getValue(pid).placements.getOrPut(tid) { defaultPlacement(pid, tid) }

    fun effectiveShape(pid: ProfileId, t: Trigger, colorOverride: Int?): Shape =
        profiles.getValue(pid).shape.scaled(t.ov.scale, colorOverride ?: t.ov.color)

    fun animFor(tid: String): Anim {
        val a = anim.copy()
        val t = trigger(tid) ?: return a
        t.ov.expandMs?.let { a.expandMs = it }
        t.ov.contractMs?.let { a.contractMs = it }
        t.ov.expandEase?.let { a.expandEase = it.copy() }
        t.ov.contractEase?.let { a.contractEase = it.copy() }
        t.ov.hapticExpand?.let { a.hapticExpand = it }
        t.ov.hapticContract?.let { a.hapticContract = it }
        return a
    }

    fun toJson(): JSONObject {
        val tr = JSONArray()
        triggers.forEach {
            tr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("key", it.keyCode)
                    .put("on", it.enabled).put("power", it.power).put("builtin", it.builtin)
                    .put("ov", it.ov.toJson())
            )
        }
        val pr = JSONObject()
        profiles.forEach { (k, v) -> pr.put(k.name, v.toJson()) }
        return JSONObject().put("triggers", tr).put("profiles", pr).put("anim", anim.toJson())
            .put("powerMode", powerMode.name).put("pulseScreen", pulseOnScreen)
            .put("menuWatch", menuWatch)
            .put("ui", JSONObject().put("invert", ui.editInvert).put("editColor", ui.editColor).put("angle", ui.angleMode).put("app", ui.appearance).put("grey", ui.grey).apply { ui.accent?.let { put("accent", it) } })
            .put("chord", JSONObject().put("on", chord.enabled).put("a", chord.a).put("b", chord.b).put("shot", chord.screenshot).put("delay", chord.delayMs.toDouble()))
    }

    companion object {
        private var counter = 0
        private const val KEYCODE_VOLUME_UP = 24
        private const val KEYCODE_VOLUME_DOWN = 25
        private const val KEYCODE_POWER = 26

        fun defaultTriggers() = mutableListOf(
            Trigger("vol_up", "Volume up", KEYCODE_VOLUME_UP, builtin = true),
            Trigger("vol_down", "Volume down", KEYCODE_VOLUME_DOWN, builtin = true),
            Trigger("power", "Power", KEYCODE_POWER, power = true, builtin = true)
        )

        /** Defaults assume a right-edge button rocker in natural portrait, mapped per rotation. */
        fun defaultPlacement(p: ProfileId, tid: String): Placement {
            val pct = when (tid) { "vol_up" -> 24f; "vol_down" -> 30f; "power" -> 38f; else -> 50f }
            return when (p) {
                ProfileId.P0 -> Placement(Edge.RIGHT, pct)
                ProfileId.L90 -> Placement(Edge.TOP, pct)
                ProfileId.P180 -> Placement(Edge.LEFT, 100f - pct)
                ProfileId.L270 -> Placement(Edge.BOTTOM, 100f - pct)
            }
        }

        fun default(): Config {
            val profiles = mutableMapOf<ProfileId, Profile>()
            ProfileId.values().forEach { profiles[it] = Profile() }
            return Config(defaultTriggers(), profiles)
        }

        fun fromJson(o: JSONObject): Config {
            val triggers = mutableListOf<Trigger>()
            val arr = o.optJSONArray("triggers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    triggers.add(
                        Trigger(
                            t.getString("id"), t.optString("name", "Button"), t.optInt("key", 0),
                            t.optBoolean("on", true), t.optBoolean("power", false),
                            t.optBoolean("builtin", false), Overrides.from(t.optJSONObject("ov"))
                        )
                    )
                }
            }
            if (triggers.isEmpty()) triggers.addAll(defaultTriggers())
            val profiles = mutableMapOf<ProfileId, Profile>()
            val po = o.optJSONObject("profiles")
            ProfileId.values().forEach { profiles[it] = Profile.from(po?.optJSONObject(it.name)) }
            val u = o.optJSONObject("ui")
            val ui = if (u == null) UiPrefs() else UiPrefs(
                u.optBoolean("invert", true), u.optInt("editColor", 0xFFFF4081.toInt()), u.optBoolean("angle", true),
                u.optString("app", "sys"), if (u.has("accent")) u.optInt("accent") else null, u.optBoolean("grey", false)
            )
            val ch = o.optJSONObject("chord")
            val chord = if (ch == null) Chord() else Chord(
                ch.optBoolean("on", false), ch.optString("a", "vol_up"), ch.optString("b", "vol_down"),
                ch.optBoolean("shot", true), ch.f("delay", 350f)
            )
            return Config(
                triggers, profiles, Anim.from(o.optJSONObject("anim")),
                enumOf(PowerKeyMode.values(), o.optString("powerMode"), PowerKeyMode.PULSE),
                o.optBoolean("pulseScreen", false), o.optBoolean("menuWatch", false), ui, chord
            )
        }

        fun parse(text: String): Config? =
            try { fromJson(JSONObject(text)) } catch (e: Exception) { null }
    }
}

object Store {
    private const val KEY = "config2"

    fun prefs(c: Context) = c.getSharedPreferences("bezelpop", Context.MODE_PRIVATE)

    fun load(c: Context): Config {
        val s = prefs(c).getString(KEY, null) ?: return Config.default()
        return Config.parse(s) ?: Config.default()
    }

    fun save(c: Context, cfg: Config) {
        prefs(c).edit().putString(KEY, cfg.toJson().toString()).apply()
    }

    const val CONFIG_KEY = KEY
}
