package com.xq.dialoglogshow.entity;

import java.util.List;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class BaseShowData {
    //普通类型
    public static final int TYE_ONE = 0;
    // 父亲类型
    public static final int TYE_TOW = 1;
    // 孩子类型
    public static final int TYE_THREE = 2;

    /**
     * item 类型
     */
    private int itemType = 0;
    /**
     * 数据
     */
    private String content;
    /**
     * 网络链接
     */
    private String url;
    /**
     * 时间戳
     */
    private long time;
    /**
     * 结果信息 简述
     */
    private String resMsg;

    /**
     * 子类
     */
    private List<BaseShowData> mList;

    /**
     * 是否已经展开
     */
    private boolean expansion;

    /**
     * 子类使用的下标
     */
    private int index;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isExpansion() {
        return expansion;
    }

    public void setExpansion(boolean expansion) {
        this.expansion = expansion;
    }

    public String getResMsg() {
        return resMsg;
    }

    public void setResMsg(String resMsg) {
        this.resMsg = resMsg;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }


    public int getItemType() {
        return itemType;
    }

    public void setItemType(int itemType) {
        this.itemType = itemType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<BaseShowData> getList() {
        return mList;
    }

    public void setList(List<BaseShowData> list) {
        mList = list;
    }
}
