package com.xq.dialoglogshow.adapter.cache_log;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/5/9
 */
public class CacheViewHolder extends RecyclerView.ViewHolder {

    public TextView mTvContent;
    public ImageView mIvIcon;

    public CacheViewHolder(@NonNull View itemView) {
        super(itemView);
        mTvContent = itemView.findViewById(R.id.tvname);
        mIvIcon = itemView.findViewById(R.id.show_app_dialog_cache_file_log_docicon);
    }


    public void setData(MyAdapterCacheFileAdapter.FileData fileData) {

        mTvContent.setText(fileData.name);
        mIvIcon.setSelected(!fileData.doc);

    }
}
