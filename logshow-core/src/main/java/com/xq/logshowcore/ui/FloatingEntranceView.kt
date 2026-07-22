package com.xq.logshowcore.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 调试面板悬浮入口，完全复刻原版 UI 动画与拖拽逻辑。
 */
class FloatingEntranceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val paintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#bbff0000")
        strokeWidth = dpToPx(1f)
    }

    private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#ddFFFFFF")
        strokeWidth = dpToPx(1f)
    }

    private val paintFill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#ddFFFFFF")
        strokeWidth = dpToPx(1f)
        textSize = dpToPx(9f)
    }

    private var animator: AnimatorSet? = null

    // 动画属性，必须 public 并加上 @JvmField 供反射，或用 setter
    var startRed: Float = 0f
        set(value) {
            field = value
            postInvalidate()
        }

    var redSize: Float = 0f
        set(value) {
            field = value
            postInvalidate()
        }

    private var minWidthSize = 0
    private var minHeightSize = 0
    private val mHorizontalPadding: Float = dpToPx(2f)

    private val mRectF = RectF()
    private val mRectF2 = RectF()
    private val mRectFBack = RectF()
    private val mPath = Path()

    init {
        initData()
    }

    private fun initData() {
        minWidthSize = dpToPx(40f).toInt()
        minHeightSize = dpToPx(12f).toInt()

        // 获取当前应用版本名称
        var appVersion = "Debug"
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = packageInfo.versionName ?: "Debug"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        text = appVersion
        setTextColor(Color.WHITE)
        textSize = 10f
        gravity = Gravity.CENTER
        val padding = mHorizontalPadding.toInt()
        setPadding(padding * 2, padding, padding * 2, padding)

        setupAnimator()
    }

    private fun setupAnimator() {
        animator = AnimatorSet()
        
        val redAnim = ObjectAnimator.ofFloat(this, "startRed", 0f, 360f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
        }

        val raSizeAnim = ObjectAnimator.ofFloat(this, "redSize", 10f, 90f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = LinearInterpolator()
        }

        animator?.playTogether(redAnim, raSizeAnim)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mRectF.set(0f, 0f, w.toFloat(), h.toFloat())
        mRectF2.set(0f, 0f, w.toFloat(), h.toFloat())
        mRectFBack.set(0f, 0f, w.toFloat(), h.toFloat())
        mRectF2.inset(mHorizontalPadding, mHorizontalPadding)
        mRectFBack.inset(-mHorizontalPadding * 10, -mHorizontalPadding * 10)
    }

    override fun onDraw(canvas: Canvas) {
        // 画红底圆角矩形
        canvas.drawRoundRect(mRectF, mHorizontalPadding, mHorizontalPadding, paintRed)
        super.onDraw(canvas)

        // 画动画扫光效果
        canvas.save()
        mPath.rewind()
        mPath.moveTo(width * 0.5f, height * 0.5f)
        mPath.arcTo(mRectFBack, startRed, redSize, false)
        mPath.close()
        canvas.clipPath(mPath)
        canvas.drawRoundRect(mRectF2, mHorizontalPadding, mHorizontalPadding, paintWhite)
        canvas.restore()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        try {
            if (visibility == View.VISIBLE) {
                animator?.start()
            } else {
                animator?.end()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            animator?.end()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 拖拽与点击逻辑 ---

    private var downX = 0f
    private var downY = 0f
    private var oldX = 0f
    private var oldY = 0f
    private var hasItMoved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                oldX = x
                oldY = y
                hasItMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                
                val parentView = parent as? View
                if (parentView != null) {
                    val newX = min(max(oldX + dx, 0f), parentView.width - width.toFloat())
                    val newY = min(max(oldY + dy, 0f), parentView.height - height.toFloat())
                    x = newX
                    y = newY
                }

                if (abs(dx) > 10 || abs(dy) > 10) {
                    hasItMoved = true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (hasItMoved) {
                    return true
                } else {
                    performClick()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        (context as? Activity)?.let {
            DebugConsoleDialog(it).show()
        }
        return true
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    companion object {
        private var instance: FloatingEntranceView? = null

        fun attachTo(activity: Activity) {
            detach()
            val decorView = activity.window.decorView as? ViewGroup ?: return
            
            instance = FloatingEntranceView(activity).apply {
                // 初始化位置：类似旧版，也可以放右侧中间
                val params = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 300 // 默认初始高度
                decorView.addView(this, params as ViewGroup.LayoutParams)
            }
        }

        fun detach() {
            instance?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }
            instance = null
        }
    }
}
