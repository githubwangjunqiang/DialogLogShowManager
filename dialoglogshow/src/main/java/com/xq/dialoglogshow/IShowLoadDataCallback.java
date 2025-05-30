package com.xq.dialoglogshow;

import android.app.Dialog;
import android.view.View;
import android.widget.FrameLayout;

import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.LogConfigData;
import com.xq.dialoglogshow.entity.PushData;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description: 获取数据的回调
 */
public interface IShowLoadDataCallback {

    /**
     * 获取网络日志
     *
     * @param startTime
     * @param endTime
     * @return
     */
    ArrayList<HttpLogData> loadHttpLog(long startTime, long endTime);

    /**
     * 获取个人信息
     *
     * @return
     */
    ArrayList<BaseShowData> loadUserInfo();

    /**
     * 获取本地key-value
     *
     * @return
     */
    ArrayList<BaseShowData> loadKeyValue();

    /**
     * 获取本地 PUSH
     *
     * @return
     */
    ArrayList<PushData> loadPush();

    /**
     * 获取全部缓存日志文件数据
     *
     * @return 文件路径
     */
    String loadAllData();
    /**
     * 获取其他数据
     *
     * @return 文件路径
     */
    ArrayList<BaseShowData> loadRestData();
    /**
     * 给开发者 自定义一tab
     *
     * @return 文件路径
     */
    void setCustomView(FrameLayout frameLayout, Dialog dialog);
    /**
     * 获取配置
     *
     */
    LogConfigData loadConfig();

    /**
     * 删除一个数据
     * @param data
     */
    boolean deleteHttpLog(BaseShowData data);
    /**
     * 删除一个数据
     */
    boolean deleteHttpLogAll();



}
