package com.xq.dialoglogshow.collector.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.collector.dialog.AutoHttpLogInfoDialog;
import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.utils.ClipboardUtils;
import com.xq.dialoglogshow.utils.DateUtils;
import com.xq.dialoglogshow.utils.ShareUtils;
import com.xq.dialoglogshow.view.BigTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动网络日志专用的独立 RecyclerView Adapter
 *
 * 为什么这样做：
 * 1. 彻底与老的 MyAdapter 解耦，避免逻辑耦合与干扰。
 * 2. 完美支持单列表模式 (点击直接弹窗) 与 父子树状模式 (点击展开/折叠)。
 * 3. 遵照项目强制规范，包含 500ms 点击防抖与中文注释。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/24
 */
public class AutoHttpLogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_SINGLE_OR_CHILD = 0;
    public static final int TYPE_GROUP_PARENT = 1;

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final RecyclerView mRecyclerView;
    private final List<AutoHttpLogModel> mList = new ArrayList<>();
    private long mLastClickTime = 0;

    public AutoHttpLogAdapter(Context context, RecyclerView recyclerView) {
        this.mContext = context;
        this.mInflater = LayoutInflater.from(context);
        this.mRecyclerView = recyclerView;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setList(List<AutoHttpLogModel> list) {
        mList.clear();
        if (list != null) {
            mList.addAll(list);
        }
        notifyDataSetChanged();
    }

    public List<AutoHttpLogModel> getList() {
        return mList;
    }

    @Override
    public int getItemViewType(int position) {
        AutoHttpLogModel model = mList.get(position);
        return model.isGroupParent() ? TYPE_GROUP_PARENT : TYPE_SINGLE_OR_CHILD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_GROUP_PARENT) {
            View view = mInflater.inflate(R.layout.show_sdk_httplog_item_tow_layout, parent, false);
            return new AutoGroupParentViewHolder(view);
        } else {
            View view = mInflater.inflate(R.layout.show_sdk_httplog_item_three_layout, parent, false);
            return new AutoSingleItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AutoHttpLogModel model = mList.get(position);
        if (holder instanceof AutoGroupParentViewHolder) {
            ((AutoGroupParentViewHolder) holder).bind(model, position);
        } else if (holder instanceof AutoSingleItemViewHolder) {
            ((AutoSingleItemViewHolder) holder).bind(model, position);
        }
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /**
     * 点击防抖判断 (默认 500ms 强制规则)
     */
    private boolean isFastClick() {
        long now = System.currentTimeMillis();
        if (now - mLastClickTime < 500) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }

    /**
     * 父分组 ViewHolder
     */
    private class AutoGroupParentViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTvContent;
        private final View mIvArrow;

        public AutoGroupParentViewHolder(@NonNull View itemView) {
            super(itemView);
            mTvContent = itemView.findViewById(R.id.show_sdk_id_item_http_log_one_tv_content);
            mIvArrow = itemView.findViewById(R.id.show_sdk_id_item_http_log_one__iv_jaintou);
        }

        @SuppressLint("SetTextI18n")
        public void bind(AutoHttpLogModel model, int position) {
            int childCount = model.getChildList() != null ? model.getChildList().size() : 0;
            mTvContent.setText("Index：" + model.getIndex() + " (包含 " + childCount + " 条请求)\n" + "Host/Group：" + model.getUrl());

            if (model.isExpanded()) {
                mIvArrow.setRotation(90);
            } else {
                mIvArrow.setRotation(0);
            }

            // 父节点点击事件：展开 / 折叠
            itemView.setOnClickListener(v -> {
                if (isFastClick()) return;
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                boolean expanded = model.isExpanded();
                model.setExpanded(!expanded);
                notifyItemChanged(pos);

                List<AutoHttpLogModel> children = model.getChildList();
                if (children != null && !children.isEmpty()) {
                    if (expanded) {
                        // 已展开 -> 要收起
                        for (int i = 0; i < children.size(); i++) {
                            mList.remove(pos + 1);
                        }
                        notifyItemRangeRemoved(pos + 1, children.size());
                    } else {
                        // 未展开 -> 要展开
                        for (int i = children.size() - 1; i >= 0; i--) {
                            mList.add(pos + 1, children.get(i));
                        }
                        notifyItemRangeInserted(pos + 1, children.size());
                    }
                }
            });
        }
    }

    /**
     * 单独立项 / 子项 ViewHolder
     */
    private class AutoSingleItemViewHolder extends RecyclerView.ViewHolder {
        private final BigTextView mBigTextView;
        private final View mViewDelete;

        public AutoSingleItemViewHolder(@NonNull View itemView) {
            super(itemView);
            mBigTextView = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_tvbig);
            mViewDelete = itemView.findViewById(R.id.show_sdk_id_dialog_http_log_delete);
        }

        public void bind(AutoHttpLogModel model, int position) {
            String formatTime = DateUtils.format(model.getTime());
            String status = model.getStatusCode() != null ? (" [" + model.getStatusCode() + "]") : "";
            String displayText = "Index：" + model.getIndex() + status + "，Time：" + formatTime + "\n" +
                    "Url：" + model.getUrl() + "\n" +
                    "👉 点击直接弹出完整 Headers/Body/cURL 详情";
            mBigTextView.setMessage(displayText);

            // 点击卡片：直接弹出独立的 AutoHttpLogInfoDialog 详情弹窗
            itemView.setOnClickListener(v -> {
                if (isFastClick()) return;
                new AutoHttpLogInfoDialog(mContext, model).show();
            });

            // 长按卡片：复制全报文与分享
            itemView.setOnLongClickListener(v -> {
                String content = model.getFullDetailText();
                ClipboardUtils.copyText(content, mContext.getApplicationContext());
                ShareUtils.shareText(mContext.getApplicationContext(), content);
                return true;
            });

            // 删除按钮（若可见）
            if (mViewDelete != null) {
                mViewDelete.setOnClickListener(v -> {
                    if (isFastClick()) return;
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        mList.remove(pos);
                        notifyItemRemoved(pos);
                    }
                });
            }
        }
    }
}
