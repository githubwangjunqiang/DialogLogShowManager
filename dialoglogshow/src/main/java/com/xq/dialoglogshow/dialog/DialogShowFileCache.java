package com.xq.dialoglogshow.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.adapter.MyAdapter;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.utils.ShowTask;

/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志显示
 */
public class DialogShowFileCache extends Dialog {
    /**
     * 适配器
     */
    private MyAdapter mMyAdapter;
    private RecyclerView mRecyclerView;
    private ShowTask mAsyncTask;


    public DialogShowFileCache(Context context) {
        super(context, R.style.dialog_httplog);
        setContentView(R.layout.show_log_sdk_cache_log_layout);
        initView();
        setListener();

    }

    /**
     * 初始化
     */
    private void initView() {
        mRecyclerView = findViewById(R.id.show_app_dialog_cache_file_log_review);

        mMyAdapter = new MyAdapter(getContext(), mRecyclerView);
        mMyAdapter.mClickAdapter = new MyAdapter.ClickAdapter() {
            @Override
            public void clickDeleteHttpLog(BaseShowData data, int position) {
                boolean b = ShowLogManager.getCallback().deleteHttpLog(data);
                if (!b) {
                    Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                mMyAdapter.removedHttpData(data, position);

            }
        };
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.addItemDecoration(new MyItemDecoration(getContext()));
        mRecyclerView.setAdapter(mMyAdapter);


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
