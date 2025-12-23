package com.xq.dialoglogshow.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.util.Pair;

import com.xq.dialoglogshow.entity.LogConfigData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.AppUtils;
import com.xq.dialoglogshow.utils.SizeUtils;


/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 日志弹框入口
 */
public class ShowEntranceFullViewVerSion extends View {
    public ShowEntranceFullViewVerSion(Context context) {
        super(context);
        initData(context);
    }

    public ShowEntranceFullViewVerSion(Context context, AttributeSet attrs) {
        super(context, attrs);
        initData(context);
    }

    public ShowEntranceFullViewVerSion(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initData(context);
    }

    public ShowEntranceFullViewVerSion(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initData(context);
    }

    Paint paintRed = new Paint();
    Paint paint = new Paint();
    Paint paintFill = new TextPaint();

    AnimatorSet animator = null;


    int leftOld = 0;
    int topOld = 0;

    private float startRed = 0F;

    public float getStartRed() {
        return startRed;
    }

    public void setStartRed(float startRed) {
        this.startRed = startRed;
        postInvalidate();
    }


    private float redSize = 0F;

    public float getRedSize() {
        return redSize;
    }

    public void setRedSize(float redSize) {
        this.redSize = redSize;
        postInvalidate();
    }

    private float ffRed = 0F;

    public float getFfRed() {
        return ffRed;
    }

    public void setFfRed(float ffRed) {
        this.ffRed = ffRed;
        postInvalidate();
    }


    int parWidth = 0;
    int parHeight = 0;
    int minWidth = 0;
    int minHeight = 0;

    private Paint.FontMetrics fontMetricsCurrent = new Paint.FontMetrics();
    private Path mPath = new Path();

    private void initData(Context context) {

        minWidth = SizeUtils.dpToPx(context, 40);
        minHeight = SizeUtils.dpToPx(context, 12);


        if (ShowLogManager.getCallback().loadConfig().showVersionCodeText != null) {
            AppVersionCode = ShowLogManager.getCallback().loadConfig().showVersionCodeText;
        } else {
            AppVersionCode = AppUtils.getAppVersionCode(context, context.getPackageName()) + "";
        }
        mHorizontalPadding = SizeUtils.dpToPx(context, 2);
        paintRed.isAntiAlias();
        paintRed.setStyle(Paint.Style.FILL);
        paintRed.setColor(Color.parseColor("#bbff0000"));
        paintRed.setStrokeWidth(SizeUtils.dpToPx(context, 1F));

        paint.isAntiAlias();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#ddFFFFFF"));
        paint.setStrokeWidth(SizeUtils.dpToPx(context, 1F));

        paintFill.isAntiAlias();
        paintFill.setStyle(Paint.Style.FILL);
        paintFill.setTextAlign(Paint.Align.CENTER);
        paintFill.setColor(Color.parseColor("#ddFFFFFF"));
        paintFill.setStrokeWidth(SizeUtils.dpToPx(context, 1F));
        paintFill.setTextSize(SizeUtils.dpToPx(context, 9));


        post(new Runnable() {
            @Override
            public void run() {
                try {
                    leftOld = getLeft();
                    topOld = getTop();
                    parWidth = ((View) getParent()).getWidth() - getWidth();
                    parHeight = ((View) getParent()).getHeight() - getHeight();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        animator = new AnimatorSet();

        ObjectAnimator red = ObjectAnimator.ofFloat(this, "startRed", 0F, 360f);
        red.setDuration(1200);
        red.setRepeatCount(ObjectAnimator.INFINITE);
        red.setRepeatMode(ObjectAnimator.RESTART);
        red.setInterpolator(new LinearInterpolator());


//        ObjectAnimator fff = ObjectAnimator.ofFloat(this, "ffRed", 0F, sizeWidth * 0.25F);
//        fff.setDuration(1200);
//        fff.setRepeatCount(ObjectAnimator.INFINITE);
//        fff.setRepeatMode(ObjectAnimator.REVERSE);
//        fff.setInterpolator(new LinearInterpolator());


        ObjectAnimator rasize = ObjectAnimator.ofFloat(this, "redSize", 10F, 90f);
        rasize.setDuration(1200);
        rasize.setRepeatCount(ObjectAnimator.INFINITE);
        rasize.setRepeatMode(ObjectAnimator.REVERSE);
        rasize.setInterpolator(new LinearInterpolator());
        animator.playTogether(red, rasize);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float textWidth = paintFill.measureText(AppVersionCode);
        float v = textWidth + mHorizontalPadding * 2;
        paintFill.getFontMetrics(fontMetricsCurrent);
        float textHeight = Math.abs(fontMetricsCurrent.leading) +
                Math.abs(fontMetricsCurrent.ascent) +
                Math.abs(fontMetricsCurrent.descent) + mHorizontalPadding * 2;


        setMeasuredDimension(Math.max((int) v, minWidth), Math.max((int) textHeight, minHeight));
    }

    private RectF mRectF = new RectF();
    private RectF mRectF2 = new RectF();
    private RectF mRectFBack = new RectF();

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRectF.set(0f, 0f, w, h);
        mRectF2.set(0f, 0f, w, h);
        mRectFBack.set(0f, 0f, w, h);
        mRectF2.inset(mHorizontalPadding, mHorizontalPadding);
        mRectFBack.inset(-mHorizontalPadding * 10, -mHorizontalPadding * 10);
        mPath.reset();
        mPath.addRoundRect(mRectF2, mHorizontalPadding, mHorizontalPadding, Path.Direction.CCW);
    }

    private static String AppVersionCode = "";
    private float mHorizontalPadding; // 左右2dp边距

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(mRectF, mHorizontalPadding, mHorizontalPadding, paintRed);

        canvas.save();
        mPath.rewind();
        mPath.moveTo(getWidth() * 0.5f, getHeight() * 0.5f);
        mPath.arcTo(mRectFBack, startRed, redSize, false);
        mPath.close();
        canvas.clipPath(mPath);
        canvas.drawRoundRect(mRectF2, mHorizontalPadding, mHorizontalPadding, paint);
        canvas.restore();


//        canvas.drawArc(mRectF, startRed, redSize, false, paint);
//        canvas.drawArc(mRectF, startRed + 180, redSize, false, paint);


        float textHeight = Math.abs(fontMetricsCurrent.leading) +
                Math.abs(fontMetricsCurrent.ascent) +
                Math.abs(fontMetricsCurrent.descent);
        float v = textHeight * 0.5f - Math.abs(fontMetricsCurrent.descent);
        canvas.drawText(AppVersionCode, getWidth() * 0.5F, getHeight() * 0.5F + v, paintFill);

    }


    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (ShowLogManager.getInstance().loadShowAnimation()) {
            try {
                if (visibility == View.VISIBLE) {
                    animator.start();
                } else {
                    animator.end();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            animator.end();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    float downX;
    float downY;
    float oldX;
    float oldY;
    boolean hasItMoved;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
//            return super.onTouchEvent(event);

            downX = event.getRawX();
            downY = event.getRawY();

            oldX = getX();
            oldY = getY();
            hasItMoved = false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
//            return super.onTouchEvent(event);

            float rawX = event.getRawX();
            float rawY = event.getRawY();

            float v = rawX - downX;
            float v1 = rawY - downY;
            setX(Math.min(Math.max(oldX + v, 0), ((View) getParent()).getWidth() - getWidth()));
            setY(Math.min(Math.max(oldY + v1, 0), ((View) getParent()).getHeight() - getHeight()));
            if (Math.abs(v) > 10 || Math.abs(v1) > 10) {
                hasItMoved = true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
//            return super.onTouchEvent(event);
            if (hasItMoved) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
