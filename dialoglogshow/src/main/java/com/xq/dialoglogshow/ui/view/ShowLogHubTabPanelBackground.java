package com.xq.dialoglogshow.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.xq.dialoglogshow.R;

/**
 * 主中枢 Tab 与内容面板的一体化背景。
 * 为什么这样做：
 * 1. 选中 Tab 若使用独立背景，底边与内容面板顶边会形成生硬的直角接缝；
 * 2. 这里用同一条 Path 绘制“选中页签 + 内容面板”的联合轮廓，并在页签两侧加入内凹圆角，
 *    让边框从页签自然过渡到内容面板；
 * 3. 描边沿用内容面板的灰色描边，避免高饱和蓝色边框在深色弹框中过于突兀。
 */
public final class ShowLogHubTabPanelBackground extends View {
    /** 判断页签是否贴近内容面板左右边缘的像素容差，避免浮点误差导致边缘页签仍绘制内凹圆弧。 */
    private static final float JOINT_EPSILON_PX = 0.5f;

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    private final float mPanelTop;
    private final float mTabCorner;
    private final float mJointCorner;
    private final float mBottomCorner;

    private float mSelectedTabLeft;
    private float mSelectedTabRight;

    public ShowLogHubTabPanelBackground(@NonNull Context context) {
        this(context, null);
    }

    public ShowLogHubTabPanelBackground(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShowLogHubTabPanelBackground(@NonNull Context context,
                                        @Nullable AttributeSet attrs,
                                        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(ContextCompat.getColor(context, R.color.showlog_bg_surface));

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(context.getResources().getDimension(R.dimen.dp_1));
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        mStrokePaint.setColor(ContextCompat.getColor(context, R.color.showlog_bg_card_stroke));

        // 轮廓几何尺寸必须与布局中的 Tab 栏高度、内容面板 marginTop 和圆角保持一致
        mPanelTop = context.getResources().getDimension(R.dimen.dp_36);
        mTabCorner = context.getResources().getDimension(R.dimen.dp_10);
        mJointCorner = context.getResources().getDimension(R.dimen.dp_6);
        mBottomCorner = context.getResources().getDimension(R.dimen.dp_12);
    }

    /**
     * 更新滚动视口内选中页签的可视边界。
     * 为什么这样做：Tab 宽度由标题决定且可横向滚动，固定等分会让选中轮廓与真实页签错位。
     */
    public void setSelectedTab(float tabLeft, float tabRight) {
        float safeLeft = Math.max(0f, Math.min(tabLeft, getWidth()));
        float safeRight = Math.max(safeLeft, Math.min(tabRight, getWidth()));
        if (mSelectedTabLeft == safeLeft && mSelectedTabRight == safeRight) {
            return;
        }
        mSelectedTabLeft = safeLeft;
        mSelectedTabRight = safeRight;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        buildJointPath(width, height);
        canvas.drawPath(mPath, mFillPaint);
        // 先填充再描边，保证 1dp 边框压在填充之上，接缝处不会出现颜色断层
        canvas.drawPath(mPath, mStrokePaint);
    }

    /**
     * 构建“页签 + 内容面板”的联合外轮廓。
     * 为什么这样做：只有把两者画成同一条闭合路径，边框才能在连接处连续，
     * 并通过二次贝塞尔曲线形成内凹圆角，而不是两条线垂直相交的直角。
     */
    private void buildJointPath(int width, int height) {
        float strokeInset = mStrokePaint.getStrokeWidth() / 2f;
        float left = strokeInset;
        float right = width - strokeInset;
        float bottom = height - strokeInset;
        float panelTop = mPanelTop;
        float tabTop = strokeInset;
        float tabCorner = mTabCorner;
        float jointCorner = mJointCorner;
        float bottomCorner = mBottomCorner;

        // 直接使用滚动视口中选中 Tab 的可视位置，RTL 和横向滚动均无需再次换算索引。
        float tabLeft = Math.max(left, Math.min(mSelectedTabLeft, right));
        float tabRight = Math.max(tabLeft, Math.min(mSelectedTabRight, right));

        mPath.reset();
        mPath.moveTo(left, panelTop);

        boolean hasLeftJoint = tabLeft > left + JOINT_EPSILON_PX;
        if (hasLeftJoint) {
            float jointStart = Math.max(left, tabLeft - jointCorner);
            mPath.lineTo(jointStart, panelTop);
            if (tabLeft > jointStart + JOINT_EPSILON_PX) {
                // 从内容面板顶边向上弯入 Tab 左边框，形成内凹圆角
                mPath.quadTo(tabLeft, panelTop, tabLeft, panelTop - jointCorner);
            } else {
                mPath.lineTo(tabLeft, panelTop - jointCorner);
            }
            mPath.lineTo(tabLeft, tabTop + tabCorner);
        } else {
            // 选中第一个 Tab 时，左边缘与内容面板左边框直接连成一条直线
            mPath.lineTo(left, tabTop + tabCorner);
        }

        mPath.quadTo(tabLeft, tabTop, tabLeft + tabCorner, tabTop);
        mPath.lineTo(tabRight - tabCorner, tabTop);
        mPath.quadTo(tabRight, tabTop, tabRight, tabTop + tabCorner);

        boolean hasRightJoint = tabRight < right - JOINT_EPSILON_PX;
        if (hasRightJoint) {
            mPath.lineTo(tabRight, panelTop - jointCorner);
            float jointEnd = Math.min(right, tabRight + jointCorner);
            if (jointEnd > tabRight + JOINT_EPSILON_PX) {
                mPath.quadTo(tabRight, panelTop, jointEnd, panelTop);
            } else {
                mPath.lineTo(jointEnd, panelTop);
            }
            mPath.lineTo(right, panelTop);
        } else {
            // 选中最后一个 Tab 时，右边缘与内容面板右边框直接连成一条直线
            mPath.lineTo(right, panelTop);
        }

        mPath.lineTo(right, bottom - bottomCorner);
        mPath.quadTo(right, bottom, right - bottomCorner, bottom);
        mPath.lineTo(left + bottomCorner, bottom);
        mPath.quadTo(left, bottom, left, bottom - bottomCorner);
        mPath.close();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        // 布局方向变化后联合轮廓需要重新镜像计算
        invalidate();
    }
}
