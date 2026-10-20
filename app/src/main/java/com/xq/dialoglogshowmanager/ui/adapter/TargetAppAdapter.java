package com.xq.dialoglogshowmanager.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.xq.dialoglogshowmanager.R;
import com.xq.dialoglogshowmanager.ui.entity.CaptureAbilityState;
import com.xq.dialoglogshowmanager.ui.entity.CertificateTrustState;
import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标 App 状态列表适配器。
 * 为什么这样做：把状态文案和颜色计算收在 Adapter 里，Activity 只关心数据刷新，后续更容易迁移到 RecyclerView。
 */
public final class TargetAppAdapter extends BaseAdapter {

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final ArrayList<TargetAppStatusItem> mItems = new ArrayList<>();

    public TargetAppAdapter(@NonNull Context context) {
        // 为什么这样做：列表项里使用了 MaterialCardView，必须保留 Activity 的主题上下文，否则会触发 MaterialComponents 主题校验崩溃。
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
    }

    public void submitList(@NonNull List<TargetAppStatusItem> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.item_target_app_status, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        bind(holder, mItems.get(position));
        return convertView;
    }

    /**
     * 为什么这样做：Item 文案里有多个状态字段，拆成局部方法能避免 onBind 里过度堆叠业务判断。
     */
    private void bind(@NonNull ViewHolder holder, @NonNull TargetAppStatusItem item) {
        holder.tvAppName.setText(item.appInfo.appName);
        holder.tvPackageName.setText(mContext.getString(
                R.string.target_app_package_label,
                item.appInfo.packageName
        ));
        holder.tvMetaInfo.setText(mContext.getString(
                R.string.target_app_target_sdk_label,
                item.appInfo.targetSdkVersion
        ));
        holder.tvSystemApp.setText(mContext.getString(
                R.string.target_app_system_app_label,
                item.appInfo.systemApp
                        ? mContext.getString(R.string.system_app_yes)
                        : mContext.getString(R.string.system_app_no)
        ));
        holder.tvTrustState.setText(resolveTrustStateText(mContext, item.certificateTrustState));
        holder.tvCaptureState.setText(resolveCaptureStateText(mContext, item.captureAbilityState));
        holder.tvStatusNote.setText(resolveStatusNoteText(mContext, item));
        applyStateColor(holder.tvTrustState, item.certificateTrustState);
        applyStateColor(holder.tvCaptureState, item.captureAbilityState);
    }

    /**
     * 为什么这样做：状态文案依赖资源，不能直接把 enum 名字暴露给用户。
     */
    @NonNull
    private String resolveTrustStateText(@NonNull Context context,
                                         @NonNull CertificateTrustState trustState) {
        switch (trustState) {
            case TRUSTED:
                return context.getString(R.string.certificate_trust_trusted);
            case NOT_TRUSTED:
                return context.getString(R.string.certificate_trust_not_trusted);
            case NOT_REQUIRED:
                return context.getString(R.string.certificate_trust_not_required);
            case UNKNOWN:
            default:
                return context.getString(R.string.certificate_trust_unknown);
        }
    }

    /**
     * 为什么这样做：抓包能力也要给出可读文案，避免用户只能看到一串技术枚举。
     */
    @NonNull
    private String resolveCaptureStateText(@NonNull Context context,
                                           @NonNull CaptureAbilityState captureAbilityState) {
        switch (captureAbilityState) {
            case FULLY_CAPTURED:
                return context.getString(R.string.capture_ability_full);
            case PARTIALLY_CAPTURED:
                return context.getString(R.string.capture_ability_partial);
            case NOT_CAPTURED:
                return context.getString(R.string.capture_ability_blocked);
            case UNKNOWN:
            default:
                return context.getString(R.string.capture_ability_unknown);
        }
    }

    /**
     * 为什么这样做：状态说明要告诉用户这是“基于日志推断”而不是系统硬读出的结论。
     */
    @NonNull
    private String resolveStatusNoteText(@NonNull Context context, @NonNull TargetAppStatusItem item) {
        if (!item.hasTrafficSample) {
            return context.getString(R.string.capture_note_unknown);
        }
        if (item.certificateTrustState == CertificateTrustState.TRUSTED) {
            return context.getString(R.string.capture_note_trusted);
        }
        if (item.certificateTrustState == CertificateTrustState.NOT_TRUSTED) {
            return context.getString(R.string.capture_note_not_trusted);
        }
        return context.getString(R.string.capture_note_not_required);
    }

    /**
     * 为什么这样做：状态颜色只做轻量提示，不依赖图片资源，便于后续跟随主题一并调整。
     */
    private void applyStateColor(@NonNull TextView textView, @NonNull CertificateTrustState state) {
        int colorRes;
        switch (state) {
            case TRUSTED:
                colorRes = R.color.demo_status_success;
                break;
            case NOT_TRUSTED:
                colorRes = R.color.demo_status_danger;
                break;
            case NOT_REQUIRED:
                colorRes = R.color.demo_status_info;
                break;
            case UNKNOWN:
            default:
                colorRes = R.color.demo_status_neutral;
                break;
        }
        textView.setBackgroundTintList(ColorStateList.valueOf(
                textView.getContext().getColor(colorRes)
        ));
    }

    /**
     * 为什么这样做：抓包能力的颜色语义和证书状态一致，用户扫一眼就能区分“完整命中”和“仅看到连接级信息”。
     */
    private void applyStateColor(@NonNull TextView textView, @NonNull CaptureAbilityState state) {
        int colorRes;
        switch (state) {
            case FULLY_CAPTURED:
                colorRes = R.color.demo_status_success;
                break;
            case PARTIALLY_CAPTURED:
                colorRes = R.color.demo_status_warning;
                break;
            case NOT_CAPTURED:
                colorRes = R.color.demo_status_danger;
                break;
            case UNKNOWN:
            default:
                colorRes = R.color.demo_status_neutral;
                break;
        }
        textView.setBackgroundTintList(ColorStateList.valueOf(
                textView.getContext().getColor(colorRes)
        ));
    }

    /**
     * 为什么这样做：ViewHolder 里把所有子控件一次性缓存，避免列表滚动时重复查找。
     */
    private static final class ViewHolder {
        private final TextView tvAppName;
        private final TextView tvPackageName;
        private final TextView tvMetaInfo;
        private final TextView tvSystemApp;
        private final TextView tvTrustState;
        private final TextView tvCaptureState;
        private final TextView tvStatusNote;

        private ViewHolder(@NonNull View itemView) {
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvPackageName = itemView.findViewById(R.id.tv_package_name);
            tvMetaInfo = itemView.findViewById(R.id.tv_target_sdk);
            tvSystemApp = itemView.findViewById(R.id.tv_system_app);
            tvTrustState = itemView.findViewById(R.id.tv_trust_state);
            tvCaptureState = itemView.findViewById(R.id.tv_capture_state);
            tvStatusNote = itemView.findViewById(R.id.tv_status_note);
        }
    }
}
