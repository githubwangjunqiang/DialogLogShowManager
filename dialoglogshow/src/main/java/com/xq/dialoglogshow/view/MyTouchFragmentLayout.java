package com.xq.dialoglogshow.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/9/23
 */
@Keep
public class MyTouchFragmentLayout extends FrameLayout {


    public MyTouchFragmentLayout(@NonNull Context context) {
        super(context);
    }

    public MyTouchFragmentLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyTouchFragmentLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyTouchFragmentLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {

            setScaleX(0.7f);
            setScaleY(0.7f);

        } else {
            setScaleX(1f);
            setScaleY(1f);
        }
        return super.dispatchTouchEvent(event);
    }

}
