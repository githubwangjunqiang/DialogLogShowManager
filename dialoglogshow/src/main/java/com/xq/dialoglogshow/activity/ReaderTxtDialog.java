package com.xq.dialoglogshow.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.adapter.ReaderTXTAdapter;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.LogConfigData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.PagedTextLoader;
import com.xq.dialoglogshow.utils.ShowTask;
import com.xq.dialoglogshow.utils.SizeUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/6/3
 */
public class ReaderTxtDialog extends Dialog {


    public ReaderTxtDialog(@NonNull Context context, @NonNull String filePath) {
        super(context, R.style.dialog_httplog);
        this.filePath = filePath;
    }

    private String filePath;
    private TextView mTvTitle;
    private TextView back;
    private TextView mShare;
    private RecyclerView mRecyclerView;
    private ReaderTXTAdapter mReaderTXTAdapter;

    private PagedTextLoader mPagedTextLoader;
    private ShowTask<List<String>> mShowTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_log_sdk_cache_log_dialog_reader_layout);
        setCanceledOnTouchOutside(false);
        setCancelable(true);
        back = findViewById(R.id.back);
        mTvTitle = findViewById(R.id.title);
        mShare = findViewById(R.id.share);
        mTvTitle.setText(new File(filePath).getName());
        mRecyclerView = findViewById(R.id.show_app_dialog_cache_file_log_review);


        mReaderTXTAdapter = new ReaderTXTAdapter(getContext(), mRecyclerView, new ReaderTXTAdapter.OnLoadMore() {
            @Override
            public void loadMore() {
                loadTxt();
            }
        });
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mReaderTXTAdapter);
        mPagedTextLoader = new PagedTextLoader(new File(filePath), 300);
        mReaderTXTAdapter.addList(new ArrayList<>());
        loadTxt();


        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        mShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LogConfigData.ReadTextCall readTextCall = ShowLogManager.getCallback().loadConfig().readTextCall;
                if (readTextCall != null) {
                    readTextCall.readText(filePath);
                }
            }
        });

    }

    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes != null) {
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = SizeUtils.getScreenHeight(getContext());
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);

    }


    @SuppressLint("StaticFieldLeak")
    private void loadTxt() {

        if (mShowTask != null) {
            mShowTask.cancel(true);
        }
        mShowTask = new ShowTask<List<String>>() {
            @Override
            protected void postMainData(List<String> list) {
                mReaderTXTAdapter.addList(list);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected List<String> doInBackground(Object[] objects) {

                try {
                    if (mPagedTextLoader.hasMore()) {
                        List<String> strings = mPagedTextLoader.loadNextPage();


                        return strings;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (isCancelled()) {
                    return null;
                }
                return null;
            }
        };
        mShowTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());

    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (mShowTask != null) {
            mShowTask.cancel(true);
        }
    }
}
