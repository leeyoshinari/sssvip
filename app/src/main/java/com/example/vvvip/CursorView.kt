package com.example.vvvip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator

class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 外圈 Paint
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FF4081") // 半透明粉红色外圈
        style = Paint.Style.FILL
    }

    // 内圈 Paint
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081") // 实心主色
        style = Paint.Style.FILL
    }

    // 白色边框 Paint
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = Math.min(centerX, centerY)

        // 1. 绘制外层半透明光晕圆
        canvas.drawCircle(centerX, centerY, radius, outerPaint)
        // 2. 绘制内层实心圆点
        canvas.drawCircle(centerX, centerY, radius * 0.5f, innerPaint)
        // 3. 绘制白色外边框
        canvas.drawCircle(centerX, centerY, radius * 0.5f, strokePaint)
    }

    /**
     * 按压反馈动画：按下缩小
     */
    fun animatePressDown() {
        this.animate()
            .scaleX(0.75f)
            .scaleY(0.75f)
            .setDuration(60)
            .start()
    }

    /**
     * 按压反馈动画：抬起恢复
     */
    fun animatePressUp(onComplete: () -> Unit = {}) {
        this.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(120)
            .setInterpolator(OvershootInterpolator(2.0f))
            .withEndAction { onComplete() }
            .start()
    }
}