package com.xq.dialoglogshow.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class MyItemDecoration extends RecyclerView.ItemDecoration{
    private Context mContext;
    private Paint mPaint = new Paint();

    public MyItemDecoration(Context context) {
        mContext = context;
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#8BDC61"));
        mPaint.setStrokeWidth(SizeUtils.dpToPx(context, 1F));
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        super.onDraw(c, parent, state);

        int childCount = parent.getChildCount();
        Rect mBounds = new Rect();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            parent.getDecoratedBoundsWithMargins(child, mBounds);
            int bottom = mBounds.bottom + Math.round(child.getTranslationY());
            int i1 = SizeUtils.dpToPx(mContext, 0.5F);
            int i2 = SizeUtils.dpToPx(mContext, 50F);
            c.drawLine(i2, bottom - i1, parent.getWidth() - i2, bottom - i1, mPaint);
        }
    }


    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.bottom = SizeUtils.dpToPx(mContext, 1F);
    }
}
