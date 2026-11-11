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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

fun currentProfileId(ctx: Context): ProfileId {
    val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    return when (dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
        Surface.ROTATION_90 -> ProfileId.L90
        Surface.ROTATION_180 -> ProfileId.P180
        Surface.ROTATION_270 -> ProfileId.L270
        else -> ProfileId.P0
    }
}

/**
 * One overlay per trigger. Pure scale animation of a pre-built path, anchored at the bezel.
 * With [persistent] the (touch-transparent) window stays attached at scale 0 so presses start instantly.
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
    private var held = false
    private var pinned = false
    private var persistent = false
    private val auto = Runnable { lastCfg?.let { release(it) } }

    fun setPersistent(p: Boolean) {
        persistent = p
        if (!p && root != null && !held && !pinned && progress < 0.001f) detach()
    }

    fun prewarm(cfg: Config) {
        persistent = true
        if (held || pinned || progress > 0.001f || anim?.isRunning == true) return
        lastCfg = cfg
        ensure(cfg, currentProfileId(svc), null)
    }

    fun press(cfg: Config, forced: ProfileId? = null) {
        if (pinned) return
        handler.removeCallbacks(auto)
        lastCfg = cfg
        held = true
        if (!ensure(cfg, forced ?: currentProfileId(svc), null)) return
        val a = cfg.animFor(trigId)
        if (progress < 0.001f) Haptics.play(svc, a.hapticExpand)
        animateTo(1f, a.expandMs, a.expandEase, a.latencyMs)
        handler.postDelayed(auto, (a.maxHoldSec * 1000f).toLong())
    }

    fun release(cfg: Config) {
        handler.removeCallbacks(auto)
        if (root == null || !held || pinned) return
        held = false
        lastCfg = cfg
        val a = cfg.animFor(trigId)
        Haptics.play(svc, a.hapticContract)
        animateTo(0f, a.contractMs, a.contractEase, 0f)
    }

    fun pulse(cfg: Config, forced: ProfileId? = null, extraHoldMs: Long = 0L) {
        press(cfg, forced)
        handler.removeCallbacks(auto)
        val a = cfg.animFor(trigId)
        handler.postDelayed(auto, (a.expandMs + a.pulseHoldMs).toLong() + extraHoldMs)
    }

    /** Edit mode: keep the bulge fully out and rebuild it live when the config changes. */
    fun pin(cfg: Config, forced: ProfileId, colorOverride: Int?) {
        handler.removeCallbacks(auto)
        lastCfg = cfg
        pinned = true
        held = false
        val before = progress
        if (!ensure(cfg, forced, colorOverride)) return
        if (before > 0.5f) {
            anim?.cancel()
            setProgress(1f)
        } else {
            val a = cfg.animFor(trigId)
            animateTo(1f, a.expandMs, a.expandEase, 0f)
        }
    }

    fun unpin(cfg: Config) {
        if (!pinned) return
        pinned = false
        held = true
        release(cfg)
    }

    fun destroy() {
        handler.removeCallbacks(auto)
        detach()
    }

    private fun setProgress(v: Float) {
        progress = max(0f, v)
        bulge?.let { if (vertical) it.scaleY = progress else it.scaleX = progress }
    }

    private fun animateTo(target: Float, ms: Float, ease: Ease, skipMs: Float) {
        anim?.cancel()
        val from = progress
        val dur = (ms * abs(target - from)).toLong()
        if (dur <= 0L) {
            setProgress(target)
            if (target == 0f) idle()
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
                if (target == 0f) idle()
            }
        })
        anim = va
        va.start()
        // latency compensation: jump ahead so the first visible frame is already moving
        if (skipMs > 0f && target > from && dur > 1L) {
            va.currentPlayTime = skipMs.toLong().coerceIn(0L, dur - 1L)
        }
    }

    private fun idle() {
        if (!persistent) detach()
    }

    private fun ensure(cfg: Config, pid: ProfileId, color: Int?): Boolean {
        val t = cfg.trigger(trigId) ?: return false
        val newSig = "${cfg.rev}|${pid.name}|${color ?: 0}|${color != null}"
        if (root != null && newSig == sig) return true
        detach()
        val shape = cfg.effectiveShape(pid, t, color)
        if (!attach(shape, cfg.placement(pid, trigId))) return false
        sig = newSig
        return true
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
        val built = ShapeBuilder.build(shape, pl.edge, density)
        val depth = max(1, ceil(built.depth).toInt())
        val length = max(2, ceil(built.length).toInt())
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
        v.setPath(built.path, shape.color)
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
        held = false
        sig = ""
    }
}
