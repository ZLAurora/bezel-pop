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
