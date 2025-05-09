package com.xq.dialoglogshow.adapter.cache_log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class MyAdapterCacheFile extends RecyclerView.Adapter<CacheViewHolder> implements View.OnClickListener, View.OnLongClickListener {

    public static class FileData {
        String path;
        String name;
        boolean doc;
        int hierarchy = 0;
    }

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LayoutInflater mLayoutInflater;
    private List<FileData> mList = new ArrayList<>();


    public List<FileData> getListNodeData() {
        return mList;
    }

    public void setList(List<FileData> list) {
        mList.clear();
        if (list != null) {
            mList.addAll(list);
        }
        if (mList.isEmpty()) {
            Toast.makeText(mContext.getApplicationContext(), "无数据", Toast.LENGTH_SHORT).show();
        }
        notifyDataSetChanged();
    }

    public MyAdapterCacheFile(Context context, RecyclerView recyclerView) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mRecyclerView = recyclerView;
    }

    @Override
    public CacheViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        return new CacheViewHolder(mLayoutInflater.inflate(R.layout.show_log_sdk_layout_item_cache_log, parent, false));
    }

    @Override
    public void onBindViewHolder(CacheViewHolder holder, int position) {
        int layoutPosition = holder.getLayoutPosition();
    }


    @Override
    public int getItemCount() {
        return mList.size();
    }


    @Override
    public void onClick(View v) {
        RecyclerView.ViewHolder containingViewHolder = mRecyclerView.findContainingViewHolder(v);

    }

    @Override
    public boolean onLongClick(View v) {

        RecyclerView.ViewHolder containingViewHolder = mRecyclerView.findContainingViewHolder(v);

        return true;
    }
}

