package com.xq.dialoglogshow.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.xq.dialoglogshow.utils.SizeUtils;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class BigTextView extends View {
    public BigTextView(Context context) {
        super(context);
        initData(context);
    }


    public BigTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initData(context);
    }

    public BigTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initData(context);
    }

    public BigTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initData(context);
    }

    private TextPaint tp = new TextPaint();

    private int heightSize = 0;
    private StaticLayout myStaticLayout;


    private void initData(Context context) {
        tp.setColor(Color.parseColor("#444444"));
        tp.setStyle(Paint.Style.FILL);
        tp.setTextSize(SizeUtils.dpToPx(context, 12F));
    }


    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        requestLayout();
        postInvalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int size = SizeUtils.getScreenWidth(getContext());
        if (mode == MeasureSpec.EXACTLY) {
            size = MeasureSpec.getSize(widthMeasureSpec);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            myStaticLayout = StaticLayout
                    .Builder.obtain(message, 0, message.length(), tp, size).build();
        } else {
            myStaticLayout = new StaticLayout(message, tp, size,
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false);
        }

        heightSize = myStaticLayout.getHeight();

        int makeMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, makeMeasureSpec);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        myStaticLayout.draw(canvas);
        canvas.restore();
    }


}
