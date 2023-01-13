package com.xq.dialoglogshow.entity;

/**
 * Created by Android-小强 on 2023/1/13.
 * mailbox:980766134@qq.com
 * description: 推送信息
 */
public class PushData extends BaseShowData {

    public PushData(long time, String content) {
        this.time = time;
        this.content = content;
    }
}
