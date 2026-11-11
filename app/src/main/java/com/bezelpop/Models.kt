package com.bezelpop

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class Edge(val label: String) { LEFT("Left"), RIGHT("Right"), TOP("Top"), BOTTOM("Bottom") }

enum class ProfileId(val label: String) {
    PORTRAIT("Portrait"), LANDSCAPE1("Landscape 1"), LANDSCAPE2("Landscape 2")
}

enum class EaseType(val label: String, val hasStrength: Boolean) {
    LINEAR("Linear", false),
    EASE_OUT("Ease out (decelerate)", true),
    EASE_IN("Ease in (accelerate)", true),
    EASE_IN_OUT("Ease in-out", false),
    OVERSHOOT("Overshoot", true),
    ANTICIPATE("Anticipate", true),
    BOUNCE("Bounce", false),
    BEZIER("Custom cubic-bezier", false)
}

enum class PowerKeyMode(val label: String) {
    PULSE("Pulse on press"),
    HOLD("Follow hold (only if phone delivers the key)"),
    OFF("Ignore power key")
}

internal fun JSONObject.f(k: String, d: Float): Float = optDouble(k, d.toDouble()).toFloat()

internal fun <E : Enum<E>> enumOf(values: Array<E>, name: String?, d: E): E =
    values.firstOrNull { it.name == name } ?: d

class Corner(var radius: Float, var w: Float = 1f, var h: Float = 1f) {
    fun toJson(): JSONObject =
        JSONObject().put("r", radius.toDouble()).put("w", w.toDouble()).put("h", h.toDouble())

    companion object {
        fun from(o: JSONObject?, d: Corner): Corner =
            if (o == null) Corner(d.radius, d.w, d.h)
            else Corner(o.f("r", d.radius), o.f("w", d.w), o.f("h", d.h))
    }
}

/**
 * Canonical bulge: x = depth (0 at bezel), y = along the edge.
 * "First end" = top/left end, "second end" = bottom/right end.
 * Concave = corner next to the bezel, convex = corner next to the flat edge.
 */
class Shape(
    var depth: Float = 9f,
    var length: Float = 90f,
    var tConcave: Corner = Corner(3f),
    var tConvex: Corner = Corner(3f),
    var bConcave: Corner = Corner(3f),
    var bConvex: Corner = Corner(3f),
    var mirror: Boolean = true,
    var smooth: Boolean = true,
    var smoothing: Float = 0.6f,
    var color: Int = Color.BLACK
) {
    val bcc: Corner get() = if (mirror) tConcave else bConcave
    val bcv: Corner get() = if (mirror) tConvex else bConvex

    fun toJson(): JSONObject = JSONObject()
        .put("depth", depth.toDouble()).put("length", length.toDouble())
        .put("tcc", tConcave.toJson()).put("tcv", tConvex.toJson())
        .put("bcc", bConcave.toJson()).put("bcv", bConvex.toJson())
        .put("mirror", mirror).put("smooth", smooth)
        .put("smoothing", smoothing.toDouble()).put("color", color)

    companion object {
        fun from(o: JSONObject?): Shape {
            val d = Shape()
            if (o == null) return d
            return Shape(
                o.f("depth", d.depth), o.f("length", d.length),
                Corner.from(o.optJSONObject("tcc"), d.tConcave),
                Corner.from(o.optJSONObject("tcv"), d.tConvex),
                Corner.from(o.optJSONObject("bcc"), d.bConcave),
                Corner.from(o.optJSONObject("bcv"), d.bConvex),
                o.optBoolean("mirror", d.mirror), o.optBoolean("smooth", d.smooth),
                o.f("smoothing", d.smoothing), o.optInt("color", d.color)
            )
        }
    }
}

/** pos = centre of the bulge along the edge, 0..100 % (left→right or top→bottom). */
class Placement(var edge: Edge, var pos: Float)

class Profile(
    var shape: Shape = Shape(),
    val placements: MutableMap<String, Placement> = mutableMapOf()
) {
    fun toJson(): JSONObject {
        val pl = JSONObject()
        placements.forEach { (k, v) ->
            pl.put(k, JSONObject().put("e", v.edge.name).put("p", v.pos.toDouble()))
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
                        enumOf(Edge.values(), po.optString("e"), Edge.RIGHT), po.f("p", 50f)
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
    fun toJson(): JSONObject = JSONObject().put("t", type.name).put("s", strength.toDouble())
        .put("b", JSONArray().apply { bz.forEach { put(it.toDouble()) } })

    companion object {
        fun from(o: JSONObject?, d: Ease): Ease {
            if (o == null) return Ease(d.type, d.strength, d.bz.copyOf())
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
    var maxHoldSec: Float = 15f
) {
    fun toJson(): JSONObject = JSONObject()
        .put("exp", expandMs.toDouble()).put("con", contractMs.toDouble())
        .put("expE", expandEase.toJson()).put("conE", contractEase.toJson())
        .put("pulse", pulseHoldMs.toDouble()).put("maxHold", maxHoldSec.toDouble())

    companion object {
        fun from(o: JSONObject?): Anim {
            val d = Anim()
            if (o == null) return d
            return Anim(
                o.f("exp", d.expandMs), o.f("con", d.contractMs),
                Ease.from(o.optJSONObject("expE"), d.expandEase),
                Ease.from(o.optJSONObject("conE"), d.contractEase),
                o.f("pulse", d.pulseHoldMs), o.f("maxHold", d.maxHoldSec)
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
    val builtin: Boolean = false
)

class Config(
    val triggers: MutableList<Trigger>,
    val profiles: MutableMap<ProfileId, Profile>,
    var anim: Anim = Anim(),
    var powerMode: PowerKeyMode = PowerKeyMode.PULSE,
    var pulseOnScreen: Boolean = false,
    var menuWatch: Boolean = false
) {
    fun trigger(id: String): Trigger? = triggers.firstOrNull { it.id == id }

    fun placement(pid: ProfileId, tid: String): Placement =
        profiles.getValue(pid).placements.getOrPut(tid) { defaultPlacement(pid, tid) }

    fun toJson(): JSONObject {
        val tr = JSONArray()
        triggers.forEach {
            tr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("key", it.keyCode)
                    .put("on", it.enabled).put("power", it.power).put("builtin", it.builtin)
            )
        }
        val pr = JSONObject()
        profiles.forEach { (k, v) -> pr.put(k.name, v.toJson()) }
        return JSONObject().put("triggers", tr).put("profiles", pr).put("anim", anim.toJson())
            .put("powerMode", powerMode.name).put("pulseScreen", pulseOnScreen)
            .put("menuWatch", menuWatch)
    }

    companion object {
        private const val KEYCODE_VOLUME_UP = 24
        private const val KEYCODE_VOLUME_DOWN = 25
        private const val KEYCODE_POWER = 26

        fun defaultTriggers() = mutableListOf(
            Trigger("vol_up", "Volume up", KEYCODE_VOLUME_UP, builtin = true),
            Trigger("vol_down", "Volume down", KEYCODE_VOLUME_DOWN, builtin = true),
            Trigger("power", "Power", KEYCODE_POWER, power = true, builtin = true)
        )

        /** Defaults assume a right-edge button rocker (Samsung-style) and map it per rotation. */
        fun defaultPlacement(p: ProfileId, tid: String): Placement {
            val pct = when (tid) { "vol_up" -> 24f; "vol_down" -> 30f; "power" -> 38f; else -> 50f }
            return when (p) {
                ProfileId.PORTRAIT -> Placement(Edge.RIGHT, pct)
                ProfileId.LANDSCAPE1 -> Placement(Edge.TOP, pct)
                ProfileId.LANDSCAPE2 -> Placement(Edge.BOTTOM, 100f - pct)
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
                            t.optBoolean("builtin", false)
                        )
                    )
                }
            }
            if (triggers.isEmpty()) triggers.addAll(defaultTriggers())
            val profiles = mutableMapOf<ProfileId, Profile>()
            val po = o.optJSONObject("profiles")
            ProfileId.values().forEach { profiles[it] = Profile.from(po?.optJSONObject(it.name)) }
            return Config(
                triggers, profiles, Anim.from(o.optJSONObject("anim")),
                enumOf(PowerKeyMode.values(), o.optString("powerMode"), PowerKeyMode.PULSE),
                o.optBoolean("pulseScreen", false), o.optBoolean("menuWatch", false)
            )
        }
    }
}

object Store {
    fun prefs(c: Context) = c.getSharedPreferences("bezelpop", Context.MODE_PRIVATE)

    fun load(c: Context): Config {
        val s = prefs(c).getString("config", null) ?: return Config.default()
        return try { Config.fromJson(JSONObject(s)) } catch (e: Exception) { Config.default() }
    }

    fun save(c: Context, cfg: Config) {
        prefs(c).edit().putString("config", cfg.toJson().toString()).apply()
    }
}
