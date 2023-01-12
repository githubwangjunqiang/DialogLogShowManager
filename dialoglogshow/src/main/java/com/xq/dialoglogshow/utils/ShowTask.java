package com.xq.dialoglogshow.utils;

import android.os.AsyncTask;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public abstract class ShowTask<T> extends AsyncTask<Object, Void, T> {
    @Override
    protected void onPostExecute(T t) {

        super.onPostExecute(t);
        try {
            postMainData(t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void postMainData(T t);
}
