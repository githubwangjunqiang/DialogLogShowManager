package com.xq.dialoglogshow.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.adapter.cache_log.MyAdapterCacheFileAdapter;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.view.BigTextView;

import java.io.File;
import java.util.LinkedList;

/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志显示
 */
public class DialogShowLogInfo extends Dialog {

    private String mContent = "";

    public DialogShowLogInfo(Context context, String content) {
        super(context, R.style.dialog_httplog);
        setContentView(R.layout.show_log_sdk_cache_log_info_layout);
        mContent = content;
        initView();
        setListener();

    }


    /**
     * 初始化
     */
    private void initView() {

        BigTextView textView = findViewById(R.id.show_app_dialog_cache_file_log_tv_title);

        textView.setMessage(mContent);

    }


    /**
     * 监听器
     */
    private void setListener() {
        //关闭
        findViewById(R.id.show_app_dialog_confirm_btnok).setOnClickListener(v -> cancel());

    }


    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes != null) {
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);


    }


}
