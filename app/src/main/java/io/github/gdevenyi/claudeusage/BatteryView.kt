package io.github.gdevenyi.claudeusage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/** A horizontal battery glyph: outline, fill by level, a bolt while charging. */
class BatteryView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {

    private var level = 0
    private var charging = false

    private val dp = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val bar = RectF()
    private val path = Path()

    fun set(level: Int, charging: Boolean) {
        this.level = level.coerceIn(0, 100)
        this.charging = charging
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        stroke.color = outline
        fill.color = when {
            charging -> context.getColor(R.color.usage_ok)
            level <= 15 -> context.getColor(R.color.usage_crit)
            else -> outline
        }
        bolt.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

        val h = height.toFloat()
        val nub = 2.5f * dp
        val half = stroke.strokeWidth / 2
        body.set(half, half, width - nub - half, h - half)
        canvas.drawRoundRect(body, 3 * dp, 3 * dp, stroke)
        // The terminal nub on the right.
        nubPaint.color = outline
        canvas.drawRect(width - nub, h * 0.3f, width.toFloat(), h * 0.7f, nubPaint)

        val inset = half + 2 * dp
        bar.set(inset, inset, inset + (body.right - half - 2 * dp - inset) * level / 100f, h - inset)
        canvas.drawRoundRect(bar, 1.5f * dp, 1.5f * dp, fill)

        if (charging) {
            val cx = body.centerX()
            val cy = h / 2
            val s = h * 0.36f
            path.reset()
            path.moveTo(cx + s * 0.25f, cy - s)
            path.lineTo(cx - s * 0.55f, cy + s * 0.15f)
            path.lineTo(cx - s * 0.05f, cy + s * 0.15f)
            path.lineTo(cx - s * 0.25f, cy + s)
            path.lineTo(cx + s * 0.55f, cy - s * 0.15f)
            path.lineTo(cx + s * 0.05f, cy - s * 0.15f)
            path.close()
            canvas.drawPath(path, bolt)
        }
    }
}
