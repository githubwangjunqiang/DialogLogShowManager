package com.xq.dialoglogshow.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.activity.ReaderTxtDialog;
import com.xq.dialoglogshow.adapter.MyAdapter;
import com.xq.dialoglogshow.adapter.cache_log.MyAdapterCacheFileAdapter;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.utils.ShareUtils;
import com.xq.dialoglogshow.utils.ShowTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志显示
 */
public class DialogShowFileCache extends Dialog {
    /**
     * 适配器
     */
    private MyAdapterCacheFileAdapter mMyAdapter;
    private RecyclerView mRecyclerView;
    private TextView mTvTitle;
    private View mBackPath;

    private String rootPath;
    private LinkedList<String> linkedList = new LinkedList<>();

    public DialogShowFileCache(Context context, String rootPath) {
        super(context, R.style.dialog_httplog);
        this.rootPath = rootPath;
        linkedList.add(rootPath);
        setContentView(R.layout.show_log_sdk_cache_log_layout);
        initView();
        setListener();

    }

    public void setTitle() {
        StringBuilder stringBuilder = new StringBuilder();
        String empty = "    ";
        for (int i = 0; i < linkedList.size(); i++) {
            String s = linkedList.get(i);
            File file = new File(s);
            for (int j = 0; j < i; j++) {
                stringBuilder.append("\n").append(empty).append(empty);
            }
            stringBuilder.append("📁 " + file.getName());
        }
        mTvTitle.setText(stringBuilder.toString());
    }

    /**
     * 初始化
     */
    private void initView() {
        mBackPath = findViewById(R.id.show_app_dialog_cache_file_log_tv_back);
        mBackPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (linkedList.size() > 1) {
                    linkedList.removeLast();
                    setTitle();
                    mMyAdapter.setPath(linkedList.getLast());
                }
            }
        });
        mTvTitle = findViewById(R.id.show_app_dialog_cache_file_log_tv_title);
        setTitle();

        mRecyclerView = findViewById(R.id.show_app_dialog_cache_file_log_review);

        mMyAdapter = new MyAdapterCacheFileAdapter(getContext(), mRecyclerView, new MyAdapterCacheFileAdapter.ClickItem() {
            @Override
            public void clickItem(MyAdapterCacheFileAdapter.FileData data, int postion) {

                if (data.doc) {
                    addPath(data.path);
                } else {
                    // 文件：优先调用 Provider 响应事件
                    ShowLogManager.getInstance().getProvider().onOpenTextFile(data.path);
                    new ReaderTxtDialog(getContext(), data.path).show();
                }

            }
        });
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // 采用精致暗黑卡片自带边框与间距，移除旧版突兀的荧光绿分割线
        // mRecyclerView.addItemDecoration(new MyItemDecoration(getContext()));
        mRecyclerView.setAdapter(mMyAdapter);

        mMyAdapter.setPath(linkedList.getLast());
    }

    public void addPath(String path) {
        linkedList.add(path);
        setTitle();
        mMyAdapter.setPath(linkedList.getLast());
    }

    /**
     * 监听器：绑定顶部及底部关闭按钮事件，保证手指在屏下更好触达操作
     */
    private void setListener() {
        // 顶部关闭按钮
        findViewById(R.id.show_app_dialog_confirm_btnok).setOnClickListener(v -> cancel());
        
        // 底部新增的关闭按钮，便于单手触达
        View btnCloseBottom = findViewById(R.id.show_app_dialog_confirm_btnok_bottom);
        if (btnCloseBottom != null) {
            btnCloseBottom.setOnClickListener(v -> cancel());
        }
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
