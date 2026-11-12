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
    EaseType.SPRING -> SpringInterpolator(strength)
    EaseType.BEZIER -> PathInterpolator(bz[0].coerceIn(0f, 1f), bz[1], bz[2].coerceIn(0f, 1f), bz[3])
}

class SpringInterpolator(private val strength: Float) : TimeInterpolator {
    override fun getInterpolation(x: Float): Float {
        val d = 3f + strength
        return 1f - kotlin.math.exp(-d * x) * kotlin.math.cos(9f * x)
    }
}

/** depth/length are the shape only; [bleed] extra px sit beyond the bezel line so no gap can show. */
class Built(val path: Path, val depth: Float, val length: Float, val bleed: Float)

object ShapeBuilder {

    /**
     * Outline: bezel -> corner A -> diagonal -> corner B -> flat edge -> corner C -> diagonal -> corner D -> bezel.
     * Every corner is one cubic Bezier. smoothing 0 = circular arc, 1 = very gradual (curvature-continuous feel).
     * Stretch w / h scale the corner in the depth / along-edge direction.
     * Returned path is in view pixels for the requested [edge]; bezel sits on that edge.
     */
    fun build(s: Shape, edge: Edge, density: Float, bleed: Float = 2f): Built {
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
        // bleed: fill a strip beyond the bezel line, then shift so the bezel line sits at x = bleed
        path.addRect(-bleed, 0f, 1f, length, Path.Direction.CW)
        path.offset(bleed, 0f)
        val vd = depth + bleed

        val m = Matrix()
        when (edge) {
            Edge.LEFT -> return Built(path, depth, length, bleed)
            Edge.RIGHT -> m.setValues(floatArrayOf(-1f, 0f, vd, 0f, 1f, 0f, 0f, 0f, 1f))
            Edge.TOP -> m.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            Edge.BOTTOM -> m.setValues(floatArrayOf(0f, 1f, 0f, -1f, 0f, vd, 0f, 0f, 1f))
        }
        path.transform(m)
        return Built(path, depth, length, bleed)
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
