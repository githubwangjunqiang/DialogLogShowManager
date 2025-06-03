package com.xq.dialoglogshow.manager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.xq.dialoglogshow.IShowLoadDataCallback;

import java.util.concurrent.Executor;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public interface IShowLogManager {
    /**
     * 设置回调
     *
     * @param iShowLoadDataCallback
     */
    void setDataCallback(IShowLoadDataCallback iShowLoadDataCallback);

    /**
     * 开始
     *
     * @param application
     */
    void start(Application application, Executor executor);

    /**
     * 开始
     *
     * @param application
     * @param executor
     * @param showAnimation
     */
    void start(Application application, Executor executor, boolean showAnimation);

    /**
     * 关闭
     */
    void stop();

    /**
     * 获取线程池
     *
     * @return
     */
    Executor loadExecutor();

    /**
     * 是否显示动画
     *
     * @return
     */
    boolean loadShowAnimation();

    /**
     * path
     *
     * @param path
     */
    void readTxtForDialog(Context activity, String path);


}
