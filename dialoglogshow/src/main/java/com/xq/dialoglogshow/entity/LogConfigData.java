package com.xq.dialoglogshow.entity;

import android.content.Context;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Keep;

import java.lang.reflect.Array;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2024/12/24
 */
@Keep
public class LogConfigData {

    @Keep
    public boolean sortUrlForTime = false;
    /**
     * 控制默认 log视图的宽高，只对默认视图生效，如果是 iLogShowView/showVersionCodeView 这两个不生效
     */
    @Keep
    public RectF debugViewSize = null;
    /**
     * 缓存文件路径 根路径
     */
    @Keep
    public String cacheDocPath = null;
    /**
     * 文本文件 由外部打开 内部不打开
     */
    @Keep
    public ReadTextCall readTextCall = null;
    /**
     * 获取view 外部实现
     */
    @Keep
    public ILogShowView iLogShowView = null;
    /**
     *是否显示 versionCode的logview 这个优先级比 iLogShowView 低
     */
    @Keep
    public boolean showVersionCodeView = false;
    /**
     *如果显示版本号 由外部传入
     */
    @Keep
    public String showVersionCodeText = null;

    @Keep
    public interface ReadTextCall{
        void readText(String path);
    }
    @Keep
    public interface ILogShowView{
        /**
         * 获取view 外部实现
         * @param context
         * @return
         */
        View getView(Context context);
    }
}
