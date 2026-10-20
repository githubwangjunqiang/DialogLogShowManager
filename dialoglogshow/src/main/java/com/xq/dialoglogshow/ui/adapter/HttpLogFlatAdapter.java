package com.xq.dialoglogshow.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.model.HttpLogRecord;
import com.xq.dialoglogshow.ui.dialog.HttpLogDetailDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 扁平瀑布流日志列表适配器。
 * 为什么这样做：
 * 1. 采用简洁高效的卡片排版，状态码药丸按 2xx/3xx/4xx/5xx/异常 分类着色，错误一目了然；
 * 2. 结合防抖机制避免快速连击重复弹出多个详情弹窗；
 * 3. 内部复用 SimpleDateFormat 格式化时间。
 */
public final class HttpLogFlatAdapter extends RecyclerView.Adapter<HttpLogFlatAdapter.ViewHolder> {

    private static final long THROTTLE_MS = 500L;
    private long mLastClickTime = 0L;

    private final List<HttpLogRecord> mData = new ArrayList<>();
    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public void setData(@NonNull List<HttpLogRecord> list) {
        mData.clear();
        mData.addAll(list);
        notifyDataSetChanged();
    }

    public void addFirst(@NonNull HttpLogRecord record) {
        mData.add(0, record);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.show_item_http_log_flat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HttpLogRecord record = mData.get(position);
        Context context = holder.itemView.getContext();

        // 1. 状态码与色彩
        if (!record.success) {
            if (record.statusCode < 0) {
                holder.tvStatusCode.setText("FAIL");
            } else {
                holder.tvStatusCode.setText(String.valueOf(record.statusCode));
            }
            holder.tvStatusCode.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_error));
        } else {
            holder.tvStatusCode.setText(String.valueOf(record.statusCode));
            if (record.statusCode >= 200 && record.statusCode < 300) {
                holder.tvStatusCode.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_2xx));
            } else if (record.statusCode >= 300 && record.statusCode < 400) {
                holder.tvStatusCode.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_3xx));
            } else if (record.statusCode >= 400 && record.statusCode < 500) {
                holder.tvStatusCode.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_4xx));
            } else {
                holder.tvStatusCode.setTextColor(ContextCompat.getColor(context, R.color.showlog_status_5xx));
            }
        }

        // 2. 方法与耗时
        holder.tvMethod.setText(record.method);
        holder.tvDuration.setText(record.durationMs + " ms");
        holder.tvSize.setText(formatByteSize(record.responseSize > 0 ? record.responseSize : record.requestSize));
        holder.tvTimestamp.setText(mTimeFormatter.format(new Date(record.timestamp)));

        // 3. 路径与 Host
        holder.tvPath.setText(record.path.isEmpty() ? "/" : record.path);
        holder.tvHost.setText(record.host);

        // 4. 异常/业务提示
        if (!record.success) {
            holder.tvErrorSummary.setVisibility(View.VISIBLE);
            if (record.errorMessage != null && !record.errorMessage.trim().isEmpty()) {
                holder.tvErrorSummary.setText(record.errorMessage.trim().split("\n")[0]);
            } else if (record.message != null && !record.message.isEmpty()) {
                holder.tvErrorSummary.setText("业务失败: " + record.message);
            } else {
                holder.tvErrorSummary.setText("请求失败");
            }
        } else {
            holder.tvErrorSummary.setVisibility(View.GONE);
        }

        // 5. 点击弹出详情（防抖 350ms）
        holder.itemView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - mLastClickTime < THROTTLE_MS) {
                return;
            }
            mLastClickTime = now;
            new HttpLogDetailDialog(context, record).show();
        });

        // 6. 长按弹出删除单条确认
        holder.itemView.setOnLongClickListener(v -> {
            if (mLongClickListener != null) {
                mLongClickListener.onItemLongClick(v, record, holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(@NonNull View view, @NonNull HttpLogRecord record, int position);
    }

    private OnItemLongClickListener mLongClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.mLongClickListener = listener;
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @NonNull
    private static String formatByteSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvStatusCode;
        final TextView tvMethod;
        final TextView tvDuration;
        final TextView tvSize;
        final TextView tvTimestamp;
        final TextView tvPath;
        final TextView tvHost;
        final TextView tvErrorSummary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatusCode = itemView.findViewById(R.id.tv_status_code);
            tvMethod = itemView.findViewById(R.id.tv_method);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvSize = itemView.findViewById(R.id.tv_size);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvPath = itemView.findViewById(R.id.tv_path);
            tvHost = itemView.findViewById(R.id.tv_host);
            tvErrorSummary = itemView.findViewById(R.id.tv_error_summary);
        }
    }
}
