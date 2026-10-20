package com.xq.dialoglogshow.ui.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.ui.dialog.HttpLogConsoleDialog;

/**
 * 免权限轻量拖拽悬浮球。
 * 为什么这样做：
 * 1. 依附于 Activity 的 DecorView，完全免除 SYSTEM_ALERT_WINDOW 危险悬浮窗权限申请；
 * 2. 支持触控拖拽与松手自动贴边；
 * 3. 单击后直接调起全新的网络日志控制台。
 */
public final class HttpLogFloatView extends FrameLayout {

    private static final int FLOAT_SIZE_DP = 52;
    private static final long CLICK_THRESHOLD_MS = 400L;
    private static final float MOVE_THRESHOLD_PX = 30f;

    private float mDownRawX;
    private float mDownRawY;
    private float mTouchStartX;
    private float mTouchStartY;
    private long mDownTime;

    public HttpLogFloatView(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        int sizePx = dp2px(getContext(), FLOAT_SIZE_DP);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(0, 0, dp2px(getContext(), 16), dp2px(getContext(), 100));
        setLayoutParams(params);

        // 圆形深色质感背景
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#E61E1E24"));
        bg.setStroke(dp2px(getContext(), 1.5f), Color.parseColor("#38BDF8"));
        setBackground(bg);

        setClickable(true);
        setFocusable(false);

        TextView tv = new TextView(getContext());
        tv.setText("LOG");
        tv.setTextColor(Color.parseColor("#38BDF8"));
        tv.setTextSize(13);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setClickable(false);
        tv.setFocusable(false);

        FrameLayout.LayoutParams tvParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        addView(tv, tvParams);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();
                mDownRawX = rawX;
                mDownRawY = rawY;
                mTouchStartX = getX() - rawX;
                mTouchStartY = getY() - rawY;
                return true;

            case MotionEvent.ACTION_MOVE:
                setX(rawX + mTouchStartX);
                setY(rawY + mTouchStartY);
                return true;

            case MotionEvent.ACTION_UP:
                long duration = System.currentTimeMillis() - mDownTime;
                float dx = Math.abs(rawX - mDownRawX);
                float dy = Math.abs(rawY - mDownRawY);
                if (duration < CLICK_THRESHOLD_MS && dx < MOVE_THRESHOLD_PX && dy < MOVE_THRESHOLD_PX) {
                    performClick();
                } else {
                    autoSnapToEdge();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        Activity activity = findActivity(getContext());
        if (activity != null) {
            com.xq.dialoglogshow.ShowLog.show(activity);
        }
        return true;
    }

    @Nullable
    private static Activity findActivity(Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private void autoSnapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float currentX = getX();
        float targetX = (currentX + getWidth() / 2f < screenWidth / 2f) ? dp2px(getContext(), 8) : (screenWidth - getWidth() - dp2px(getContext(), 8));
        animate().x(targetX).setDuration(200).start();
    }

    private static int dp2px(@NonNull Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
