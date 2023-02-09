package com.xq.dialoglogshow.manager;

import android.app.Application;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.utils.ShowLogActivityUtils;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description: 管理器
 */
public class ShowLogManager implements IShowLoadDataCallback, IShowLogManager {
    /**
     * 数据回调 接口
     */
    private volatile IShowLoadDataCallback showLoadDataCallback;
    /**
     * 外部传入的线程池
     */
    private volatile Executor executor;


    private ShowLogManager() {
    }

    public static IShowLogManager getInstance() {
        return Holder.showLogManager;
    }

    @Override
    public void setDataCallback(IShowLoadDataCallback iShowLoadDataCallback) {
        showLoadDataCallback = iShowLoadDataCallback;
    }

    @Override
    public void start(Application application, Executor executor) {
        ShowLogActivityUtils.getInstance().open = true;
        if (application == null) {
            throw new NullPointerException("application为空");
        }
        if (executor == null) {
            throw new NullPointerException("线程池为空");
        }
        try {
            application.unregisterActivityLifecycleCallbacks(ShowLogActivityUtils.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            application.registerActivityLifecycleCallbacks(ShowLogActivityUtils.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.executor = executor;
    }

    @Override
    public void stop() {
        ShowLogActivityUtils.getInstance().open = false;
    }

    @Override
    public Executor loadExecutor() {
        return executor;
    }


    @Override
    public ArrayList<HttpLogData> loadHttpLog(long startTime, long endTime) {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadHttpLog(startTime, endTime);
        }
        return null;
    }

    @Override
    public ArrayList<BaseShowData> loadUserInfo() {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadUserInfo();
        }
        return null;
    }

    @Override
    public ArrayList<BaseShowData> loadKeyValue() {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadKeyValue();
        }
        return null;
    }

    @Override
    public ArrayList<PushData> loadPush() {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadPush();
        }
        return null;
    }

    @Override
    public String loadAllData() {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadAllData();
        }
        return null;
    }

    @Override
    public ArrayList<BaseShowData> loadRestData() {
        if (showLoadDataCallback != null) {
            return showLoadDataCallback.loadRestData();
        }
        return null;
    }

    private static class Holder {
        private static volatile ShowLogManager showLogManager = new ShowLogManager();
    }


}
