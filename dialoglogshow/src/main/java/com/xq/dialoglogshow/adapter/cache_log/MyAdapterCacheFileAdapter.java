package com.xq.dialoglogshow.adapter.cache_log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Android-小强 on 2023/1/10.
 * mailbox:980766134@qq.com
 * description:
 */
public class MyAdapterCacheFileAdapter extends RecyclerView.Adapter<CacheViewHolder> implements View.OnClickListener {

    public static class FileData {
        public String path;
        public String name;
        public boolean doc;
    }

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LayoutInflater mLayoutInflater;
    private List<FileData> mList = new ArrayList<>();


    public void setPath(String path) {
        mList.clear();
        File file = new File(path);

        if (file.isDirectory()) {
            for (File listFile : file.listFiles()) {
                FileData fileData = new FileData();
                fileData.doc = listFile.isDirectory();
                fileData.path = listFile.getAbsolutePath();
                fileData.name = listFile.getName();
                mList.add(fileData);
            }
        }

        notifyDataSetChanged();


    }


    private void setList(List<FileData> list) {
        mList.clear();
        if (list != null) {
            mList.addAll(list);
        }
        if (mList.isEmpty()) {
            Toast.makeText(mContext.getApplicationContext(), "无数据", Toast.LENGTH_SHORT).show();
        }
        notifyDataSetChanged();
    }

    public interface ClickItem {
        void clickItem(FileData data, int postion);
    }

    private ClickItem clickItem;

    public MyAdapterCacheFileAdapter(Context context, RecyclerView recyclerView, ClickItem clickItem) {
        mContext = context;
        this.clickItem = clickItem;
        mLayoutInflater = LayoutInflater.from(context);
        mRecyclerView = recyclerView;
    }

    @Override
    public CacheViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        return new CacheViewHolder(mLayoutInflater.inflate(R.layout.show_log_sdk_layout_item_cache_log, parent, false));
    }

    @Override
    public void onBindViewHolder(CacheViewHolder holder, int position) {
        holder.itemView.setOnClickListener(this);
        holder.setData(mList.get(holder.getLayoutPosition()));

    }


    @Override
    public int getItemCount() {
        return mList.size();
    }


    @Override
    public void onClick(View v) {
        RecyclerView.ViewHolder containingViewHolder = mRecyclerView.findContainingViewHolder(v);
        assert containingViewHolder != null;
        int layoutPosition = containingViewHolder.getLayoutPosition();
        clickItem.clickItem(mList.get(layoutPosition), layoutPosition);
    }

}

