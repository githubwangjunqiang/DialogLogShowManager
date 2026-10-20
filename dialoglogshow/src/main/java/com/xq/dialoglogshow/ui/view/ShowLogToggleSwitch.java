package com.xq.dialoglogshow.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.xq.dialoglogshow.R;

/**
 * 两态滑动开关。
 * 为什么这样做：列表/分组、时间倒序/正序这类二选一控件若拆成两个独立按钮，会占用横向空间且选中态容易割裂；
 * 收敛成单个带滑动滑块的开关后，用户点击任意一侧即可切换，视觉上始终是一个整体按钮。
 */
public final class ShowLogToggleSwitch extends FrameLayout {

    /** 滑块动画时长：过短会像跳变，过长会拖慢连续切换。 */
    private static final long THUMB_ANIM_DURATION_MS = 160L;

    /**
     * 滑块直接由控件自身绘制，而不是作为子 View。
     * 为什么这样做：子 View 的宽高依赖控件最终尺寸，在 onSizeChanged 中调用 setLayoutParams 会触发一次
     * 布局周期内的 requestLayout，该请求可能被当前布局流程丢弃，导致滑块一直保持 0 宽而不可见。
     */
    private final Drawable mThumbDrawable;

    /** 滑块位置比例：0 表示完全在左侧，1 表示完全在右侧。 */
    private float mThumbFraction;

    @Nullable
    private ValueAnimator mThumbAnimator;

    private TextView mLeftText;
    private TextView mRightText;
    private boolean mChecked;
    private OnCheckedChangeListener mListener;

    public ShowLogToggleSwitch(@NonNull Context context) {
        this(context, null);
    }

    public ShowLogToggleSwitch(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShowLogToggleSwitch(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mThumbDrawable = ContextCompat.getDrawable(context, R.drawable.bg_showlog_toggle_thumb);
        LayoutInflater.from(context).inflate(R.layout.show_view_toggle_switch, this, true);
        initViews();
    }

    private void initViews() {
        mLeftText = findViewById(R.id.toggle_left);
        mRightText = findViewById(R.id.toggle_right);

        // 左右两半分别代表“未选中/选中”，点击控件空白处时取反，保证整块区域都能响应
        mLeftText.setOnClickListener(v -> setCheckedFromUser(false));
        mRightText.setOnClickListener(v -> setCheckedFromUser(true));
        setOnClickListener(v -> setCheckedFromUser(!mChecked));
        setFocusable(true);

        updateLabelState();
    }

    /**
     * 设置左右两侧文案。
     * 为什么这样做：同一个开关要复用于“列表/分组”和“倒序/正序”两种场景，文案由调用方注入，避免为每种场景复制控件。
     */
    public void setLabels(@NonNull CharSequence left, @NonNull CharSequence right) {
        mLeftText.setText(left);
        mRightText.setText(right);
    }

    /**
     * 设置当前选中状态。
     * 该方法只更新视觉，不触发回调；供初始化或防抖回滚使用，避免状态回滚时再次触发业务刷新。
     */
    public void setChecked(boolean checked, boolean animate) {
        if (mChecked == checked) {
            animateThumbTo(checked ? 1f : 0f, false);
            return;
        }
        mChecked = checked;
        animateThumbTo(checked ? 1f : 0f, animate);
        updateLabelState();
    }

    public boolean isChecked() {
        return mChecked;
    }

    /**
     * 注册用户操作回调；仅在用户点击左右两侧或控件本身导致状态变化时触发。
     */
    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        mListener = listener;
    }

    private void setCheckedFromUser(boolean checked) {
        if (mChecked == checked) {
            return;
        }
        mChecked = checked;
        animateThumbTo(checked ? 1f : 0f, true);
        updateLabelState();
        if (mListener != null) {
            mListener.onCheckedChanged(this, mChecked);
        }
    }

    /**
     * 将滑块移动到目标位置。
     * 为什么这样做：动画必须从当前比例继续，快速连续切换时才不会跳回起点；非动画场景则立即落位。
     */
    private void animateThumbTo(float targetFraction, boolean animate) {
        cancelThumbAnimator();
        if (!animate || getWidth() == 0) {
            mThumbFraction = targetFraction;
            invalidate();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(mThumbFraction, targetFraction);
        animator.setDuration(THUMB_ANIM_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            mThumbFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        mThumbAnimator = animator;
        animator.start();
    }

    private void cancelThumbAnimator() {
        if (mThumbAnimator != null) {
            mThumbAnimator.cancel();
            mThumbAnimator = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 先绘制滑块再绘制子 View，保证左右文案始终压在蓝色滑块之上
        drawThumb(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawThumb(@NonNull Canvas canvas) {
        if (mThumbDrawable == null) {
            return;
        }
        int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }

        int thumbWidth = contentWidth / 2;
        // 可用行程 = 内容区宽度 - 滑块宽度；用行程乘以比例，选中时滑块边缘能精确贴合内容区
        int travel = contentWidth - thumbWidth;
        int offset = Math.round(travel * mThumbFraction);
        // LinearLayout 在 RTL 下会自动把第一个标签排到右侧，滑块必须同步镜像，否则会压住另一个标签
        int left = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? getPaddingLeft() + travel - offset
                : getPaddingLeft() + offset;
        mThumbDrawable.setBounds(left, getPaddingTop(), left + thumbWidth,
                getPaddingTop() + contentHeight);
        mThumbDrawable.draw(canvas);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        // 布局方向变化后滑块位置需要按新方向重算，主动重绘避免停留在旧侧
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        // 页面关闭后仍在运行的动画会持有 View 引用并触发无意义重绘
        cancelThumbAnimator();
        super.onDetachedFromWindow();
    }

    private void updateLabelState() {
        int selectedColor = ContextCompat.getColor(getContext(), R.color.showlog_text_on_accent);
        int normalColor = ContextCompat.getColor(getContext(), R.color.showlog_text_secondary);
        mLeftText.setTextColor(mChecked ? normalColor : selectedColor);
        mRightText.setTextColor(mChecked ? selectedColor : normalColor);
        mLeftText.setTypeface(null, mChecked ? Typeface.NORMAL : Typeface.BOLD);
        mRightText.setTypeface(null, mChecked ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * 用户切换回调。
     */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(@NonNull ShowLogToggleSwitch view, boolean isChecked);
    }
}
