package com.xq.dialoglogshow.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import com.xq.dialoglogshow.utils.SizeUtils;


/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 日志弹框入口
 */
public class ShowEntranceFullView extends View {
    public ShowEntranceFullView(Context context) {
        super(context);
        initData(context);
    }

    public ShowEntranceFullView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initData(context);
    }

    public ShowEntranceFullView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initData(context);
    }

    public ShowEntranceFullView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initData(context);
    }

    Paint paintRed = new Paint();
    Paint paint = new Paint();
    Paint paintFill = new Paint();
    RectF rectf = new RectF();

    AnimatorSet animator = null;

    public int sizeWidth;
    public int sizeHeight;

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

    private void initData(Context context) {
        sizeWidth = SizeUtils.dpToPx(context, 50);
        sizeHeight = SizeUtils.dpToPx(context, 50);

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
        paintFill.setColor(Color.parseColor("#aaFFFFFF"));
        paintFill.setStrokeWidth(SizeUtils.dpToPx(context, 1F));


        post(new Runnable() {
            @Override
            public void run() {
                try {
                    leftOld = getLeft();
                    topOld = getTop();
                    parWidth = ((View) getParent()).getWidth() - sizeWidth;
                    parHeight = ((View) getParent()).getHeight() - sizeWidth;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        animator = new AnimatorSet();

        ObjectAnimator red = ObjectAnimator.ofFloat(this, "startRed", 0F, 360F);
        red.setDuration(1200);
        red.setRepeatCount(ObjectAnimator.INFINITE);
        red.setRepeatMode(ObjectAnimator.RESTART);
        red.setInterpolator(new LinearInterpolator());


        ObjectAnimator fff = ObjectAnimator.ofFloat(this, "ffRed", 0F, sizeWidth * 0.25F);
        fff.setDuration(1200);
        fff.setRepeatCount(ObjectAnimator.INFINITE);
        fff.setRepeatMode(ObjectAnimator.REVERSE);
        fff.setInterpolator(new LinearInterpolator());


        ObjectAnimator rasize = ObjectAnimator.ofFloat(this, "redSize", 10F, 90F);
        rasize.setDuration(1200);
        rasize.setRepeatCount(ObjectAnimator.INFINITE);
        rasize.setRepeatMode(ObjectAnimator.REVERSE);
        rasize.setInterpolator(new LinearInterpolator());
        animator.playTogether(red, fff, rasize);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(sizeWidth, sizeHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int top = SizeUtils.dpToPx(getContext().getApplicationContext(), 5);
        rectf.set(top, top,
                w - top, h - top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(getWidth() * 0.5F, getHeight() * 0.5F, getHeight() * 0.5F, paintRed);
        canvas.drawArc(rectf, startRed, redSize, false, paint);
        canvas.drawArc(rectf, startRed + 180, redSize, false, paint);
        canvas.drawCircle(getWidth() * 0.5F, getHeight() * 0.5F, ffRed, paintFill);
    }


    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            animator.start();
        } else {
            animator.end();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.end();
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
