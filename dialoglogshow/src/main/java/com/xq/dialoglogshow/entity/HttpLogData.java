package com.xq.dialoglogshow.entity;

import java.util.List;

/**
 * Created by Android-小强 on 2023/1/13.
 * mailbox:980766134@qq.com
 * description: http日志专用
 */
public class HttpLogData extends BaseShowData {

    public HttpLogData(String url, long time, String content, String resMsg, List<BaseShowData> list, boolean expansion, int index) {
        this.url = url;
        this.time = time;
        this.resMsg = resMsg;
        mList = list;
        this.expansion = expansion;
        this.index = index;
        this.content = content;
    }



}
