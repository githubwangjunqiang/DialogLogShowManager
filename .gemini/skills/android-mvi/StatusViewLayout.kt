package com.xq.video.social.base.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.Keep
import com.xq.video.social.R
import com.xq.video.social.base.baseui.UIStatus
import com.xq.video.social.base.utils.setOnClickListener400
import com.xq.video.social.manager.log.logd

/**
 * Description: 视图状态切换布局 (Loading/Error/Empty/Content)
 * 支持淡入淡出动画，高性能防冲突
 * Author: 王俊强 980766134@qq.com
 * Date: 2025/10/13
 */
@Keep
open class StatusViewLayout : FrameLayout {

    companion object {
        private const val ANIM_DURATION = 250L // 动画时长 ms

        @JvmStatic
        fun initContext(errorId: Int, loadingId: Int, emptyId: Int) {
            loadingViewResId = loadingId
            errorViewResId = errorId
            emptyViewResId = emptyId
        }

        @JvmStatic
        fun View.findStatusViewLayout(): StatusViewLayout? {
           return this.findViewWithTag<StatusViewLayout>(StatusViewLayout.tag_StatusView_layout)
//            if (this is StatusViewLayout) return this
//            if (this is ViewGroup) {
//                for (i in 0 until childCount) {
//                    val found = getChildAt(i).findStatusViewLayout()
//                    if (found != null) return found
//                }
//            }
//            return null
        }

        @JvmStatic
        private var loadingViewResId: Int? = null

        @JvmStatic
        private var emptyViewResId: Int? = null

        @JvmStatic
        private var errorViewResId: Int? = null

        private const val tag_StatusView_layout = "tag_StatusView_layout"
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initView(context, attrs)
    }

    private var loadingView: View? = null
    private var errorView: View? = null
    private var emptyView: View? = null

    private var clickError: ((View) -> Unit)? = null
    private var clickEmpty: ((View) -> Unit)? = null

    private var defaultErrorRes: Int = R.mipmap.app_error_net_icon
    private var defaultEmptyRes: Int = R.mipmap.app_empty_icon

    private var listenerList: StatusListener? = null

    // 当前状态记录，防止重复调用
    private var currentStatus: UIStatus = UIStatus.Content

    private fun initView(context: Context, attrs: AttributeSet?) {
        val obtainStyledAttributes =
            context.obtainStyledAttributes(attrs, R.styleable.StatusViewLayout)

        defaultEmptyRes = obtainStyledAttributes.getResourceId(
            R.styleable.StatusViewLayout_status_view_empty_icon,
            defaultEmptyRes
        )
        defaultErrorRes = obtainStyledAttributes.getResourceId(
            R.styleable.StatusViewLayout_status_view_error_icon,
            defaultErrorRes
        )
        val skip = obtainStyledAttributes.getBoolean(
            R.styleable.StatusViewLayout_status_view_skip_tag,
            false
        )

        obtainStyledAttributes.recycle()

        removeAllViews()
        errorViewResId?.also { view ->
            val inflated = LayoutInflater.from(context).inflate(view, this, false)
            setErrorView(inflated)
        }
        loadingViewResId?.also { view ->
            val inflated = LayoutInflater.from(context).inflate(view, this, false)
            setLoadingView(inflated)
        }
        emptyViewResId?.also { viewResId ->
            val inflated = LayoutInflater.from(context).inflate(viewResId, this, false)
            setEmptyView(inflated)
        }

        // 初始状态默认为隐藏（Content）
        this.visibility = GONE
        this.alpha = 0f

        if (!skip) {
            this.tag = tag_StatusView_layout
        }
    }

    /**
     * 自定义
     */
    fun setLoadingView(view: View) {
        loadingView?.let { removeView(it) }
        loadingView = view
        addView(loadingView)
        loadingView?.visibility = GONE
        loadingView?.alpha = 0f
        loadingView?.setOnClickListener { }
    }

    /**
     * 自定义
     */
    fun setErrorView(view: View) {
        errorView?.let { removeView(it) }
        errorView = view
        addView(errorView)
        errorView?.visibility = GONE
        errorView?.alpha = 0f
        errorView?.setOnClickListener400 {
            clickError?.invoke(errorView!!)
        }
    }

    /**
     * 自定义
     */
    fun setEmptyView(view: View) {
        emptyView?.let { removeView(it) }
        emptyView = view
        addView(emptyView)
        emptyView?.visibility = GONE
        emptyView?.alpha = 0f
        emptyView?.setOnClickListener400 {
            clickEmpty?.invoke(emptyView!!)
        }
//        emptyView?.setOnTouchListener {v, event ->
//            if(event.action == MotionEvent.ACTION_DOWN){
//                clickEmpty?.invoke(emptyView!!)
//                return@setOnTouchListener true
//            }
//            false
//        }
    }

    /**
     * 改变状态
     */
    fun changeUIStatus(uiStatus: UIStatus) {
        post {
            "changeUIStatus-changeUIStatus-last:${currentStatus}".logd()
            "changeUIStatus-changeUIStatus-new:${uiStatus}".logd()
            if (currentStatus == uiStatus) {
                return@post
            }
            currentStatus = uiStatus
            listenerList?.change(uiStatus)
            cancelAnimation(view = null)
            when (uiStatus) {
                is UIStatus.Content -> {
                    "changeUIStatus-Content".logd()
                    fadeOut(this) {
                        "changeUIStatus-Content-GONE".logd()
                        this.visibility = View.GONE
                    }
                }

                is UIStatus.Empty -> {
                    this.visibility = View.VISIBLE
                    fadeIn(this)
                    errorView?.visibility = View.GONE
                    loadingView?.visibility = View.GONE
                    emptyView?.visibility = View.VISIBLE
                    emptyView?.also { view ->
                        fadeIn(view)
                        if (uiStatus.resId != null) {
                            view.findViewById<ImageView>(R.id.error_icon)
                                ?.setImageResource(uiStatus.resId)
                        } else {
                            view.findViewById<ImageView>(R.id.error_icon)
                                ?.setImageResource(defaultEmptyRes)
                        }
                    }

                }

                is UIStatus.Error -> {
                    this.visibility = View.VISIBLE
                    fadeIn(this)
                    emptyView?.visibility = View.GONE
                    loadingView?.visibility = View.GONE
                    errorView?.visibility = View.VISIBLE
                    errorView?.also { view ->
                        fadeIn(view)
                        if (uiStatus.resId != null) {
                            view.findViewById<ImageView>(R.id.error_icon)
                                ?.setImageResource(uiStatus.resId)
                        } else {
                            view.findViewById<ImageView>(R.id.error_icon)
                                ?.setImageResource(defaultErrorRes)
                        }
                    }
                }

                is UIStatus.Loading -> {
                    this.visibility = View.VISIBLE
                    fadeIn(this)
                    emptyView?.visibility = View.GONE
                    errorView?.visibility = View.GONE
                    loadingView?.visibility = View.VISIBLE
                    loadingView?.also { view ->
                        fadeIn(view)
                    }
                }

                is UIStatus.LoadingDialog -> {
                    fadeOut(this) {
                        this.visibility = View.GONE
                    }
                }
            }
        }
    }

    private var lastView: View? = null

    fun cancelAnimation(view: View?) {
        if (view == null) {
            lastView?.animate()?.setListener(null)?.cancel()
        } else {
            view.animate().setListener(null).cancel()
        }

    }

    /**
     * 淡入动画帮助类
     */
    private fun fadeIn(view: View) {
        lastView = view
        // 关键：先取消该 View 上正在进行的任何动画，防止冲突
        cancelAnimation(view)
        view.animate()
            .alpha(1f)
            .setDuration(ANIM_DURATION)
            .setListener(null) // 清除之前的 EndListener (比如 fadeOut 设置的 GONE)
            .start()
    }

    /**
     * 淡出动画帮助类
     */
    private fun fadeOut(view: View, onEnd: () -> Unit) {
        lastView = view
        // 关键：先取消该 View 上正在进行的任何动画
        cancelAnimation(view)

        view.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd() // 执行结束回调（通常是 set GONE）
                }
            })
            .start()
    }

    // region Listeners
    fun setErrorClick(click: (View) -> Unit) {
        this.clickError = click
    }

    fun setEmptyClick(click: (View) -> Unit) {
        this.clickEmpty = click
    }

    fun setContentShowListener(listenerList: StatusListener) {
        this.listenerList = listenerList
    }

    interface StatusListener {
        fun change(status: UIStatus)
    }
    // endregion

}