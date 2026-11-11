---
title: "iOS 18 button popout app for Android"
date: 2026-11-11T20:51:47.193Z
source: "https://claude.ai/chat/0f7c9341-f7c2-45e1-9116-437b659e7a7a"
---

# iOS 18 button popout app for Android

*Exported: 11/11/2026, 20:51:47*
*Messages: 40*

---

## You `2026-11-11T02:32:38.528616Z`

![1794364169404.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/f0619196-bf19-4c31-84ca-e690b043744b/preview)

Given that I have a GitHub account for apk building, could you code for me an app that lets me recreate the iOS 18 button popout animation on any android?

- listen for button clicks that the user can specify (presets for volume and power, with additional specification options for the user for stuff like the bixby button or nothing ai key or gaming phone triggers) 

- animated popout of bezel which then stays present as long as button is held and disappears when the user lets go off button

- optimisation so the app doesn't drain power

- positioning options for each overlay

- uses android accessibility service to show as an overlay even on lock screen 

Questions or concerns before starting (don't code yet)

---

## Claude `2026-11-11T02:32:50.289666Z`

Short version: this is doable, but a few Android constraints will shape the design. Here are my concerns and questions.

**Technical concerns**

1. **Held-button detection is the hard part.** An AccessibilityService can only receive key events if it declares `flagRequestFilterKeyEvents` and `canRequestFilterKeyEvents`. Then `onKeyEvent()` gives you ACTION_DOWN and ACTION_UP, which is exactly what you need for "show while held, hide on release." But:
   - **Power button:** Android does not deliver it to accessibility services. You can't reliably detect press/release. Workarounds are screen on/off broadcasts (only a tap, no hold duration) or nothing. I'd recommend we treat power as best-effort or drop it.
   - **Volume keys:** work well, but consuming the event (returning true) stops the volume from changing. Returning false lets it change normally, which is what you want for an overlay that mirrors the system action.
   - **Bixby, Nothing Essential Key, gaming triggers:** these are mostly handled at the OEM/system level and often never reach accessibility services. Some do emit a standard keycode (e.g. a configurable key mapped to a generic keycode); others can't be caught at all. I'd build a "learn a key" feature that records whichever keycode arrives, and be upfront that it works only if the device exposes one.

2. **Lock screen:** accessibility overlays (`TYPE_ACCESSIBILITY_OVERLAY`) can draw above the lock screen on most devices, but key event filtering on the secure lock screen is restricted on some Android versions and OEMs. Expect inconsistency, and I'd list it as "works on most, not guaranteed."

3. **Battery:** an accessibility service is event-driven, so idle cost is near zero if I subscribe to no accessibility events (only key events) and keep the overlay view detached until needed. Animation uses hardware-accelerated property animations only while visible.

4. **Play Store / sideload:** since you're building APKs via GitHub, you're sideloading, so Google's accessibility policy isn't an issue. On Android 13+ you'll need to allow restricted settings for sideloaded apps before you can enable the service (App info → ⋮ → Allow restricted settings).

5. **GitHub Actions:** I'll include a workflow file that builds a debug APK and uploads it as an artifact. Debug-signed is fine for personal use.

**Design details I'd propose**
- Kotlin, minimal dependencies, no Compose in the overlay (plain Views/Canvas draw faster and lighter in a service).
- Pill/bezel slides out from the screen edge with a spring animation, fills proportionally for volume, and retracts on release.
- Per-trigger settings: keycode, screen edge, vertical/horizontal offset, size, color/transparency.

**Questions**
1. **What should the overlay show?** The iOS look is a volume slider-style pill. Should it display actual system volume level for volume keys, or just be a purely cosmetic bezel for any trigger (so Bixby/power could show something generic)?
2. **Should the original key action still happen?** (e.g., volume actually changes, which is my default.)
3. **Which Android version and phone brand are you targeting?** Samsung/Xiaomi/Nothing behave differently for key handling and battery restrictions.
4. **Do you want the system's own volume panel hidden?** That's not possible to do cleanly without root, so the Android panel would appear alongside the pop-out. Is that acceptable?
5. **Light/dark or iOS-style frosted glass?** Real blur behind an overlay is limited (Android 12+ `setBackgroundBlurRadius` works on windows on some devices), so I'd plan a translucent look with optional blur where supported.

If you're fine with the power-button and OEM-key caveats, answer these and I'll start with the project structure, service, overlay, settings UI, and the GitHub workflow.

---

## You `2026-11-11T02:37:41.896512Z`

![1794364550063.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/cc12397f-2b3c-4bef-af4a-be6b8d9ff60f/preview)

1. No volume pill. Im talking about this bezel animation circled. (Image 1)

2. Yes

3. Samsung, but if it could work on others thatd be cool. No worries if not

4. The android volume panel must appear, as the app idea isn't a popout volume panel but rather the black bezel bulging a little (iPhone X notch shaped but way flatter and longer) as seen in photo

5. Full black. Colour option could be good for users on phones with white bezels ig?

Any other questions or concerns, don't code yet

---

## Claude `2026-11-11T02:37:55.408091Z`

Got it, that makes it simpler: the overlay is a black shape growing out of the screen edge, like a flat, elongated notch. It only has to draw one path, with no slider logic.

**Concerns**
1. **Alignment with the real bezel.** The shape must sit flush with the physical screen edge, and Samsung's rounded or curved edges make that tricky. I'll add a live preview in settings with sliders for depth, length, corner radius, and vertical position, so you can match it to your phone by eye.
2. **Overlap with Samsung's volume panel.** On One UI the volume panel appears near the same edge as the buttons, so it may touch the bulge. A per-button vertical offset lets you nudge it clear, but you'll want to tweak it.
3. **Power button.** It still can't be detected as a hold. I'd offer a best-effort option that pulses the bulge briefly on screen on/off, or drop it.
4. **Samsung background killing.** One UI can silently kill accessibility services. You'll need to set the app to "Unrestricted" battery, and I'll add a first-run checklist screen (enable service, allow restricted settings, battery exemption).
5. **Holding a volume key** sends repeated down events, so I'll debounce them to keep the animation smooth and cheap.

**Questions**
1. Are your buttons on the right edge, and roughly how far down from the top is the volume rocker? (I'll default to right and adjust with the preview.)
2. Should the volume-up and volume-down keys each have their own bulge position, or one shared bulge that covers both?
3. When you release, should the bulge retract instantly (like iOS) or after a short delay of about 150 ms?
4. For the power button, do you want the pulse option or none?

If you answer those, I'll start coding.

---

## You `2026-11-11T02:44:50.991357Z`

Make every button optional

1. Yes please. As the shape has 2 corners (1 concave then 1 convex), then the flat edge, then another 2 corners (1 convex then 1 concave), please add customisable stretch (width/height) and corner radius for each, as well as height and width for t whole thing ofc. Let me know if you understand what I mean by the corners and concave stuff, with a diagram if you can

3. See if there's any other experimental ways you can make the power button be detected as a hold and implement any/all as options for it. If not, do "pulse for short duration" on any press of power key, and also add the "on screen on/off" as an optional toggle 

4. Cool. Make the first run checklist skippable tho, and returnable to, later. 

5. Cool

1. I've got the S25 edge if you can find schematics for that then use them but just default to something generic if not

2. Individual bulge position for each of those buttons

3. Start the retraction instantly. The retraction itself should take a reasonable amount of time. Allow users to customise all animation times and anything else necessary. 

4. Answered

Don't code yet just need the diagram real quick

---

## Claude `2026-11-11T02:45:27.535771Z`

Each end of the bulge has the same pair of corners:

- **Concave (1):** the curve that leaves the bezel and flares into the screen. Its center sits outside the shape.
- **Convex (2):** the curve that rounds the slope into the flat edge. Its center sits inside the shape.
- **Slope:** a short straight run between the two corners. It disappears if the corner radii are large enough to meet.
- **Flat edge (3):** the straight section parallel to the bezel.

The top and bottom ends are mirror images, but each end gets its own settings. That lets the shape be asymmetric if your phone needs it.

Per end, you can set:
- the concave corner's radius and stretch (width and height)
- the convex corner's radius and stretch (width and height)

For the whole shape, you can set:
- the depth (how far it reaches into the screen)
- the total length

If that matches what you pictured, tell me and I'll start coding.

---

## You `2026-11-11T02:51:08.572102Z`

Perfect! Although in reality the bulge wouldn't flare out nearly that much, I do recognise that you've just done  it that way so I can see  each part individually.

In terms of the animation itself, the bulge should
- be invisible (button not yet pressed)
- [button pressed]
- stretch out horizontally until it reaches user defined width
- stay out until button released
- squish horizontally back into the frame until not visible anymore.
No translational movement, only width-scaling anchored to the side bezel. (Height scaling anchored to the "top" bezel if in landscape)

Please add option for smoothed "apple corners" (G3 rounding or bezier or whatever) with custom control pt and radius, vs simple straight-quartercircle-straight G1 corners with custom radius.

Please add custom global animation easing options for both the expand and the contract individually.

Please add separate customisation profiles for portrait, landscape 1, and landscape 2! If you cannot differentiate l1 and l2, tell me rq before coding

---

## Claude `2026-11-11T02:59:39.580459Z`

Yes, I can tell Landscape 1 and 2 apart: Android reports the display rotation, so 90° and 270° each get their own profile. Upside-down portrait reuses the portrait profile. I'll start coding now.

Still building: the service and overlay are done, and I'm now writing the settings screen and setup checklist.

Two compile problems turned up while I was reviewing the code. I'm fixing them, then I'll add the README and zip the project.

I've built the project, but I couldn't compile it here (no network), so the first GitHub build may flag a small error. If it does, paste me the log and I'll fix it.

**To build:** unzip `BezelPop.zip`, push it to a repo's `main` branch, and run the "Build APK" workflow in the Actions tab. Download `app-release.apk` from the artifact. It's signed with the debug key, which is fine for personal use.

**What's in it**
- **Animation:** the bulge scales out from the bezel with no movement, stays while the button is held, and starts squishing back the instant you let go. Anything that scales along the edge's depth direction works on all four edges. A landscape top-bezel bulge scales vertically, as you described.
- **Profiles:** Portrait, Landscape 1 and Landscape 2 each have their own shape and button positions. Upside-down portrait reuses Portrait.
- **Corners:** each end has a concave and a convex corner with its own radius, width stretch and height stretch. A switch picks plain quarter-circle corners or smoothed "Apple" corners with a smoothing slider. A "mirror" switch copies the first end onto the second.
- **Easing and timing:** expand and contract have separate times and separate curves (linear, ease in/out, overshoot, anticipate, bounce, or a custom cubic-bezier). You can also set the pulse hold time and a safety timeout in case a button release is missed.
- **Buttons:** volume up, volume down and power are on by default, and every button can be switched off. You can add custom buttons with a "Learn key" option. Each button has its own edge and position per profile, and there's a Test button for the real overlay.
- **Power key:** modes are pulse on press (default), follow hold, or ignore. There's also a toggle to pulse when the screen turns on or off, and an experimental one that shows the bulge while the power menu is open.
- **Setup checklist:** it's skippable, you can reopen it from the main screen, and it checks the service and battery status for you.

**Things to know**
- I couldn't find S25 Edge schematics, so the defaults are generic: 9 dp deep, 90 dp long. The live preview in the settings lets you match your phone by eye.
- The power key likely won't register as a hold on your phone, so the pulse and screen-on options will probably be what works.
- The default button positions assume a right-edge rocker. Rotation presets are guesses I haven't checked on a real phone, so check them with Test in each orientation.

> **Artifact: Models.kt** (Data models and JSON persistence)

```kotlin
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
            pl?.keys()?.forEach { k ->
                val po = pl.getJSONObject(k)
                p.placements[k] = Placement(
                    enumOf(Edge.values(), po.optString("e"), Edge.RIGHT), po.f("p", 50f)
                )
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

```

> **Artifact: Drawing.kt** (Shape path builder, views, easing)

```kotlin
package com.bezelpop

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

fun Ease.interpolator(): TimeInterpolator = when (type) {
    EaseType.LINEAR -> LinearInterpolator()
    EaseType.EASE_OUT -> DecelerateInterpolator(strength)
    EaseType.EASE_IN -> AccelerateInterpolator(strength)
    EaseType.EASE_IN_OUT -> AccelerateDecelerateInterpolator()
    EaseType.OVERSHOOT -> OvershootInterpolator(strength)
    EaseType.ANTICIPATE -> AnticipateInterpolator(strength)
    EaseType.BOUNCE -> BounceInterpolator()
    EaseType.BEZIER -> PathInterpolator(bz[0].coerceIn(0f, 1f), bz[1], bz[2].coerceIn(0f, 1f), bz[3])
}

object ShapeBuilder {

    fun build(s: Shape, edge: Edge, density: Float): Path =
        buildPx(s, edge, s.depth * density, s.length * density, density)

    /**
     * Builds the bulge outline in view pixels, bezel on the side given by [edge].
     * Canonical frame: x = depth (0 at bezel), y = along the edge.
     * Outline: bezel -> concave corner -> slope -> convex corner -> flat edge -> convex -> slope -> concave -> bezel.
     * Each corner is a "Figma-style" smoothed corner; smoothing 0 gives a plain (elliptical) quarter circle.
     */
    fun buildPx(s: Shape, edge: Edge, dPx: Float, lPx: Float, density: Float): Path {
        val dD = max(1f, dPx)
        val dL = max(2f, lPx)
        val sm = if (s.smooth) s.smoothing.coerceIn(0f, 1f) else 0f
        val k1 = 1f + sm
        val tcc = s.tConcave
        val tcv = s.tConvex
        val bcc = s.bcc
        val bcv = s.bcv

        fun ext(c: Corner) = c.radius * density * k1

        val depthTop = ext(tcc) * tcc.w + ext(tcv) * tcv.w
        val depthBot = ext(bcc) * bcc.w + ext(bcv) * bcv.w
        val along = ext(tcc) * tcc.h + ext(tcv) * tcv.h + ext(bcv) * bcv.h + ext(bcc) * bcc.h
        var k = 1f
        val md = max(depthTop, depthBot)
        if (md > dD) k = min(k, dD / md)
        if (along > dL) k = min(k, dL / along)

        fun r(c: Corner) = c.radius * density * k

        val path = Path()
        val yA = ext(tcc) * k * tcc.h
        val yB = ext(bcc) * k * bcc.h

        corner(path, 0f, yA, 0f, 1f, 1f, 0f, r(tcc), sm, tcc.h, tcc.w, true)
        corner(path, dD, yA, 1f, 0f, 0f, 1f, r(tcv), sm, tcv.w, tcv.h, false)
        corner(path, dD, dL - yB, 0f, 1f, -1f, 0f, r(bcv), sm, bcv.h, bcv.w, false)
        corner(path, 0f, dL - yB, -1f, 0f, 0f, 1f, r(bcc), sm, bcc.w, bcc.h, false)
        path.close()

        val m = Matrix()
        when (edge) {
            Edge.LEFT -> return path
            Edge.RIGHT -> m.setValues(floatArrayOf(-1f, 0f, dD, 0f, 1f, 0f, 0f, 0f, 1f))
            Edge.TOP -> m.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            Edge.BOTTOM -> m.setValues(floatArrayOf(0f, 1f, 0f, -1f, 0f, dD, 0f, 0f, 1f))
        }
        path.transform(m)
        return path
    }

    /**
     * One 90 degree corner. (cx,cy) = sharp vertex, u = incoming direction, v = outgoing direction.
     * su / sv stretch the corner along u / v (ellipse instead of circle when != 1).
     */
    private fun corner(
        p: Path, cx: Float, cy: Float, ux: Float, uy: Float, vx: Float, vy: Float,
        r: Float, sm: Float, su: Float, sv: Float, first: Boolean
    ) {
        if (r < 0.01f) {
            if (first) p.moveTo(cx, cy) else p.lineTo(cx, cy)
            return
        }
        val pp = (1.0 + sm) * r
        val theta = Math.toRadians(90.0 * (1.0 - sm))
        val alpha = (Math.PI / 2 - theta) / 2
        val beta = Math.toRadians(45.0 * sm)
        val arcLen = sin(theta / 2) * r * sqrt(2.0)
        val p34 = r * tan(alpha / 2)
        val c = p34 * cos(beta)
        val d = c * tan(beta)
        val b = (pp - arcLen - c - d) / 3.0
        val a = 2 * b

        fun px(xu: Double, xv: Double) = (cx + ux * ((xu - pp) * su) + vx * (xv * sv)).toFloat()
        fun py(xu: Double, xv: Double) = (cy + uy * ((xu - pp) * su) + vy * (xv * sv)).toFloat()

        if (first) p.moveTo(px(0.0, 0.0), py(0.0, 0.0)) else p.lineTo(px(0.0, 0.0), py(0.0, 0.0))

        val a0u = a + b + c
        val a0v = d
        p.cubicTo(
            px(a, 0.0), py(a, 0.0),
            px(a + b, 0.0), py(a + b, 0.0),
            px(a0u, a0v), py(a0u, a0v)
        )

        val a1u = a0u + arcLen
        val a1v = a0v + arcLen
        val hh = 4.0 / 3.0 * tan(theta / 4) * r
        val c1u = a0u + cos(beta) * hh
        val c1v = a0v + sin(beta) * hh
        val c2u = a1u - cos(beta + theta) * hh
        val c2v = a1v - sin(beta + theta) * hh
        p.cubicTo(px(c1u, c1v), py(c1u, c1v), px(c2u, c2v), py(c2u, c2v), px(a1u, a1v), py(a1u, a1v))

        p.cubicTo(
            px(a1u + d, a1v + c), py(a1u + d, a1v + c),
            px(a1u + d, a1v + b + c), py(a1u + d, a1v + b + c),
            px(a1u + d, a1v + a + b + c), py(a1u + d, a1v + a + b + c)
        )
    }
}

class BulgeView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var path: Path? = null

    fun setPath(p: Path, color: Int) {
        path = p
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        path?.let { canvas.drawPath(it, paint) }
    }
}

/** Zoomed preview used in the settings screen (bezel on the right). */
class PreviewView(ctx: Context) : View(ctx) {
    var shape: Shape = Shape()
    private val screenP = Paint().apply { color = 0xFFEDEDED.toInt() }
    private val bezelP = Paint().apply { color = 0xFF444444.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x88888888.toInt(); strokeWidth = 1.5f
    }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF555555.toInt(); textSize = 30f }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bezelW = w * 0.12f
        canvas.drawRect(0f, 0f, w, h, screenP)
        canvas.drawRect(w - bezelW, 0f, w, h, bezelP)
        val z = min((w - bezelW) * 0.8f / shape.depth, h * 0.9f / shape.length)
        val dW = shape.depth * z
        val dL = shape.length * z
        val path = ShapeBuilder.buildPx(shape, Edge.RIGHT, dW, dL, z)
        canvas.save()
        canvas.translate(w - bezelW - dW, (h - dL) / 2f)
        fill.color = shape.color
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
        canvas.restore()
        canvas.drawText("Zoom x%.1f".format(z / resources.displayMetrics.density), 16f, 40f, tp)
    }
}

```

> **Artifact: BulgeOverlay.kt** (Overlay window + animation controller)

```kotlin
package com.bezelpop

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun currentProfileId(ctx: Context): ProfileId {
    val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return when (dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
        Surface.ROTATION_90 -> ProfileId.LANDSCAPE1
        Surface.ROTATION_270 -> ProfileId.LANDSCAPE2
        else -> ProfileId.PORTRAIT
    }
}

/**
 * One overlay per trigger. The window only exists while the bulge is visible (or animating),
 * so idle cost is zero. The animation is a pure scale of a pre-built path, anchored at the bezel.
 */
class BulgeOverlay(private val svc: AccessibilityService, private val trigId: String) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var bulge: BulgeView? = null
    private var vertical = false
    private var sig = ""
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private var lastCfg: Config? = null
    private val auto = Runnable { lastCfg?.let { release(it) } }

    fun press(cfg: Config, forced: ProfileId? = null) {
        handler.removeCallbacks(auto)
        lastCfg = cfg
        val pid = forced ?: currentProfileId(svc)
        val profile = cfg.profiles.getValue(pid)
        val pl = cfg.placement(pid, trigId)
        val newSig = pid.name + pl.edge.name + pl.pos + profile.shape.toJson().toString()
        if (root != null && newSig != sig) detach()
        if (root == null) {
            if (!attach(profile.shape, pl)) return
            sig = newSig
        }
        animateTo(1f, cfg.anim.expandMs, cfg.anim.expandEase)
        handler.postDelayed(auto, (cfg.anim.maxHoldSec * 1000f).toLong())
    }

    fun release(cfg: Config) {
        handler.removeCallbacks(auto)
        if (root == null) return
        animateTo(0f, cfg.anim.contractMs, cfg.anim.contractEase)
    }

    fun pulse(cfg: Config, forced: ProfileId? = null, extraHoldMs: Long = 0L) {
        press(cfg, forced)
        handler.removeCallbacks(auto)
        handler.postDelayed(auto, (cfg.anim.expandMs + cfg.anim.pulseHoldMs).toLong() + extraHoldMs)
    }

    fun destroy() {
        handler.removeCallbacks(auto)
        detach()
    }

    private fun setProgress(v: Float) {
        progress = max(0f, v)
        bulge?.let { if (vertical) it.scaleY = progress else it.scaleX = progress }
    }

    private fun animateTo(target: Float, ms: Float, ease: Ease) {
        anim?.cancel()
        val from = progress
        val dur = (ms * abs(target - from)).toLong()
        if (dur <= 0L) {
            setProgress(target)
            if (target == 0f) detach()
            return
        }
        var cancelled = false
        val va = ValueAnimator.ofFloat(from, target)
        va.duration = dur
        va.interpolator = ease.interpolator()
        va.addUpdateListener { setProgress(it.animatedValue as Float) }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) {
                if (cancelled) return
                setProgress(target)
                if (target == 0f) detach()
            }
        })
        anim = va
        va.start()
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }

    private fun attach(shape: Shape, pl: Placement): Boolean {
        val density = svc.resources.displayMetrics.density
        val (sw, sh) = screenSize()
        val depth = max(1, (shape.depth * density).roundToInt())
        val length = max(2, (shape.length * density).roundToInt())
        // Window is deeper than the shape so overshoot easings can extend past 100 %.
        val winDepth = (depth * 1.6f).roundToInt() + 2
        vertical = pl.edge == Edge.TOP || pl.edge == Edge.BOTTOM
        val winW = if (vertical) length else winDepth
        val winH = if (vertical) winDepth else length
        val frac = pl.pos / 100f
        val x: Int
        val y: Int
        if (!vertical) {
            x = if (pl.edge == Edge.LEFT) 0 else sw - winDepth
            y = (frac * sh - length / 2f).roundToInt().coerceIn(0, max(0, sh - length))
        } else {
            y = if (pl.edge == Edge.TOP) 0 else sh - winDepth
            x = (frac * sw - length / 2f).roundToInt().coerceIn(0, max(0, sw - length))
        }

        val lp = WindowManager.LayoutParams(
            winW, winH, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.x = x
        lp.y = y
        lp.title = "BezelPop"
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val r = FrameLayout(svc)
        r.clipChildren = false
        r.clipToPadding = false
        val v = BulgeView(svc)
        val vw = if (vertical) length else depth
        val vh = if (vertical) depth else length
        v.setPath(
            ShapeBuilder.buildPx(shape, pl.edge, depth.toFloat(), length.toFloat(), density),
            shape.color
        )
        val g = when (pl.edge) {
            Edge.LEFT -> Gravity.LEFT or Gravity.TOP
            Edge.RIGHT -> Gravity.RIGHT or Gravity.TOP
            Edge.TOP -> Gravity.TOP or Gravity.LEFT
            Edge.BOTTOM -> Gravity.BOTTOM or Gravity.LEFT
        }
        r.addView(v, FrameLayout.LayoutParams(vw, vh, g))
        when (pl.edge) {
            Edge.LEFT -> v.pivotX = 0f
            Edge.RIGHT -> v.pivotX = depth.toFloat()
            Edge.TOP -> v.pivotY = 0f
            Edge.BOTTOM -> v.pivotY = depth.toFloat()
        }
        root = r
        bulge = v
        setProgress(0f)
        return try {
            wm.addView(r, lp)
            true
        } catch (e: Exception) {
            root = null
            bulge = null
            false
        }
    }

    private fun detach() {
        anim?.cancel()
        anim = null
        root?.let { try { wm.removeViewImmediate(it) } catch (e: Exception) { } }
        root = null
        bulge = null
        progress = 0f
    }
}

```

> **Artifact: KeyService.kt** (Accessibility service)

```kotlin
package com.bezelpop

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
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
    private var swallowUp = -1
    private var receiverOn = false
    private var watchOn = false
    private var menuShown = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "config") reload()
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
    }

    private fun reload() {
        cfg = Store.load(this)
        syncInfo()
        syncReceiver()
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
        overlays.values.forEach { it.destroy() } // rotation: drop overlays, next press rebuilds for the new profile
    }

    /** Called from the settings screen so you can preview on the real overlay. */
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
        overlays.values.forEach { it.destroy() }
        overlays.clear()
        try { Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (e: Exception) { }
        if (receiverOn) {
            try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
            receiverOn = false
        }
    }
}

```

> **Artifact: MainActivity.kt** (Settings UI (programmatic views))

```kotlin
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
        val sb = SeekBar(this).apply {
            max = 1000
            progress = (((value - min) / (max - min)) * 1000f).toInt().coerceIn(0, 1000)
        }
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

```

> **Artifact: ChecklistActivity.kt** (Setup checklist screen and status helpers)

```kotlin
package com.bezelpop

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Checks {
    fun serviceEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(c, KeyService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun batteryUnrestricted(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
}

class ChecklistActivity : Activity() {

    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var s3: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val sv = ScrollView(this).apply { fitsSystemWindows = true; addView(box) }
        setContentView(sv)

        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 17f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(20), 0, dp(2))
        }
        fun body(t: String) = TextView(this).apply { text = t; textSize = 14f }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }

        box.addView(TextView(this).apply { text = "Setup checklist"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        box.addView(body("You can skip this and come back any time from the main screen."))

        box.addView(title("1. Allow restricted settings"))
        box.addView(body("Android 13+ blocks accessibility for sideloaded apps. Open App info, tap the three dots (top right) and choose Allow restricted settings. The option only appears after you have tried to enable the service once (step 2)."))
        s1 = body("Cannot be detected automatically.")
        box.addView(s1)
        box.addView(btn("Open App info") { startActivity(appInfo()) })

        box.addView(title("2. Turn on the accessibility service"))
        box.addView(body("Accessibility > Installed apps > BezelPop > On. It only listens for key presses and reads no screen content."))
        s2 = body("")
        box.addView(s2)
        box.addView(btn("Open Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        box.addView(title("3. Battery: Unrestricted"))
        box.addView(body("Samsung can silently kill accessibility services. Set Battery to Unrestricted for BezelPop (App info > Battery), and make sure it is not listed under Settings > Battery > Background usage limits > Sleeping apps."))
        s3 = body("")
        box.addView(s3)
        box.addView(btn("Ask to ignore battery optimisation") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { startActivity(appInfo()) }
        })
        box.addView(btn("Open App info (Battery)") { startActivity(appInfo()) })

        box.addView(btn("Done / Skip for now") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(28) }
        })
    }

    override fun onResume() {
        super.onResume()
        s2.text = if (Checks.serviceEnabled(this)) "Status: enabled" else "Status: not enabled yet"
        s3.text = if (Checks.batteryUnrestricted(this)) "Status: unrestricted" else "Status: not yet unrestricted"
    }
}

```

---

## You `2026-11-11T03:13:15.701564Z`

![1794366792321.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/70db3938-a6c8-4399-8b38-02ffb4ec98fd/preview)



---

## Claude `2026-11-11T03:13:20.981504Z`

The repo has no workflow file, probably because the hidden `.github` folder got skipped when you uploaded. You can add it here:

1. Tap **set up a workflow yourself**.
2. Delete everything in the editor and paste this:

```yaml
name: Build APK
on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.7"
      - name: Build
        run: gradle assembleDebug assembleRelease --no-daemon --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: BezelPop-apks
          path: app/build/outputs/apk/**/*.apk
```

3. Tap **Commit changes**. The build starts on its own.
4. When it finishes (a few minutes), open **Actions**, tap the run, and download **BezelPop-apks** under Artifacts.

Before committing, check that the repo's root has `app/`, `build.gradle.kts`, `settings.gradle.kts` and `gradle.properties`. If any are missing, the upload skipped those too and the build will fail. If it fails, paste the red error lines from the log and I'll fix them.

---

## You `2026-11-11T03:14:19.156302Z`

![1794366853295.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/0e93f85f-957c-4f71-8af6-0e3ace60537c/preview)



---

## Claude `2026-11-11T03:14:31.551899Z`

The upload flattened everything into the repo root, so Gradle can't find the files. You can fix it from your phone by moving each file with the rename trick.

**To move a file:** open it, tap the pencil (Edit), tap the filename box at the top, put the cursor at the very start and type the folder path (each `/` creates a folder), then tap **Commit changes**. For example, `Models.kt` becomes `app/src/main/java/com/bezelpop/Models.kt`.

Move these:

- `AndroidManifest.xml` → `app/src/main/AndroidManifest.xml`
- `BulgeOverlay.kt`, `ChecklistActivity.kt`, `Drawing.kt`, `KeyService.kt`, `MainActivity.kt`, `Models.kt` → `app/src/main/java/com/bezelpop/` in front of the name
- `accessibility_service_…xml` → `app/src/main/res/xml/` in front of the name
- `strings.xml` and `themes.xml` → `app/src/main/res/values/` in front of the name
- `gitignore` → rename to `.gitignore`

**Delete** the root `build.yml`, since the workflow now lives in `.github/workflows`. Open it, tap the ⋯ menu, then Delete file.

**Fix the Gradle files.** The upload had two files named `build.gradle.kts` (one for the root, one for `app/`), and only one survived. Open the one in the root, replace everything with this and commit:

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

Then tap **Add file → Create new file**, name it `app/build.gradle.kts`, and paste this:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bezelpop"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bezelpop"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
```

Leave `README.md`, `gradle.properties` and `settings.gradle.kts` where they are. Each commit starts a build that will fail until everything is in place. After your last move, open **Actions** and check the newest run. If you have a computer handy, unzipping `BezelPop.zip` and uploading the folders from there is quicker.

---

## You `2026-11-11T03:15:22.093251Z`

That seems long. Can I just delete everything and reupload the folder. Ik how to do it without flattening.

---

## Claude `2026-11-11T03:15:26.409086Z`

Yes, that's the quickest fix.

1. **Clear the repo.** Either delete the files one by one, or go to Settings → scroll to the bottom → Delete this repository, then create a new empty one.
2. **Unzip `BezelPop.zip`** and upload the **contents** of the `BezelPop` folder, not the folder itself. `app/`, `.github/`, `build.gradle.kts` and the other root files should sit at the top level of the repo.
3. **Check hidden files.** Make sure `.github` (and `.gitignore`) went up too. Some file pickers hide dot-folders, which is probably how the workflow went missing last time.
4. **Open the Actions tab.** The build starts on its own after the commit, and the APK appears as an artifact when it finishes.

Before committing, check that the repo root shows `app`, `.github`, `build.gradle.kts`, `settings.gradle.kts` and `gradle.properties`. If you're uploading through a phone browser, it flattens folders, so use a computer, or git/GitHub Desktop, to keep the structure.

---

## You `2026-11-11T03:20:53.678759Z`

Nvm can't figure out how to do it without flattening. Can I upload the zip and unzip that in GitHub?

---

## Claude `2026-11-11T03:21:09.610832Z`

GitHub can't unzip files, but a small workflow can do it for you. The steps below should work from your phone.

1. **Upload the zip.** In the repo, tap **Add file → Upload files** and pick `BezelPop.zip` (a single file, so nothing gets flattened). Commit it.
2. **Add the unzip workflow.** Tap **Add file → Create new file**, name it `.github/workflows/unzip.yml`, and paste:

```yaml
name: Unzip project
on: workflow_dispatch
permissions:
  contents: write
jobs:
  unzip:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: |
          git rm -q --ignore-unmatch AndroidManifest.xml BulgeOverlay.kt ChecklistActivity.kt Drawing.kt KeyService.kt MainActivity.kt Models.kt accessibility_service_config.xml strings.xml themes.xml gitignore build.yml
          unzip -o BezelPop.zip
          rm -rf BezelPop/.github
          cp -r BezelPop/. .
          rm -rf BezelPop BezelPop.zip
          git config user.name "bot"
          git config user.email "bot@users.noreply.github.com"
          git add -A
          git commit -m "Unzip project"
          git push
```

3. **Run it.** Go to **Actions → Unzip project → Run workflow**. When it's green, the repo should have `app/`, `build.gradle.kts` and the other root files in the right places. It also deletes the flattened strays from the root.
4. **Build the APK.** The unzip push won't start a build by itself, so go to **Actions → Build APK → Run workflow**. Your existing `.github/workflows/build.yml` stays in place, since the script skips the zip's `.github` folder. When the build finishes, download **BezelPop-apks** from the run's Artifacts.

If a run fails, paste the red error lines and I'll fix them.

---

## You `2026-11-11T03:22:45.728200Z`

![1794367348749.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/73d0c051-58a5-4cb3-b88b-f8a615a9459d/preview)

Stuck on 3. I went to actions but this is what it looks like.

---

## Claude `2026-11-11T03:22:52.413856Z`

An empty "All workflows" page just means nothing has run yet. A manual workflow only gets its Run button once you open it from the list.

1. Tap the **sidebar icon** (the little panel icon left of "All workflows").
2. Tap **Unzip project** in that list.
3. Tap the **Run workflow** dropdown and then the green **Run workflow** button. On a phone browser, you may need to switch to desktop view (browser ⋮ menu → Desktop site) to see it.

If **Unzip project** isn't in the sidebar, GitHub hasn't picked the file up. Check the **Code** tab for `.github/workflows/unzip.yml`. It must be exactly that path, committed to the default branch (main). If the file is there but still isn't listed, the YAML was probably pasted with its indentation broken. Open the file, tap the pencil to edit, and check that the lines are indented as in my message. GitHub will show a red error banner at the top if it's invalid.

If you can't find it, tell me what the Code tab shows inside `.github/workflows`.

---

## You `2026-11-11T03:23:17.744289Z`

Pressed run workflow. What next

---

## Claude `2026-11-11T03:23:20.736687Z`

Wait about 30 seconds, then open the **Actions** tab (pull to refresh if needed).

1. **Check the run.** You should see an "Unzip project" run. A green tick means it worked. Open the **Code** tab to confirm you now have `app/`, `build.gradle.kts` and `settings.gradle.kts` in the repo, with the flattened strays gone.
2. **Start the build.** In Actions, open the sidebar, tap **Build APK**, then **Run workflow**, then the green **Run workflow** button.
3. **Wait for the build.** It takes about 3 to 6 minutes. When it shows a green tick, tap the run and scroll to **Artifacts** at the bottom. Download **BezelPop-apks**, unzip it, and install `app-release.apk`.

If the unzip run has a red X, open it and send me the red error lines. The same goes for the build: if it fails, tap the failed run, then the failed step, and paste the red text.

---

## You `2026-11-11T03:23:58.013332Z`

![1794367429872.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/ccef6809-8cb3-454a-a2c8-d6b586de223b/preview)

Stuck on 2.

---

## Claude `2026-11-11T03:24:05.575986Z`

You're in the right place. Tap **Unzip project** in that list, which opens its page. Then tap the **Run workflow** dropdown near the top of the run list, and then the green **Run workflow** button. If you don't see the dropdown, switch to desktop view (browser ⋮ menu → **Desktop site**).

Give it about 30 seconds, then close the sidebar and check **All workflows** for a green tick on the run.

Only **Unzip project** is listed, so **Build APK** doesn't exist yet. If it's still missing after the unzip succeeds, create it by hand:

1. In the repo, tap **Add file → Create new file**.
2. Name it `.github/workflows/build.yml`.
3. Paste the build workflow from my earlier message, the one starting `name: Build APK`.
4. Commit it. It will then show up in the sidebar and run on its own.

---

## You `2026-11-11T03:29:16.839434Z`

Done!!! Install debug or release?

---

## Claude `2026-11-11T03:29:18.381060Z`

Install **release**. It's the optimised build and is signed with the debug key, so it installs without any extra steps.

Debug is only worth using if something crashes and you want more detailed error output. Install one or the other, not both, since they share the same app ID.

After installing, open BezelPop and follow the setup checklist: allow restricted settings, turn on the accessibility service, then set battery to Unrestricted. Then use **Test** on a button in the settings to check the bulge lines up with your bezel.

---

## You `2026-11-11T03:29:40.625927Z`

Thank you

---

## Claude `2026-11-11T03:29:41.408360Z`

You're welcome! If the bulge doesn't line up with your S25 Edge's bezel, or a button doesn't trigger, tell me what you see and I'll help tune it.

---

## You `2026-11-11T03:42:05.460335Z`

I fear the implementation is bad unfortunately, because of the way I specified corners, my mistake.

It's more like
Flat bezel
Then diagonal line
Then flat edge of bulge
Then diagonal line
Then flat bezel

With corner rounding between the transitions.

Please could you draw a diagram for how you think this might work?

Also, please could you make the app into material 3 expressive UI design, using some open source docs / m3e guidelines or whatever as reference?

Also, could you please make the editor more pleasant to use (buttons to increment values by 0.1 and 1, as well as "apply to all" buttons where sensible, as well as the preview being replaced by just showing the actual overlay consistently while it's being edited, with a bit of added margin on the button's side of the screen so the UI doesn't overlap the overlay, and then the actual UI on the opposite side.

Please also add a dark mode which uses system dark mode to switch on.both ligh mode and dark mode use system colours, with an option to make the preview a chosen colour for easier viewing.

Please also make the white/grey/black options you gave into a colour wheel instead.

Any other ideas?

In landscape instead of the margin idea you should have a slight top or bottom area which content goes under.

---

## Claude `2026-11-11T03:42:51.326876Z`

Yes, I understand. The diagonals are real slopes, not right-angle steps, so each transition is a corner with a turn smaller than 90°. Here's how I'd model it.

The dashed line is the sharp outline you described. The solid shape is the same outline with the four transitions rounded. The turn angle at each corner now comes from the slope, so the corner math has to be rewritten. That also explains why the old version felt wrong.

**Shape controls (per end, with a mirror toggle)**
- Depth and total length.
- The diagonal's angle (or its run along the edge).
- A radius for each of the 4 corners, with the same smoothing options as before.

**Other changes**
- **Material 3 Expressive:** I'd rebuild the settings in Jetpack Compose, which is where M3 Expressive lives, and check the current official docs while coding. The overlay stays plain Views.
- **Theme:** light and dark follow the system, using system colours (Material You dynamic colour on Android 12+). A setting lets you pick the preview colour.
- **Live editor:** the real overlay stays on screen while you edit (it needs the service on). UI content is padded away from the button's edge in portrait. In landscape, content scrolls under a slim top or bottom bar instead.
- **Editing controls:** every value gets −/+ buttons for 0.1 and 1 steps, and you can tap the number to type it. "Apply to all" appears where it makes sense: all corners, both ends, copy to other profiles, and expand easing → contract.
- **Colour:** a proper colour wheel with a brightness slider, replacing the presets.

**More ideas**
- Export and import settings as a file, plus reset-to-default per section.
- Optional haptic tick on expand.
- Per-button colour and size overrides.
- Undo for the last edit.

I'll assume Compose and keep the stretch sliders unless you say otherwise. One thing to decide before I code: should the diagonal be set by **angle** (degrees) or by **run** (how far along the edge it travels)? I'd pick angle, since it stays consistent when you change depth.

---

## You `2026-11-11T03:44:57.737441Z`

Those ideas sound excellent. Also for compose, please keep in mind that I'm on an Android phone with no access to a pc. Is that an issue? 

For your last question: please make it a global toggle, defaulting to angle.

---

## Claude `2026-11-11T03:45:51.787550Z`

Compose isn't a problem for you. GitHub Actions does all the building in the cloud, so you don't need a PC. The only costs are a slightly longer first build and a bigger APK. I'll check the current Material 3 Expressive docs so the versions and API names are right.

#### Sources

1. [Material 3 Expressive](https://pub.dev/documentation/material_3_expressive/1.0.4/) — pub
2. [package summary](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary?authuser=6) — android
3. [Material 3 Expressive](https://pub.dev/documentation/material_3_expressive/1.0.3/) — pub
4. [Material 3 Expressive components for Jetpack Compose and Compose Multiplatform](https://composables.com/material-3-expressive) — composables
5. [Now liveThe Skillselion MCP - thousands of ranked skills, loaded into your agent mid-task. No install.Get it →](https://skillselion.com/skills/ahmed3elshaer/everything-claude-code-mobile/m3-expressive) — skillselion
6. [m3 expressive](https://www.skills.sh/talissonvitorino/kmp-ios-skills/m3-expressive) — skills
7. [m3 expressive](https://skills.sh/ahmed3elshaer/everything-claude-code-mobile/m3-expressive) — skills
8. [github.com](https://github.com/android/compose-samples/pull/1689/files) — github
9. [Recent Release Notes  |  Android Developers](https://developer.android.com/jetpack/androidx/versions/all-channel) — android
10. [compose bom](https://github.com/chrisbanes/compose-bom) — github
11. [Material Android is Compose-first](https://m3.material.io/blog/material-is-compose-first) — material
12. [Compose Material 3  |  Jetpack  |  Android Developers](https://developer.android.com/jetpack/androidx/releases/compose-material3) — android
13. [gist.github.com](https://gist.github.com/BobbyESP/5b07ed192f257111a6c99e67fcfaca34) — github
14. [Wear Compose for Material 3  |  Jetpack  |  Android Developers](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3) — android
15. [Compose Material](https://developer.Android.Com/jetpack/androidx/releases/compose-material) — android
16. [Jetpack Compose](https://en.wikipedia.org/wiki/Jetpack_Compose) — Wikipedia

---

## You `2026-11-11T03:47:44.429440Z`

Sorry to interrupt. I found another issue. Quick click-release of the volume button doesn't show the animation at all (it does, but it goes out and then immediately back in so quickly that you don't notice it).

On iOS it reaches full expansion no matter what. 

could you also make it so that happens?

Please add a user customisable timer for that (that is, the length of the expand and the length of the contract in the case that the button is released before the expand is complete)

---

## Claude `2026-11-11T03:48:24.747380Z`

No problem. I'll fix it now: the bulge will always reach full expansion, and only then contract.

---

## You `2026-11-11T13:18:32.138839Z`

![1001122463.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/e8c57454-e6a9-4e9e-b221-d752bf1c8705/preview)

Sorry, I was incorrect. On iOS the bezel does not always reach full expansion. The original code was correct and you don't need to patch quick-tap behaviour. My issue is actually that the delay seems ever so slightly higher than on iOS (like, small. In milliseconds). could you fix that? No worries if not 

Additionally please add

Import/export settings.
Undo.
Haptic feedback.
Per-button overrides.
Sensible reset options.

And please

Make it look like one of the m3e concept apps, please. Image provided. Especially that gradient stuff and dotted background on the top left image mmmm. Maybe a cool unique title typeface with that gradient in it, as well as a very subtle dotted grid background, and colours being chosen according to your systems dynamic colour (but maybe using the vibrant colour complements of the system's set dynamic colour, to look similar to the fancy gradient at the topleft sometimes) 

System-following light and dark themes.
Dynamic system colours.
More pleasant controls, numerical inputs and increment buttons.
Apply-to-all functionality where appropriate.
Colour wheel and adjustable preview colour.
Live editing as I previously specified 

Please ask any other questions or concerns before continuing

---

## Claude `2026-11-11T13:19:09.557636Z`

Understood. I'll drop the quick-tap change, so please don't use the zip I just made. The original behaviour was right, and I'll fix the small extra delay instead.

**Latency**
- Each press creates the overlay window from scratch, and my code rebuilds a JSON signature of the shape on every press. Both cost a few milliseconds.
- The fix is a "low-latency mode" that keeps the window attached but invisible (the cost is negligible), plus cached shape data.
- I'll also add a latency compensation slider. It starts the animation N ms in, so you can match iOS by eye.
- Some delay is outside my control: Android hands key events to accessibility services after its own processing, so a millisecond or two may remain.

**The rest of your list**
- **Per-button overrides** (each optional, otherwise inherits the global setting): colour, depth/length scale, expand/contract times and easing, haptics, and position.
- **Import/export:** a JSON file through Android's file picker, plus copy and paste as text, so no PC is needed.
- **Undo:** an undo/redo history where one slider drag counts as one step.
- **Haptics:** a click or tick on expand and on contract, with a choice of strength. Samsung's system vibration settings can mute it.
- **Resets:** per section, per profile, per button, or everything, all with confirmation and all undoable.
- **Shape:** the new diagonal model ships in this same release.
- **Live editing:** the preview colour only changes the overlay while you're editing. Your real overlay colour stays as set.

**Concerns**
1. **The "unique typeface".** I can't download font files here. The option is Google Fonts downloaded on the phone, which needs internet and Google Play services on first launch, and it falls back to the system font if that fails.
2. **True M3 Expressive components.** From the release notes I found, most of them are still in 1.5 alpha builds of Material 3. Alphas can break, and you can't debug on a PC.
3. **Build risk.** I can't compile here, so the first Actions build will probably need one or two fix rounds. If so, paste me the red log lines. The app also grows by roughly 8 to 10 MB.
4. **Battery.** The gradient title and dotted grid will be static, with no idle animation.

**Which would you prefer?** Reply with "go" to accept my defaults.

- **Fonts:** Google Fonts download (my default), or the system font with a gradient title.
- **Material:** stable Material 3 with hand-built expressive styling (big rounded shapes, springy motion, wavy cookie shapes, the gradient and dotted grid), or the alpha build with the official expressive components. I'd pick stable, since it's much less likely to break without a PC, and it can still look like the concept apps.
- **Low-latency mode:** on by default, with a toggle.

---

## You `2026-11-11T13:26:37.063914Z`

![1001122467.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/576cc432-8b4c-4be2-9eeb-e295f5dba780/preview)
![1001122465.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/989d3194-6412-494a-be2a-48fae1617f68/preview)
![1001122466.jpg](https://claude.ai/api/0e53bc4c-b20f-4df3-b889-e4f47f733831/files/a564f87d-01f4-4a01-acc8-a6a148a93017/preview)

Please also consider that when there's any overlap on iOS between areas, they sort of blend together this way.

Ensure the invisible overlay doesn't eat touches

Don't forget the small-adjustment buttons (e.g. +0.1%, +1% where appropriate)

You would enter into edit mode for each specific bulge so that that bulge would show constantly whilst editing (with the optional toggle to make it the opposite colour so it stands out whilst editing)

Quick question: which of the stable and alpha builds of M3 will be most expressive?

Everything else is good

Any more issues? Don't code yet

---

## Claude `2026-11-11T13:27:32.288041Z`

**Alpha is the more expressive one.** From the release notes I found, the official expressive parts (expressive theme and motion, wavy sliders, button groups, shape morphing) are mostly in the Material 3 1.5 alpha builds, and stable has little of it. I'll pin one exact alpha version so it can't change under you. If the build rejects it, I'll fall back to stable with hand-built styling.

**What your photos show**
- The real bump is much flatter and longer than my defaults, roughly 1:20 depth to length, with long gentle slopes. I'll make the defaults shallower (about 4 dp deep, 110 dp long, strong smoothing) and you can tune them with live editing.
- **The blending:** I can do this between my own bulges. If two overlap (say volume up and down placed close together, both held), they'll merge into one smooth shape. I can't blend into Samsung's own volume panel because I can't draw on it. If that's what you meant, tell me.

**Your other points**
- **Touches:** overlay windows are already non-touchable and non-focusable, and the always-attached invisible window in low-latency mode keeps that. Accessibility overlays are treated as trusted, so apps shouldn't block taps behind them. Once you've installed it, check that edge swipes and taps near the bezel still work.
- **Small-adjustment buttons:** yes, with steps matched to the unit: ±0.1% / ±1% for positions, ±0.1 / ±1 dp for sizes, ±1 / ±10 ms for times. You can also tap a number to type it.
- **Edit mode:** tapping Edit on a button's card pins that bulge fully out. A toggle inverts its colour so it stands out, and leaving edit mode or the app hides it.

**More issues**
1. **Editing a landscape profile while the phone is upright:** the bulge can't appear at the right edge. By default the editor will follow the phone's rotation, so you rotate to edit Landscape 1 or 2. A "preview anyway" switch will show it in the wrong place.
2. **Fonts:** the title font downloads on first launch, so it needs internet once. It falls back to the system font after that if the download fails.
3. **Per-button overrides:** these cover colour, size scale, timings and easing, and haptics. Position is already per button and per profile.
4. **Latency:** besides low-latency mode and the compensation slider, I'll default to a punchier ease-out so motion is visible on the very first frame. A little delay inside Android itself will remain.
5. **The quick-tap zip:** ignore it. I'll rebuild from the original behaviour, so that change won't be in.

I have no further questions. Say "go" and I'll start coding.

---

## You `2026-11-11T14:27:35.868638Z`

1. You should only be able to edit a profile that youre currently in . E.g. you can only edit landscape 90° when you're in landscape 90°, same with the others. Look at first image, mt capsule app does similar (though it sadly doesn't differentiate between both landscapes). Please also add a portrait 180° option as I do use that on my phone sometimes haha (upside down portrait)

2. Sure

4. Default to decelerate maybe

To

---

## Claude `2026-11-11T14:27:50.689155Z`

Noted. Here's what I'll do with your three answers.

1. **Profiles:** there will be four, named by rotation: Portrait 0°, Landscape 90°, Portrait 180° (upside down) and Landscape 270°. The editor only edits the profile matching your phone's current orientation. The others show as locked with a "rotate to edit" note, and Android's rotation lock still decides which orientations you can reach. The settings screen will also be allowed to rotate into upside-down portrait, so you can edit that profile. Portrait 180° gets sensible defaults too, with the button edge flipped to the left side. There's no "preview anyway" switch any more.
2. **Fonts:** the title font downloads from Google Fonts on first launch, with a fallback if it fails.
3. **Easing:** expand defaults to a decelerate curve, so motion is visible on the very first frame. Contract stays ease-in-out unless you want it changed, and both remain editable.

Your message ended with "To", so I think part of it got cut off. If there was more, send it now. Otherwise say "go" and I'll start coding.

---

## You `2026-11-11T19:54:26.727611Z`

Perfect, and don't forget the latency compensation. Thank you

additionally keep the smooth corner things's stretch (width and height wise) options, and make the width and height of both diagonals customizable as well. and allow the user to instantly copy their portrait settings to landscape (I mean corner settings etc, not position, as in landscape the user will have to do the positioning themself).

Also can the app intercept the volume down+power for screenshot combination, in order to animate the buttons as well as also triggering a system screenshot?

---

## Claude `2026-11-11T20:05:11.109274Z`

Short answer on your screenshot question: not reliably. Power is almost never passed to accessibility services, so I can't see the volume-down + power combo. If I stopped Volume Down from reaching the system, I'd break the screenshot shortcut. What I can do is animate Volume Down as usual and let Samsung take the screenshot itself. Alternatively, I can add an optional chord, such as Volume Up + Down, that animates both buttons and takes the screenshot through the accessibility service. Say if you want that.

I'm now writing the rebuild: the new diagonal shape with stretch, per-diagonal width and height, copy to all profiles, and latency compensation. I'm using stable Material 3 for now, since a broken alpha build would be hard to debug without a PC. I'm also using a built-in condensed italic font for the title, because the Google Fonts setup needs certificate data I can't safely write by hand.

Still going: models, overlay, service, theme and components are written. I'm now writing the home and editor screens, the setup checklist, and the activity.

BezelPop 2 is built and zipped as BezelPop.zip, but I couldn't compile it here. The first GitHub build may hit a Compose error. If so, paste the red lines and I'll fix them.

**To update your repo (no flattening):**
1. In the repo, delete the old `BezelPop.zip` if it's still there, then upload the new one with Add file → Upload files.
2. Run Actions → Unzip project.
3. Run Actions → Build APK, then install `app-release.apk` from the artifact. It will install over the old version, but your old settings won't carry over.

**What's in it**
- **Shape:** it's flat bezel, then a diagonal, then a flat edge, then a diagonal, then the bezel, with four rounded and smoothed corners. Each diagonal has its own width and height, and there are link toggles for both. A global switch picks angle or length for the diagonals, and defaults to angle. The corner stretch options are still there, and "All" copies a value to every corner.
- **Copying shapes:** "Copy shape from…" and "Copy to all others" move corners, diagonals and colour between profiles. Positions are never copied.
- **Profiles:** there are four, Portrait 0°, Landscape 90°, Portrait 180° and Landscape 270°. You can only edit the one matching how the phone is held, and the app can now rotate upside-down.
- **Latency:** there's a compensation slider (default 12 ms) and a low-latency mode that keeps invisible, touch-transparent windows ready. Expand defaults to decelerate.
- **Live editing:** tapping Edit on a button pins its real bulge on screen while you edit. In portrait the content leaves a margin on the bulge's side. In landscape a slim fade strip sits at the top or bottom edge, and content scrolls under it. A toggle inverts the bulge colour while editing, or you can pick a preview colour. The bulge needs the service on.
- **Controls:** every value has a slider, tap-to-type, and −/+ buttons (0.1 and 1, or 1 and 10 where it fits). There's a colour wheel, plus undo and redo.
- **Look:** a gradient condensed-italic title, a dotted grid, and gradient colours rotated from your system accent. It follows the system light and dark theme.
- **Per-button settings:** each button can override colour, size scale, expand/contract time and easing, and haptics.
- **Haptics and backup:** haptics are per expand and contract. Settings export and import as a file or as copied text. Resets are undoable.

**Differences from what I said**
- I used stable Material 3 with hand-built styling, not the alpha components, since a broken alpha build would be hard to debug from your phone.
- The title font is the system's condensed italic, because the Google Fonts setup needs certificate data I can't safely write by hand.
- Overlapping bulges just sit on top of each other. Smooth merging isn't built.
- Corner radius now needs big values (the default is 150 dp) because the slopes are shallow.
- Defaults are rough guesses from your photos, so tune them with live editing.

> **Artifact: Models.kt** (Data models, JSON persistence, effective-config helpers)

```kotlin
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
    BEZIER("Custom cubic-bezier", false)
}

enum class PowerKeyMode(val label: String) {
    PULSE("Pulse on press"),
    HOLD("Follow hold (only if phone delivers key)"),
    OFF("Ignore power key")
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
    var hapticContract: HapticLevel = HapticLevel.OFF
) {
    fun copy(): Anim = from(toJson())

    fun toJson(): JSONObject = JSONObject()
        .put("exp", expandMs.toDouble()).put("con", contractMs.toDouble())
        .put("expE", expandEase.toJson()).put("conE", contractEase.toJson())
        .put("pulse", pulseHoldMs.toDouble()).put("maxHold", maxHoldSec.toDouble())
        .put("lat", latencyMs.toDouble()).put("lowLat", lowLatency)
        .put("hExp", hapticExpand.name).put("hCon", hapticContract.name)

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
                enumOf(HapticLevel.values(), o.optString("hCon"), HapticLevel.OFF)
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
    var angleMode: Boolean = true
)

class Config(
    val triggers: MutableList<Trigger>,
    val profiles: MutableMap<ProfileId, Profile>,
    var anim: Anim = Anim(),
    var powerMode: PowerKeyMode = PowerKeyMode.PULSE,
    var pulseOnScreen: Boolean = false,
    var menuWatch: Boolean = false,
    var ui: UiPrefs = UiPrefs()
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
            .put("ui", JSONObject().put("invert", ui.editInvert).put("editColor", ui.editColor).put("angle", ui.angleMode))
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
                u.optBoolean("invert", true), u.optInt("editColor", 0xFFFF4081.toInt()), u.optBoolean("angle", true)
            )
            return Config(
                triggers, profiles, Anim.from(o.optJSONObject("anim")),
                enumOf(PowerKeyMode.values(), o.optString("powerMode"), PowerKeyMode.PULSE),
                o.optBoolean("pulseScreen", false), o.optBoolean("menuWatch", false), ui
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

```

> **Artifact: Drawing.kt** (Shape geometry (diagonals + smoothed corners), view, easing)

```kotlin
package com.bezelpop

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

fun Ease.interpolator(): TimeInterpolator = when (type) {
    EaseType.LINEAR -> LinearInterpolator()
    EaseType.EASE_OUT -> DecelerateInterpolator(strength)
    EaseType.EASE_IN -> AccelerateInterpolator(strength)
    EaseType.EASE_IN_OUT -> AccelerateDecelerateInterpolator()
    EaseType.OVERSHOOT -> OvershootInterpolator(strength)
    EaseType.ANTICIPATE -> AnticipateInterpolator(strength)
    EaseType.BOUNCE -> BounceInterpolator()
    EaseType.BEZIER -> PathInterpolator(bz[0].coerceIn(0f, 1f), bz[1], bz[2].coerceIn(0f, 1f), bz[3])
}

class Built(val path: Path, val depth: Float, val length: Float)

object ShapeBuilder {

    /**
     * Outline: bezel -> corner A -> diagonal -> corner B -> flat edge -> corner C -> diagonal -> corner D -> bezel.
     * Every corner is one cubic Bezier. smoothing 0 = circular arc, 1 = very gradual (curvature-continuous feel).
     * Stretch w / h scale the corner in the depth / along-edge direction.
     * Returned path is in view pixels for the requested [edge]; bezel sits on that edge.
     */
    fun build(s: Shape, edge: Edge, density: Float): Built {
        val len = max(4f, s.length * density)
        val wT = max(0.5f, s.wTop * density)
        val wB = max(0.5f, s.wBot * density)
        var hT = max(0.5f, s.hTop * density)
        var hB = max(0.5f, s.hBot * density)
        if (hT + hB > len) {
            val k = len / (hT + hB)
            hT *= k; hB *= k
        }
        val sm = if (s.smooth) s.smoothing.coerceIn(0f, 1f) else 0f

        val vx = floatArrayOf(0f, wT, wB, 0f)
        val vy = floatArrayOf(0f, hT, len - hB, len)

        // leg directions: 0 = A->B, 1 = B->C, 2 = C->D
        val lx = FloatArray(3)
        val ly = FloatArray(3)
        val ll = FloatArray(3)
        for (i in 0..2) {
            val dx = vx[i + 1] - vx[i]
            val dy = vy[i + 1] - vy[i]
            val l = hypot(dx, dy)
            ll[i] = l
            if (l < 1e-3f) { lx[i] = 0f; ly[i] = 1f } else { lx[i] = dx / l; ly[i] = dy / l }
        }
        val inx = FloatArray(4)
        val iny = FloatArray(4)
        val outx = FloatArray(4)
        val outy = FloatArray(4)
        for (i in 0..3) {
            if (i == 0) { inx[i] = 0f; iny[i] = 1f } else { inx[i] = lx[i - 1]; iny[i] = ly[i - 1] }
            if (i == 3) { outx[i] = 0f; outy[i] = 1f } else { outx[i] = lx[i]; outy[i] = ly[i] }
        }
        val cs = arrayOf(s.cA, s.cB, s.cornerC, s.cornerD)

        // budget: corner tangents on one leg must not exceed that leg's length
        val extIn = FloatArray(4)
        val extOut = FloatArray(4)
        for (i in 0..3) {
            val t = tangent(inx[i], iny[i], outx[i], outy[i], cs[i].radius * density, sm)
            extIn[i] = hypot(inx[i] * t * cs[i].w, iny[i] * t * cs[i].h)
            extOut[i] = hypot(outx[i] * t * cs[i].w, outy[i] * t * cs[i].h)
        }
        var k = 1f
        for (i in 0..2) {
            val sum = extOut[i] + extIn[i + 1]
            if (sum > ll[i] && sum > 0f) k = min(k, ll[i] / sum)
        }

        val path = Path()
        for (i in 0..3) {
            corner(
                path, vx[i], vy[i], inx[i], iny[i], outx[i], outy[i],
                cs[i].radius * density * k, sm, cs[i].w, cs[i].h, i == 0
            )
        }
        path.close()

        val b = RectF()
        path.computeBounds(b, true)
        path.offset(0f, -b.top)
        val depth = max(1f, b.right)
        val length = max(2f, b.height())

        val m = Matrix()
        when (edge) {
            Edge.LEFT -> return Built(path, depth, length)
            Edge.RIGHT -> m.setValues(floatArrayOf(-1f, 0f, depth, 0f, 1f, 0f, 0f, 0f, 1f))
            Edge.TOP -> m.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            Edge.BOTTOM -> m.setValues(floatArrayOf(0f, 1f, 0f, -1f, 0f, depth, 0f, 0f, 1f))
        }
        path.transform(m)
        return Built(path, depth, length)
    }

    private fun tangent(ux: Float, uy: Float, ox: Float, oy: Float, r: Float, sm: Float): Float {
        val phi = acos((ux * ox + uy * oy).coerceIn(-1f, 1f))
        if (phi < 0.01f || r < 0.01f) return 0f
        val pc = min(phi, 2.9f)
        return r * tan(pc / 2f) * (1f + sm)
    }

    private fun corner(
        p: Path, vx: Float, vy: Float, ux: Float, uy: Float, ox: Float, oy: Float,
        r: Float, sm: Float, sw: Float, sh: Float, first: Boolean
    ) {
        val phi = acos((ux * ox + uy * oy).coerceIn(-1f, 1f))
        if (r < 0.01f || phi < 0.01f) {
            if (first) p.moveTo(vx, vy) else p.lineTo(vx, vy)
            return
        }
        val pc = min(phi, 2.9f)
        val t = r * tan(pc / 2f) * (1f + sm)
        val g0 = (4f / 3f * tan(pc / 4f)) / tan(pc / 2f)
        val g = g0 + (1f - g0) * sm
        val sx = vx - ux * t * sw
        val sy = vy - uy * t * sh
        val ex = vx + ox * t * sw
        val ey = vy + oy * t * sh
        val c1x = vx - ux * t * (1f - g) * sw
        val c1y = vy - uy * t * (1f - g) * sh
        val c2x = vx + ox * t * (1f - g) * sw
        val c2y = vy + oy * t * (1f - g) * sh
        if (first) p.moveTo(sx, sy) else p.lineTo(sx, sy)
        p.cubicTo(c1x, c1y, c2x, c2y, ex, ey)
    }
}

class BulgeView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var path: Path? = null

    fun setPath(p: Path, color: Int) {
        path = p
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        path?.let { canvas.drawPath(it, paint) }
    }
}

```

> **Artifact: KeyService.kt** (Accessibility service with prewarm, pin/replay for editor)

```kotlin
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

    private fun reload() {
        cfg = Store.load(this)
        syncInfo()
        syncReceiver()
        syncPrewarm()
        pinnedId?.let { overlay(it).pin(cfg, pinnedProfile, pinnedColor) }
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

```

> **Artifact: Ui.kt** (UI state (undo/redo), theme, background, reusable components)

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bezelpop

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// ======================= state + undo/redo =======================

class UiState(private val app: Context) {
    var cfg by mutableStateOf(Store.load(app))
    var tick by mutableIntStateOf(0)
    var profile by mutableStateOf(currentProfileId(app))
    var editing by mutableStateOf<String?>(null)
    var showChecklist by mutableStateOf(false)
    var resumeTick by mutableIntStateOf(0)
    var learningId by mutableStateOf<String?>(Store.prefs(app).getString("learn_id", null))
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private val undoS = ArrayList<String>()
    private val redoS = ArrayList<String>()
    private var burstBase: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val flush = Runnable { commitBurst() }

    /** Every change goes through here. A burst of changes (e.g. one slider drag) is one undo step. */
    fun edit(block: () -> Unit) {
        if (burstBase == null) burstBase = cfg.toJson().toString()
        handler.removeCallbacks(flush)
        block()
        Store.save(app, cfg)
        tick++
        canUndo = true
        handler.postDelayed(flush, 700)
    }

    private fun commitBurst() {
        burstBase?.let {
            undoS.add(it)
            if (undoS.size > 60) undoS.removeAt(0)
            redoS.clear()
        }
        burstBase = null
        syncFlags()
    }

    private fun syncFlags() {
        canUndo = undoS.isNotEmpty() || burstBase != null
        canRedo = redoS.isNotEmpty()
    }

    fun undo() {
        handler.removeCallbacks(flush)
        commitBurst()
        if (undoS.isEmpty()) return
        redoS.add(cfg.toJson().toString())
        applyJson(undoS.removeAt(undoS.size - 1))
    }

    fun redo() {
        handler.removeCallbacks(flush)
        commitBurst()
        if (redoS.isEmpty()) return
        undoS.add(cfg.toJson().toString())
        applyJson(redoS.removeAt(redoS.size - 1))
    }

    private fun applyJson(json: String) {
        Config.parse(json)?.let {
            cfg = it
            Store.save(app, it)
            tick++
        }
        syncFlags()
    }

    fun reloadFromStore() {
        cfg = Store.load(app)
        learningId = Store.prefs(app).getString("learn_id", null)
        tick++
    }
}

// ======================= theme =======================

@Composable
fun BezelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}

/** A vivid version of the system accent, rotated around the colour wheel (complements / triads). */
fun vivid(base: Color, shift: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = (hsv[0] + shift) % 360f
    hsv[1] = max(hsv[1], 0.62f)
    hsv[2] = max(hsv[2], 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun accentBrush(alpha: Float = 1f): Brush {
    val p = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(
        listOf(vivid(p, 0f).copy(alpha = alpha), vivid(p, 110f).copy(alpha = alpha), vivid(p, 200f).copy(alpha = alpha))
    )
}

fun Modifier.dotGrid(color: Color, spacing: Dp = 18.dp): Modifier = drawWithCache {
    val s = spacing.toPx()
    val pts = ArrayList<Offset>()
    var y = s / 2f
    while (y < size.height) {
        var x = s / 2f
        while (x < size.width) {
            pts.add(Offset(x, y))
            x += s
        }
        y += s
    }
    onDrawBehind {
        drawPoints(pts, PointMode.Points, color, strokeWidth = 2.6f, cap = StrokeCap.Round)
    }
}

@Composable
fun GlowBackground(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val g1 = vivid(cs.primary, 0f)
    val g2 = vivid(cs.primary, 110f)
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(g1.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(0f, 0f), radius = size.maxDimension * 0.75f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(g2.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.4f), radius = size.maxDimension * 0.55f
                    )
                )
            }
            .dotGrid(cs.onBackground.copy(alpha = 0.10f))
    ) { content() }
}

@Composable
fun GradientText(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    val brush = accentBrush()
    val ff = remember {
        FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD_ITALIC))
    }
    Text(
        text, fontFamily = ff, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            }
    )
}

// ======================= components =======================

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = cs.surfaceContainer.copy(alpha = 0.94f),
        border = BorderStroke(1.2.dp, accentBrush(0.55f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = cs.primary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> Picker(label: String, items: List<T>, selected: T, name: (T) -> String, onSel: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(name(selected)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(name(item)) }, onClick = { open = false; onSel(item) })
                }
            }
        }
    }
}

@Composable
fun <T> ChipRow(
    items: List<T>, selected: T, name: (T) -> String,
    enabledFor: (T) -> Boolean = { true }, onSel: (T) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected, onClick = { onSel(item) },
                enabled = enabledFor(item), label = { Text(name(item)) }
            )
        }
    }
}

@Composable
fun NumberDialog(title: String, value: Float, onDismiss: () -> Unit, onOk: (Float) -> Unit) {
    var text by remember { mutableStateOf(String.format("%.3f", value).trimEnd('0').trimEnd('.', ',')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.replace(',', '.').toFloatOrNull()
                if (v != null) onOk(v) else onDismiss()
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun trimNum(x: Float): String = if (x == x.toInt().toFloat()) x.toInt().toString() else x.toString()

@Composable
private fun StepBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

/** Slider + −/+ fine and coarse buttons + tap-to-type number. Optional "All" button. */
@Composable
fun Stepper(
    label: String, value: Float, min: Float, max: Float, unit: String = "",
    fine: Float = 0.1f, coarse: Float = 1f, decimals: Int = 1,
    onAll: (() -> Unit)? = null, onChange: (Float) -> Unit
) {
    var typing by remember { mutableStateOf(false) }
    fun fmt(v: Float) = String.format("%.${decimals}f", v) + unit
    fun set(v: Float) {
        val c = v.coerceIn(min, max)
        onChange(Math.round(c * 1000f) / 1000f)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                fmt(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { typing = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Slider(value = value.coerceIn(min, max), onValueChange = { set(it) }, valueRange = min..max)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepBtn("−" + trimNum(coarse), Modifier.weight(1f)) { set(value - coarse) }
            StepBtn("−" + trimNum(fine), Modifier.weight(1f)) { set(value - fine) }
            StepBtn("+" + trimNum(fine), Modifier.weight(1f)) { set(value + fine) }
            StepBtn("+" + trimNum(coarse), Modifier.weight(1f)) { set(value + coarse) }
            if (onAll != null) StepBtn("All", Modifier.weight(1f)) { onAll() }
        }
    }
    if (typing) NumberDialog(label, value, { typing = false }) { set(it); typing = false }
}

@Composable
fun EaseEditor(title: String, e: Ease, onEdit: (Ease.() -> Unit) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Picker("Curve", EaseType.values().toList(), e.type, { it.label }) { picked -> onEdit { type = picked } }
    if (e.type.hasStrength) {
        Stepper("Strength", e.strength, 0.5f, 5f, "", 0.05f, 0.5f, 2) { v -> onEdit { strength = v } }
    }
    if (e.type == EaseType.BEZIER) {
        Stepper("x1", e.bz[0], 0f, 1f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[0] = v } }
        Stepper("y1", e.bz[1], -1f, 2f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[1] = v } }
        Stepper("x2", e.bz[2], 0f, 1f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[2] = v } }
        Stepper("y2", e.bz[3], -1f, 2f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[3] = v } }
    }
}

// ---------- colour wheel ----------

private val hueColors = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }

@Composable
fun ColorWheel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    fun pick(p: Offset, w: Float) {
        val c = w / 2f
        val dx = p.x - c
        val dy = p.y - c
        val s = (hypot(dx, dy) / c).coerceIn(0f, 1f)
        var h = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (h < 0f) h += 360f
        cb(h, s)
    }
    Canvas(
        Modifier
            .size(240.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pick(it, size.width.toFloat()) },
                    onDrag = { change, _ -> pick(change.position, size.width.toFloat()) }
                )
            }
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Brush.sweepGradient(hueColors, c), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = r), radius = r, center = c)
        drawCircle(Color.Black.copy(alpha = 1f - value), radius = r, center = c)
        val a = Math.toRadians(hue.toDouble())
        val p = Offset(c.x + (cos(a) * sat * r).toFloat(), c.y + (sin(a) * sat * r).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.5f), 13.dp.toPx(), p, style = Stroke(5.dp.toPx()))
        drawCircle(Color.White, 12.dp.toPx(), p, style = Stroke(3.dp.toPx()))
    }
}

@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    val result = Color.hsv(hue, sat, value, alpha)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorWheel(hue, sat, value) { h, s -> hue = h; sat = s }
                Text("Brightness")
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                Text("Opacity")
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.05f..1f)
                Box(
                    Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp)).background(result)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                Text(String.format("#%08X", result.toArgb()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(result.toArgb()) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalButton(onClick = { open = true }) { Text("Wheel…") }
    }
    if (open) ColorDialog(label, color, { open = false }) { open = false; onPick(it) }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}

```

> **Artifact: Screens.kt** (Home, editor and checklist screens)

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.bezelpop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.tan

object Checks {
    fun serviceEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(c, KeyService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun batteryUnrestricted(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
}

private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

private fun contrastOf(c: Int): Int {
    val l = (0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) +
        0.114f * android.graphics.Color.blue(c)) / 255f
    return if (l > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

private fun angleOf(w: Float, h: Float): Float = Math.toDegrees(atan2(w.toDouble(), h.toDouble())).toFloat()
private fun runFromAngle(w: Float, deg: Float): Float = (w / tan(Math.toRadians(deg.toDouble()))).toFloat()

// ======================= top bar =======================

@Composable
private fun TopBar(ui: UiState, title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") }
        if (onBack == null) GradientText("BezelPop", 44.sp, Modifier.weight(1f))
        else Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = { ui.undo() }, enabled = ui.canUndo) { Text("Undo") }
        TextButton(onClick = { ui.redo() }, enabled = ui.canRedo) { Text("Redo") }
    }
}

// ======================= home =======================

@Composable
fun HomeScreen(ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    @Suppress("UNUSED_VARIABLE") val rt = ui.resumeTick
    val cfg = ui.cfg
    val ctx = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    val clipboard = LocalClipboardManager.current
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(ui.cfg.toJson().toString(2).toByteArray()) }
                toast(ctx, "Exported")
            } catch (e: Exception) { toast(ctx, "Export failed") }
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val parsed = Config.parse(text)
                if (parsed == null) toast(ctx, "That file isn't a BezelPop export")
                else { ui.edit { ui.cfg = parsed }; toast(ctx, "Imported (undo available)") }
            } catch (e: Exception) { toast(ctx, "Import failed") }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(
                start = bars.calculateStartPadding(ld) + 16.dp, end = bars.calculateEndPadding(ld) + 16.dp,
                top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 40.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopBar(ui, "BezelPop", null)

        // ---- status ----
        Section("Status") {
            val on = Checks.serviceEnabled(ctx)
            Text(if (on) "Service is ON" else "Service is OFF — turn it on in Accessibility settings")
            Text("Phone orientation right now: ${ui.profile.label}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                FilledTonalButton(onClick = { ui.showChecklist = true }) { Text("Setup checklist") }
            }
        }

        // ---- profiles ----
        Section("Profiles") {
            ChipRow(ProfileId.values().toList(), ui.profile, { it.label }, enabledFor = { it == ui.profile }) { }
            Hint("You can only edit the profile that matches how your phone is held right now. Rotate the phone to edit another one.")
            FilledTonalButton(onClick = {
                confirm = "Copy this profile's shape (corners, diagonals, colour) to all other profiles? Positions are not touched." to {
                    ui.edit {
                        val src = cfg.profiles.getValue(ui.profile).shape
                        ProfileId.values().filter { it != ui.profile }.forEach { cfg.profiles.getValue(it).shape = src.copy() }
                    }
                }
            }) { Text("Copy ${ui.profile.label} shape to all others") }
        }

        // ---- buttons ----
        for (t in cfg.triggers.toList()) {
            Section(t.name) {
                SwitchRow("Enabled", t.enabled) { v -> ui.edit { t.enabled = v } }
                val kc = if (t.keyCode == 0) "not set yet" else "${KeyEvent.keyCodeToString(t.keyCode)} (${t.keyCode})"
                Text("Key: $kc", style = MaterialTheme.typography.bodySmall)
                val learning = ui.learningId == t.id
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Edit") { ui.editing = t.id }
                    FilledTonalButton(onClick = {
                        if (learning) {
                            Store.prefs(ctx).edit().remove("learn_id").apply(); ui.learningId = null
                        } else {
                            if (KeyService.instance == null) toast(ctx, "Turn on the accessibility service first")
                            Store.prefs(ctx).edit().putString("learn_id", t.id).apply(); ui.learningId = t.id
                            toast(ctx, "Press the physical button now")
                        }
                    }) { Text(if (learning) "Cancel" else "Learn key") }
                    FilledTonalButton(onClick = {
                        val s = KeyService.instance
                        if (s == null) toast(ctx, "Turn on the accessibility service first") else s.demo(t.id, ui.profile)
                    }) { Text("Test") }
                    if (!t.builtin) FilledTonalButton(onClick = { ui.edit { cfg.triggers.remove(t) } }) { Text("Remove") }
                }
                if (t.power) {
                    Hint("Android rarely passes the power key to accessibility services, so hold detection may be impossible. Fallbacks:")
                    Picker("Power key mode", PowerKeyMode.values().toList(), cfg.powerMode, { it.label }) { m -> ui.edit { cfg.powerMode = m } }
                    SwitchRow("Also pulse when screen turns on/off", cfg.pulseOnScreen) { v -> ui.edit { cfg.pulseOnScreen = v } }
                    SwitchRow("Experimental: show while power menu is open", cfg.menuWatch) { v -> ui.edit { cfg.menuWatch = v } }
                }
            }
        }
        Section("Add a button") {
            Hint("Custom buttons (Bixby, Essential key, gaming triggers…) only work if the phone sends a normal key event.")
            PrimaryButton("Add custom button…") { addDialog = true }
        }

        // ---- animation ----
        val a = cfg.anim
        Section("Animation") {
            Stepper("Expand time", a.expandMs, 30f, 1500f, " ms", 1f, 10f, 0, onAll = { ui.edit { a.contractMs = a.expandMs } }) { v -> ui.edit { a.expandMs = v } }
            Stepper("Contract time", a.contractMs, 30f, 1500f, " ms", 1f, 10f, 0) { v -> ui.edit { a.contractMs = v } }
            Stepper("Latency compensation", a.latencyMs, 0f, 80f, " ms", 1f, 5f, 0) { v -> ui.edit { a.latencyMs = v } }
            Hint("Starts the expand animation this far in, to hide the small delay Android adds before apps see a key press.")
            SwitchRow("Low-latency mode (keep invisible windows ready)", a.lowLatency) { v -> ui.edit { a.lowLatency = v } }
            Stepper("Pulse hold time", a.pulseHoldMs, 0f, 2000f, " ms", 1f, 10f, 0) { v -> ui.edit { a.pulseHoldMs = v } }
            Stepper("Safety timeout", a.maxHoldSec, 2f, 60f, " s", 1f, 5f, 0) { v -> ui.edit { a.maxHoldSec = v } }
            EaseEditor("Expand easing", a.expandEase) { f -> ui.edit { a.expandEase.f() } }
            FilledTonalButton(onClick = { ui.edit { a.contractEase = a.expandEase.copy() } }) { Text("Use expand easing for contract") }
            EaseEditor("Contract easing", a.contractEase) { f -> ui.edit { a.contractEase.f() } }
        }

        Section("Haptics") {
            Picker("On expand", HapticLevel.values().toList(), a.hapticExpand, { it.label }) { h -> ui.edit { a.hapticExpand = h } }
            Picker("On contract", HapticLevel.values().toList(), a.hapticContract, { it.label }) { h -> ui.edit { a.hapticContract = h } }
            FilledTonalButton(onClick = { ui.edit { a.hapticContract = a.hapticExpand } }) { Text("Use expand haptic for contract") }
            Hint("Per-button haptics can be set in each button's overrides. System vibration settings can mute these.")
        }

        Section("Editing appearance") {
            SwitchRow("Invert bulge colour while editing", cfg.ui.editInvert) { v -> ui.edit { cfg.ui.editInvert = v } }
            if (!cfg.ui.editInvert) ColorRow("Editing colour", cfg.ui.editColor) { c -> ui.edit { cfg.ui.editColor = c } }
            Hint("This only changes the live overlay while you are in an editor. Your real bulge colour is untouched.")
        }

        Section("Backup & reset") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { exportL.launch("bezelpop-settings.json") }) { Text("Export file") }
                FilledTonalButton(onClick = { importL.launch(arrayOf("*/*")) }) { Text("Import file") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString(cfg.toJson().toString(2))); toast(ctx, "Copied settings text")
                }) { Text("Copy as text") }
                FilledTonalButton(onClick = {
                    val parsed = clipboard.getText()?.text?.let { Config.parse(it) }
                    if (parsed == null) toast(ctx, "Clipboard has no BezelPop settings")
                    else { ui.edit { ui.cfg = parsed }; toast(ctx, "Pasted (undo available)") }
                }) { Text("Paste text") }
            }
            Hint("Resets are undoable.")
            OutlinedButton(onClick = {
                confirm = "Reset ${ui.profile.label} shape to default?" to { ui.edit { cfg.profiles.getValue(ui.profile).shape = Shape() } }
            }) { Text("Reset this profile's shape") }
            OutlinedButton(onClick = {
                confirm = "Reset button positions for ${ui.profile.label}?" to { ui.edit { cfg.profiles.getValue(ui.profile).placements.clear() } }
            }) { Text("Reset positions (this profile)") }
            OutlinedButton(onClick = {
                confirm = "Reset all animation and haptic settings?" to { ui.edit { cfg.anim = Anim() } }
            }) { Text("Reset animation settings") }
            OutlinedButton(onClick = {
                confirm = "Remove all per-button overrides?" to { ui.edit { cfg.triggers.forEach { it.ov = Overrides() } } }
            }) { Text("Reset all button overrides") }
            OutlinedButton(onClick = {
                confirm = "Reset EVERYTHING to defaults? (You can still undo.)" to { ui.edit { ui.cfg = Config.default() } }
            }) { Text("Reset everything") }
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    if (addDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("New button") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    addDialog = false
                    val t = Trigger("custom_" + System.currentTimeMillis(), name.trim().ifEmpty { "Custom button" }, 0)
                    ui.edit { cfg.triggers.add(t) }
                    Store.prefs(ctx).edit().putString("learn_id", t.id).apply()
                    ui.learningId = t.id
                    toast(ctx, "Press the physical button now (service must be on)")
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } }
        )
    }
}

// ======================= editor =======================

@Composable
fun EditorScreen(ui: UiState, id: String) {
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val cfg = ui.cfg
    val t = cfg.trigger(id)
    if (t == null) {
        LaunchedEffect(Unit) { ui.editing = null }
        return
    }
    val ctx = LocalContext.current
    val prof = cfg.profiles.getValue(ui.profile)
    val s = prof.shape
    val pl = cfg.placement(ui.profile, id)
    val effColor = t.ov.color ?: s.color
    val previewColor: Int = if (cfg.ui.editInvert) contrastOf(effColor) else cfg.ui.editColor

    LaunchedEffect(id, ui.profile, previewColor, ui.resumeTick) {
        KeyService.instance?.pin(id, ui.profile, previewColor)
    }
    DisposableEffect(Unit) { onDispose { KeyService.instance?.unpin() } }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    val depthDp = maxOf(s.wTop, s.wBot) * t.ov.scale
    val margin = (depthDp + 28f).dp
    val strip = (depthDp + 22f).dp
    val extraStart = if (pl.edge == Edge.LEFT) margin else 0.dp
    val extraEnd = if (pl.edge == Edge.RIGHT) margin else 0.dp
    val extraTop = if (pl.edge == Edge.TOP) strip else 0.dp
    val extraBottom = if (pl.edge == Edge.BOTTOM) strip else 0.dp
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    start = bars.calculateStartPadding(ld) + 16.dp + extraStart,
                    end = bars.calculateEndPadding(ld) + 16.dp + extraEnd,
                    top = bars.calculateTopPadding() + 8.dp + extraTop,
                    bottom = bars.calculateBottomPadding() + 40.dp + extraBottom
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopBar(ui, t.name, { ui.editing = null })

            if (!Checks.serviceEnabled(ctx)) {
                Section("Live overlay is off") {
                    Hint("Turn on the accessibility service to see the real bulge while you edit.")
                    FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility settings") }
                }
            }

            Section("Position · ${ui.profile.label}") {
                Text("Edge", style = MaterialTheme.typography.bodyMedium)
                ChipRow(Edge.values().toList(), pl.edge, { it.label }) { e -> ui.edit { pl.edge = e } }
                Stepper("Position along edge", pl.pos, 0f, 100f, "%", 0.1f, 1f, 1) { v -> ui.edit { pl.pos = v } }
                Hint("Landscape positions are set separately in each landscape orientation.")
            }

            Section("Shape · ${ui.profile.label}") {
                Stepper("Length (bezel contact)", s.length, 20f, 400f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.length = v } }

                SwitchRow("Link both diagonal widths (depth)", s.linkWidths) { v -> ui.edit { s.linkWidths = v; if (v) s.wBot = s.wTop } }
                Stepper(if (s.linkWidths) "Depth (diagonal width)" else "Top diagonal width", s.wTop, 0.5f, 40f, " dp", 0.1f, 1f, 1) { v ->
                    ui.edit { s.wTop = v; if (s.linkWidths) s.wBot = v }
                }
                if (!s.linkWidths) {
                    Stepper("Bottom diagonal width", s.wBot, 0.5f, 40f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.wBot = v } }
                }

                SwitchRow("Set diagonals by angle (off = by length along edge)", cfg.ui.angleMode) { v -> ui.edit { cfg.ui.angleMode = v } }
                SwitchRow("Link both diagonal heights", s.linkHeights) { v -> ui.edit { s.linkHeights = v; if (v) s.hBot = s.hTop } }
                if (cfg.ui.angleMode) {
                    Stepper("Top diagonal angle", angleOf(s.wTop, s.hTop), 0.5f, 85f, "°", 0.1f, 1f, 1) { d ->
                        ui.edit { s.hTop = runFromAngle(s.wTop, d); if (s.linkHeights) s.hBot = runFromAngle(s.wBot, d) }
                    }
                    if (!s.linkHeights) {
                        Stepper("Bottom diagonal angle", angleOf(s.wBot, s.hBot), 0.5f, 85f, "°", 0.1f, 1f, 1) { d ->
                            ui.edit { s.hBot = runFromAngle(s.wBot, d) }
                        }
                    }
                } else {
                    Stepper(if (s.linkHeights) "Diagonal height (along edge)" else "Top diagonal height", s.hTop, 0.5f, 250f, " dp", 0.1f, 1f, 1) { v ->
                        ui.edit { s.hTop = v; if (s.linkHeights) s.hBot = v }
                    }
                    if (!s.linkHeights) {
                        Stepper("Bottom diagonal height", s.hBot, 0.5f, 250f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.hBot = v } }
                    }
                }

                SwitchRow("Smooth (Apple-style) corners", s.smooth) { v -> ui.edit { s.smooth = v } }
                if (s.smooth) Stepper("Smoothing (control point)", s.smoothing, 0f, 1f, "", 0.01f, 0.1f, 2) { v -> ui.edit { s.smoothing = v } }
                else Hint("Plain corners: straight, circular arc, straight.")

                SwitchRow("Mirror ends (D copies A, C copies B)", s.mirrorCorners) { v -> ui.edit { s.mirrorCorners = v } }
                CornerEditor("Corner A · bezel → diagonal", s.cA, ui, s)
                CornerEditor("Corner B · diagonal → flat edge", s.cB, ui, s)
                if (!s.mirrorCorners) {
                    CornerEditor("Corner C · flat edge → diagonal", s.cC, ui, s)
                    CornerEditor("Corner D · diagonal → bezel", s.cD, ui, s)
                }

                ColorRow("Shape colour", s.color) { c -> ui.edit { s.color = c } }

                CopyShapeMenu(ui)
            }

            Section("Overrides · ${t.name}") {
                Hint("Anything left on inherit follows the global settings.")
                SwitchRow("Own colour", t.ov.color != null) { on -> ui.edit { t.ov.color = if (on) s.color else null } }
                t.ov.color?.let { c -> ColorRow("Colour", c) { v -> ui.edit { t.ov.color = v } } }
                Stepper("Size scale", t.ov.scale, 0.3f, 3f, "×", 0.01f, 0.1f, 2) { v -> ui.edit { t.ov.scale = v } }

                SwitchRow("Own expand time", t.ov.expandMs != null) { on -> ui.edit { t.ov.expandMs = if (on) cfg.anim.expandMs else null } }
                t.ov.expandMs?.let { v -> Stepper("Expand time", v, 30f, 1500f, " ms", 1f, 10f, 0) { x -> ui.edit { t.ov.expandMs = x } } }
                SwitchRow("Own contract time", t.ov.contractMs != null) { on -> ui.edit { t.ov.contractMs = if (on) cfg.anim.contractMs else null } }
                t.ov.contractMs?.let { v -> Stepper("Contract time", v, 30f, 1500f, " ms", 1f, 10f, 0) { x -> ui.edit { t.ov.contractMs = x } } }

                SwitchRow("Own expand easing", t.ov.expandEase != null) { on -> ui.edit { t.ov.expandEase = if (on) cfg.anim.expandEase.copy() else null } }
                t.ov.expandEase?.let { e -> EaseEditor("Expand easing", e) { f -> ui.edit { e.f() } } }
                SwitchRow("Own contract easing", t.ov.contractEase != null) { on -> ui.edit { t.ov.contractEase = if (on) cfg.anim.contractEase.copy() else null } }
                t.ov.contractEase?.let { e -> EaseEditor("Contract easing", e) { f -> ui.edit { e.f() } } }

                val hapticItems = listOf<HapticLevel?>(null) + HapticLevel.values().toList()
                Picker("Expand haptic", hapticItems, t.ov.hapticExpand, { it?.label ?: "Inherit" }) { h -> ui.edit { t.ov.hapticExpand = h } }
                Picker("Contract haptic", hapticItems, t.ov.hapticContract, { it?.label ?: "Inherit" }) { h -> ui.edit { t.ov.hapticContract = h } }

                OutlinedButton(onClick = { ui.edit { t.ov = Overrides() } }) { Text("Reset this button's overrides") }
            }

            PrimaryButton("Replay animation") { KeyService.instance?.replay(id, ui.profile, previewColor) }
        }

        // landscape: a slim strip the content scrolls under, so it never collides with the bulge
        if (pl.edge == Edge.TOP) {
            Box(
                Modifier.fillMaxWidth().height(strip + bars.calculateTopPadding()).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Transparent)))
            )
        }
        if (pl.edge == Edge.BOTTOM) {
            Box(
                Modifier.fillMaxWidth().height(strip + bars.calculateBottomPadding()).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.85f), bg)))
            )
        }
    }
}

@Composable
private fun CornerEditor(title: String, c: Corner, ui: UiState, s: Shape) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val all = listOf(s.cA, s.cB, s.cC, s.cD)
    Stepper("Radius", c.radius, 0f, 800f, " dp", 1f, 10f, 0, onAll = { ui.edit { all.forEach { it.radius = c.radius } } }) { v -> ui.edit { c.radius = v } }
    Stepper("Stretch width (depth direction)", c.w, 0.1f, 4f, "×", 0.01f, 0.1f, 2, onAll = { ui.edit { all.forEach { it.w = c.w } } }) { v -> ui.edit { c.w = v } }
    Stepper("Stretch height (along edge)", c.h, 0.1f, 4f, "×", 0.01f, 0.1f, 2, onAll = { ui.edit { all.forEach { it.h = c.h } } }) { v -> ui.edit { c.h = v } }
}

@Composable
private fun CopyShapeMenu(ui: UiState) {
    var open by remember { mutableStateOf(false) }
    val others = ProfileId.values().filter { it != ui.profile }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilledTonalButton(onClick = { open = true }) { Text("Copy shape from…") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                others.forEach { p ->
                    DropdownMenuItem(text = { Text(p.label) }, onClick = {
                        open = false
                        ui.edit { ui.cfg.profiles.getValue(ui.profile).shape = ui.cfg.profiles.getValue(p).shape.copy() }
                    })
                }
            }
        }
        FilledTonalButton(onClick = {
            ui.edit {
                val src = ui.cfg.profiles.getValue(ui.profile).shape
                others.forEach { ui.cfg.profiles.getValue(it).shape = src.copy() }
            }
        }) { Text("Copy to all others") }
    }
    Hint("Copies corners, diagonals and colour. Positions are never copied.")
}

// ======================= checklist =======================

@Composable
fun ChecklistScreen(ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val rt = ui.resumeTick
    val ctx = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(
                start = bars.calculateStartPadding(ld) + 16.dp, end = bars.calculateEndPadding(ld) + 16.dp,
                top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 40.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientText("Setup", 44.sp)
        Hint("You can skip this and come back any time from the main screen.")

        Section("1 · Allow restricted settings") {
            Text("Android 13+ blocks accessibility for sideloaded apps. Open App info, tap ⋮ (top right) and choose Allow restricted settings. The option appears after you have tried to enable the service once (step 2).")
            Hint("Can't be detected automatically.")
            FilledTonalButton(onClick = { ctx.startActivity(appInfo()) }) { Text("Open App info") }
        }
        Section("2 · Turn on the accessibility service") {
            Text("Accessibility > Installed apps > BezelPop > On. It only listens for key presses and reads no screen content.")
            Text(if (Checks.serviceEnabled(ctx)) "Status: enabled ✓" else "Status: not enabled yet")
            FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
        }
        Section("3 · Battery: Unrestricted") {
            Text("Samsung can silently kill accessibility services. Set Battery to Unrestricted (App info > Battery) and make sure BezelPop isn't under Settings > Battery > Background usage limits > Sleeping apps.")
            Text(if (Checks.batteryUnrestricted(ctx)) "Status: unrestricted ✓" else "Status: not yet unrestricted")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    } catch (e: Exception) { ctx.startActivity(appInfo()) }
                }) { Text("Ask to ignore") }
                FilledTonalButton(onClick = { ctx.startActivity(appInfo()) }) { Text("App info") }
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Done / Skip for now", Modifier.fillMaxWidth()) { ui.showChecklist = false }
    }
}

```

---

