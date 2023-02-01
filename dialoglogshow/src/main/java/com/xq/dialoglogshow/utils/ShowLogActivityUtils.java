package com.xq.dialoglogshow.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.dialog.DialogShowLog;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.view.ShowEntranceFullView;


/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志 显示相关管理工具
 */
public class ShowLogActivityUtils implements Application.ActivityLifecycleCallbacks {
    private ShowLogActivityUtils() {
    }

    /**
     * 是否开启和关闭
     */
    public volatile boolean open = false;
    private static String viewTag = "FullViewHttpLog";

    public static ShowLogActivityUtils getInstance() {
        return Holder.showLogManager;
    }

    private static class Holder {
        private static volatile ShowLogActivityUtils showLogManager = new ShowLogActivityUtils();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPostResumed(@NonNull Activity activity) {
        Application.ActivityLifecycleCallbacks.super.onActivityPostResumed(activity);
        try {
            View root = activity.findViewById(android.R.id.content);
            if (root != null) {
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (activity.isDestroyed() || activity.isFinishing()) {
                                return;
                            }

                            if (root instanceof FrameLayout) {
                                View viewWithTag = root.findViewWithTag(viewTag);
                                if (open) {
                                    if (viewWithTag == null) {
                                        loadFullView(activity, root);
                                    }
                                } else {
                                    if (viewWithTag != null) {
                                        ((FrameLayout) root).removeView(viewWithTag);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    private static View loadFullView(Activity activity, View root) {
        ShowEntranceFullView fullView = new ShowEntranceFullView(activity);
        fullView.setTag(viewTag);
        fullView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DialogShowLog(activity).show();
            }
        });
        if (root instanceof ViewGroup) {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.topMargin = root.getHeight() - fullView.sizeHeight;
            ((ViewGroup) root).addView(fullView, layoutParams);
        }

        return fullView;
    }
}
