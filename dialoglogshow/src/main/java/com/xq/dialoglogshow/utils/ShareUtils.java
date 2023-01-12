package com.xq.dialoglogshow.utils;

import android.content.Context;
import android.content.Intent;

/**
 * Created by Android-小强 on 2023/1/11.
 * mailbox:980766134@qq.com
 * description:
 */
public class ShareUtils {
    /**
     * 分享文本
     */
    public static void shareText(Context context, String shareText) {
        Intent apply = new Intent(Intent.ACTION_SEND);
        apply.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        apply.putExtra(Intent.EXTRA_TEXT, shareText);
        apply.setType("text/*");
        try {
            Intent chooser = Intent.createChooser(apply, "share-test");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
