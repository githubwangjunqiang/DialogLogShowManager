package com.xq.dialoglogshow.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.utils.SizeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/6/3
 */
public class ReaderTXTAdapter extends RecyclerView.Adapter<ReaderTXTAdapter.ReaderVh> {

    Context context;
    RecyclerView recyclerView;

    private String sError = "_Error";
    private String sLoading = "_Loading";
    private OnLoadMore mOnLoadMore;

    private ArrayList<String> list = new ArrayList<>();

    public interface OnLoadMore {

        void loadMore();
    }

    private boolean loadMore;

    public ReaderTXTAdapter(Context context,
                            RecyclerView recyclerView,
                            OnLoadMore mOnLoadMore
    ) {
        this.mOnLoadMore = mOnLoadMore;
        this.context = context;
        this.recyclerView = recyclerView;

        this.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (loadMore) {
                    return;
                }
                if (!recyclerView.canScrollVertically(1)) {
                    loadMore = true;
                    mOnLoadMore.loadMore();
                }
            }
        });


    }

    @NonNull
    @Override
    public ReaderVh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReaderVh(LayoutInflater.from(context).inflate(R.layout.show_log_sdk_log_file_dialog_item_layout, parent, false));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private int padding = SizeUtils.dpToPx(context, 10F);

    @Override
    public void onBindViewHolder(@NonNull ReaderVh holder, int position) {
        String s = list.get(position);
        if (s.equals(sLoading)) {
            holder.mTextView.setGravity(Gravity.CENTER);
            holder.mTextView.setTextColor(Color.GREEN);
            holder.mTextView.setPadding(padding, padding, padding, padding);
            holder.mTextView.setText("正在加载更多数据...");
            return;
        }
        if (s.equals(sError)) {
            holder.mTextView.setGravity(Gravity.CENTER);
            holder.mTextView.setTextColor(Color.RED);
            holder.mTextView.setPadding(padding, padding, padding, padding);
            holder.mTextView.setText("已经到底了");
            return;
        }
        holder.mTextView.setPadding(0, 0, 0, 0);
        holder.mTextView.setTextColor(Color.BLACK);
        holder.mTextView.setGravity(Gravity.START);
        holder.mTextView.setText(list.get(position));
    }


    public void addList(List<String> list) {
        if (list == null || list.isEmpty()) {
            if (!this.list.isEmpty()) {
                this.list.remove(this.list.size() - 1);
                this.list.add(sError);
                notifyDataSetChanged();
                return;
            }
            return;
        }
        if (!this.list.isEmpty()) {
            this.list.remove(this.list.size() - 1);
        }
        this.list.addAll(list);
        this.list.add(sLoading);
        notifyDataSetChanged();
        loadMore = false;
    }

    public static class ReaderVh extends RecyclerView.ViewHolder {

        public TextView mTextView;

        public ReaderVh(@NonNull View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.id_show_file_tvcontent);
        }
    }
}
