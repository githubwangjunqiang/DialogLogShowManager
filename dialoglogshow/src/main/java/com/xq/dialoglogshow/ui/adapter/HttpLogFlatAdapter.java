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
 * 1. 列表项只保留“请求方式、时间、业务成败、接口全名”四个关键信息，两行高度即可完成快速扫读；
 * 2. 业务成败直接使用 HttpLogRecord.success，失败统一标红，不再混用 HTTP 状态码判断；
 * 3. 结合防抖机制避免快速连击重复弹出多个详情弹窗；
 * 4. 内部复用 SimpleDateFormat 格式化时间。
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

        // 1. 业务成功/失败徽标：以数据字段 success 为准，失败使用红色
        holder.tvSuccess.setText(record.success ? R.string.showlog_success : R.string.showlog_failure);
        holder.tvSuccess.setTextColor(ContextCompat.getColor(context,
                record.success ? R.color.showlog_status_2xx : R.color.showlog_status_5xx));

        // 2. 请求方式与时间
        holder.tvMethod.setText(record.method);
        holder.tvTimestamp.setText(mTimeFormatter.format(new Date(record.timestamp)));

        // 3. 接口全名：优先完整 URL，缺失时回退到 host + path，保证仍能定位接口
        String fullUrl = record.url;
        if (fullUrl == null || fullUrl.trim().isEmpty()) {
            String host = record.host != null ? record.host : "";
            String path = record.path != null ? record.path : "";
            fullUrl = host + path;
        }
        holder.tvFullUrl.setText(fullUrl == null || fullUrl.trim().isEmpty()
                ? context.getString(R.string.showlog_path_root) : fullUrl);

        // 4. 点击弹出详情（防抖 350ms）
        holder.itemView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - mLastClickTime < THROTTLE_MS) {
                return;
            }
            mLastClickTime = now;
            new HttpLogDetailDialog(context, record).show();
        });

        // 5. 长按弹出删除单条确认
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

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSuccess;
        final TextView tvMethod;
        final TextView tvTimestamp;
        final TextView tvFullUrl;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSuccess = itemView.findViewById(R.id.tv_success);
            tvMethod = itemView.findViewById(R.id.tv_method);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvFullUrl = itemView.findViewById(R.id.tv_full_url);
        }
    }
}
