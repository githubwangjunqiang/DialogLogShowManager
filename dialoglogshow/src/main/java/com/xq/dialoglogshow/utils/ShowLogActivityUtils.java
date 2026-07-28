package com.xq.dialoglogshow.utils;

import android.app.Activity;
import android.app.Application;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.RectF;

import com.xq.dialoglogshow.collector.vpn.VpnHealthMonitor;
import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.dialog.DialogShowLog;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.view.ShowEntranceFullView;
import com.xq.dialoglogshow.view.ShowEntranceFullViewVerSion;


/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志 显示相关管理工具
 */
public class ShowLogActivityUtils implements Application.ActivityLifecycleCallbacks {
    /**
     * 为什么这样做：VPN 自动恢复要求识别“真正进入前台/后台”的瞬间，单个 Activity 的 resume/pause 不能可靠代表整个应用态。
     */
    private int startedActivityCount = 0;

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
        startedActivityCount++;
        if (startedActivityCount == 1) {
            VpnHealthMonitor.getInstance().onAppForeground(activity.getApplicationContext());
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        initView(activity);
    }

    @Override
    public void onActivityPostResumed(@NonNull Activity activity) {
        Application.ActivityLifecycleCallbacks.super.onActivityPostResumed(activity);
//        initView(activity);
    }

    private void initView(Activity activity) {

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
        startedActivityCount = Math.max(0, startedActivityCount - 1);
        if (startedActivityCount == 0) {
            VpnHealthMonitor.getInstance().onAppBackground(activity.getApplicationContext());
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    private static View loadFullView(Activity activity, View root) {
        View fullView;
        ShowLogProvider provider = ShowLogManager.getInstance().getProvider();
        View customView = provider.getCustomEntranceView(activity);
        if (customView != null) {
            fullView = customView;
        } else if (provider.isShowVersionCodeView()) {
            fullView = new ShowEntranceFullViewVerSion(activity);
        } else {
            fullView = new ShowEntranceFullView(activity);
        }

        fullView.setElevation(20);
        fullView.setTag(viewTag);
        fullView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DialogShowLog(activity).show();
            }
        });
        if (root instanceof FrameLayout) {
            FrameLayout.LayoutParams layoutParams =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
            RectF debugViewSize = provider.getDebugViewSize();
            if (debugViewSize != null) {
                layoutParams.topMargin = (int) debugViewSize.top;
                layoutParams.leftMargin = (int) debugViewSize.left;
            } else {
                layoutParams.topMargin = root.getHeight() / 2;
                layoutParams.leftMargin = 10;
            }
            ((FrameLayout) root).addView(fullView, layoutParams);
        }

        return fullView;
    }
}
