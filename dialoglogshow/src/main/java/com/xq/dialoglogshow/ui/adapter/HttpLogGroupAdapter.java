package com.xq.dialoglogshow.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.model.HttpLogGroupItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口名（Path）聚合分组折叠列表适配器。
 * 为什么这样做：
 * 1. 同接口多并发或重复调用的场景，折叠后只占一行卡片，大幅降低信息噪音；
 * 2. 点击展开直接内嵌复用 HttpLogFlatAdapter 渲染历次调用细节，保持视觉与交互完全一致。
 */
public final class HttpLogGroupAdapter extends RecyclerView.Adapter<HttpLogGroupAdapter.GroupViewHolder> {

    private static final long THROTTLE_MS = 300L;
    private long mLastClickTime = 0L;

    private final List<HttpLogGroupItem> mGroups = new ArrayList<>();

    public void setData(@NonNull List<HttpLogGroupItem> list) {
        mGroups.clear();
        mGroups.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.show_item_http_log_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        HttpLogGroupItem group = mGroups.get(position);
        Context context = holder.itemView.getContext();

        holder.tvGroupMethod.setText(group.method);
        holder.tvGroupPath.setText(group.path.isEmpty() ? "/" : group.path);
        holder.tvGroupHost.setText(group.host);
        holder.tvGroupCount.setText(context.getString(R.string.showlog_group_calls, group.totalCount));
        holder.tvGroupLatestInfo.setText(context.getString(R.string.showlog_group_latest, group.latestStatusCode, group.latestDurationMs));

        holder.tvExpandIndicator.setText(group.isExpanded ? "▲" : "▼");
        holder.rvChildren.setVisibility(group.isExpanded ? View.VISIBLE : View.GONE);

        if (group.isExpanded) {
            if (holder.childAdapter == null) {
                holder.childAdapter = new HttpLogFlatAdapter();
                holder.rvChildren.setLayoutManager(new LinearLayoutManager(context));
                holder.rvChildren.setAdapter(holder.childAdapter);
            }
            holder.childAdapter.setData(group.records);
            // 代理子项长按回调
            holder.childAdapter.setOnItemLongClickListener((view, record, pos) -> {
                if (mChildLongClickListener != null) {
                    mChildLongClickListener.onItemLongClick(view, record, pos);
                }
            });
        }

        holder.layoutHeader.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - mLastClickTime < THROTTLE_MS) {
                return;
            }
            mLastClickTime = now;
            group.isExpanded = !group.isExpanded;
            notifyItemChanged(holder.getBindingAdapterPosition());
        });

        // 长按整个分组 Header 卡片
        holder.layoutHeader.setOnLongClickListener(v -> {
            if (mGroupLongClickListener != null) {
                mGroupLongClickListener.onGroupLongClick(v, group, holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
    }

    public interface OnGroupLongClickListener {
        void onGroupLongClick(@NonNull View view, @NonNull HttpLogGroupItem group, int position);
    }

    private OnGroupLongClickListener mGroupLongClickListener;
    private HttpLogFlatAdapter.OnItemLongClickListener mChildLongClickListener;

    public void setOnGroupLongClickListener(OnGroupLongClickListener listener) {
        this.mGroupLongClickListener = listener;
    }

    public void setOnChildLongClickListener(HttpLogFlatAdapter.OnItemLongClickListener listener) {
        this.mChildLongClickListener = listener;
    }

    @Override
    public int getItemCount() {
        return mGroups.size();
    }

    static final class GroupViewHolder extends RecyclerView.ViewHolder {
        final View layoutHeader;
        final TextView tvGroupMethod;
        final TextView tvGroupPath;
        final TextView tvGroupHost;
        final TextView tvGroupCount;
        final TextView tvGroupLatestInfo;
        final TextView tvExpandIndicator;
        final RecyclerView rvChildren;
        HttpLogFlatAdapter childAdapter;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layout_group_header);
            tvGroupMethod = itemView.findViewById(R.id.tv_group_method);
            tvGroupPath = itemView.findViewById(R.id.tv_group_path);
            tvGroupHost = itemView.findViewById(R.id.tv_group_host);
            tvGroupCount = itemView.findViewById(R.id.tv_group_count);
            tvGroupLatestInfo = itemView.findViewById(R.id.tv_group_latest_info);
            tvExpandIndicator = itemView.findViewById(R.id.tv_expand_indicator);
            rvChildren = itemView.findViewById(R.id.rv_group_children);
        }
    }
}
