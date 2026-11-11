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
