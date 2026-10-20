package com.xq.dialoglogshow.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.model.AppDeviceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 设备与应用全景信息列表适配器。
 * 为什么这样做：
 * 1. 采用简洁高效的卡片排版，重要指标与警告项高亮显眼；
 * 2. 支持单项点击或长按一键将数值复制到剪贴板，极大便利测试报 Bug；
 * 3. 内部维护分类和搜索过滤，毫秒级局部响应。
 */
public final class AppDeviceInfoAdapter extends RecyclerView.Adapter<AppDeviceInfoAdapter.ViewHolder> {

    private final List<AppDeviceInfo.InfoItem> mSourceList = new ArrayList<>();
    private final List<AppDeviceInfo.InfoItem> mDisplayList = new ArrayList<>();

    private String mCurrentCategory = "全部";
    private String mKeyword = "";

    public void setData(@NonNull List<AppDeviceInfo.InfoItem> items) {
        mSourceList.clear();
        mSourceList.addAll(items);
        filterAndNotify();
    }

    public void setCategory(@NonNull String category) {
        this.mCurrentCategory = category;
        filterAndNotify();
    }

    public void setKeyword(@NonNull String keyword) {
        this.mKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        filterAndNotify();
    }

    public int getDisplayItemCount() {
        return mDisplayList.size();
    }

    private void filterAndNotify() {
        mDisplayList.clear();
        for (AppDeviceInfo.InfoItem item : mSourceList) {
            // 分类匹配
            if (!"全部".equals(mCurrentCategory) && !item.category.contains(mCurrentCategory) && !mCurrentCategory.contains(item.category)) {
                continue;
            }
            // 搜索匹配
            if (!mKeyword.isEmpty()) {
                boolean matchKey = item.key.toLowerCase(Locale.ROOT).contains(mKeyword);
                boolean matchVal = item.value.toLowerCase(Locale.ROOT).contains(mKeyword);
                boolean matchCat = item.category.toLowerCase(Locale.ROOT).contains(mKeyword);
                if (!matchKey && !matchVal && !matchCat) {
                    continue;
                }
            }
            mDisplayList.add(item);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.show_item_app_device_info_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppDeviceInfo.InfoItem item = mDisplayList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvKey.setText(item.key);
        holder.tvCategoryBadge.setText(item.category);
        holder.tvValue.setText(item.value);

        // 重要属性或告警属性高亮呈现
        if (item.isImportant) {
            holder.tvValue.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_2xx));
        } else if (item.value.contains("⚠️") || item.value.contains("紧急")) {
            holder.tvValue.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_error));
        } else {
            holder.tvValue.setTextColor(ContextCompat.getColor(context, R.color.showlog_action_btn));
        }

        // 点击或长按复制该项数值
        View.OnClickListener copyListener = v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(item.key, item.value));
                Toast.makeText(context, "已复制 " + item.key + ": " + item.value, Toast.LENGTH_SHORT).show();
            }
        };

        holder.itemView.setOnClickListener(copyListener);
        holder.itemView.setOnLongClickListener(v -> {
            copyListener.onClick(v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mDisplayList.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvKey;
        final TextView tvCategoryBadge;
        final TextView tvValue;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvKey = itemView.findViewById(R.id.tv_info_key);
            tvCategoryBadge = itemView.findViewById(R.id.tv_info_category_badge);
            tvValue = itemView.findViewById(R.id.tv_info_value);
        }
    }
}
