package com.xq.dialoglogshow.dialog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.utils.ClipboardUtils;
import com.xq.dialoglogshow.utils.ShareUtils;
import com.xq.dialoglogshow.utils.SizeUtils;
import com.xq.dialoglogshow.view.BigTextView;

/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志显示
 */
public class DialogShowLogInfo extends Dialog {

    private String mContent = "";
    private View itemView;

    private View rootView;
    private View itemRoot;

    public DialogShowLogInfo(Context context, String content, View itemView, View itemRoot) {
        super(context, R.style.dialog_httplog);
        setContentView(R.layout.show_log_sdk_cache_log_info_layout);
        mContent = content;
        this.itemView = itemView;
        this.itemRoot = itemRoot;
        initView();
        setListener();
        setCanceledOnTouchOutside(true);


    }


    /**
     * 初始化
     */
    private void initView() {

        rootView = findViewById(R.id.show_app_dialog_log_info_root);
        BigTextView textView = findViewById(R.id.show_app_dialog_cache_file_log_tv_title);

        textView.setMessage(mContent);

        textView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                findViewById(R.id.show_app_dialog_confirm_btnshare).performClick();
                return true;
            }
        });
    }


    /**
     * 监听器
     */
    private void setListener() {
        rootView.postDelayed(new Runnable() {
            @Override
            public void run() {
                //关闭
                findViewById(R.id.show_app_dialog_confirm_btnok).setOnClickListener(v -> cancel());
                findViewById(R.id.show_app_dialog_confirm_btnok2).setOnClickListener(v -> cancel());
                //分享
                findViewById(R.id.show_app_dialog_confirm_btnshare).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ClipboardUtils.copyText(mContent, view.getContext().getApplicationContext());
                        ShareUtils.shareText(view.getContext().getApplicationContext(), mContent);
                    }
                });
            }
        }, 0);


        setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                endItem(itemView);
            }
        });

    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
//        endAn();
    }

    @Override
    public void show() {
        super.show();

        screenHeight = SizeUtils.getScreenHeight(getContext().getApplicationContext());
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes != null) {
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = (int) (screenHeight * 0.8f);
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);


//        startAn(screenHeight);


    }

    Animator circularRevealEnd;

    private void endAn() {
        if (circularRevealEnd != null || !isShowing()) {
            return;
        }
        float paddtop = outLocation[1] * 1.0f + itemPaddingTop + heightitemView / 2 - screenHeight * 0.1f;
        int widthRoot = rootView.getWidth();
        int height = rootView.getHeight();
        circularRevealEnd = ViewAnimationUtils.createCircularReveal(rootView,
                widthRoot / 2,
                (int) (paddtop),
                height,
                0f);
        circularRevealEnd.setDuration(800);
        circularRevealEnd.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                endItem(itemView);

                cancel();
            }
        });
        circularRevealEnd.start();

    }

    public static void endItem(View itemView) {
        itemView.setElevation(10);
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(itemView, View.SCALE_Y,
                2f, 1.7f, 1.2f,0.9f, 1.05f, 1f);
        objectAnimator.setDuration(500);
        objectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator valueAnimator) {
                Float animatedValue = (Float) valueAnimator.getAnimatedValue();
                itemView.setScaleX(animatedValue);
            }
        });
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                itemView.setElevation(0);
            }
        });
        objectAnimator.start();

    }

    int screenHeight;
    int[] outLocation = new int[2];
    int itemPaddingTop;
    int heightitemView;

    private void startAn(int screenHeight) {
        int width = itemView.getWidth();
        heightitemView = itemView.getHeight();
        itemView.getLocationInWindow(outLocation);

        int heightItemRoot = itemRoot.getHeight();
        itemPaddingTop = (screenHeight - heightItemRoot) / 2;

        Log.d("12345", "itemView-x:" + outLocation[0] + ",y:" + outLocation[1] + ",r:" + width + ",b:" + heightitemView);

        rootView.post(new Runnable() {
            @Override
            public void run() {
                int[] intLocation = new int[2];
                int widthRoot = rootView.getWidth();
                int heightRoot = rootView.getHeight();
                rootView.getLocationInWindow(intLocation);

                Log.d("12345", "rootView-x:" + intLocation[0] + ",y:" + intLocation[1] + ",r:" + widthRoot + ",b:" + heightRoot);

                float paddtop = outLocation[1] * 1.0f + itemPaddingTop + heightitemView / 2 - screenHeight * 0.1f;

                Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootView,
                        widthRoot / 2,
                        (int) (paddtop),
                        0f,
                        heightRoot);
                circularReveal.setDuration(800);
                circularReveal.start();

            }
        });
    }


}
