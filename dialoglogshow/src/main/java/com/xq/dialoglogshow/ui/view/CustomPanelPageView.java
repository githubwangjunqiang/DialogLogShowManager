package com.xq.dialoglogshow.ui.view;

import android.app.Dialog;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.custom.CustomPanelManager;
import com.xq.dialoglogshow.core.custom.CustomPanelProvider;

/**
 * 自定义扩展面板承载视图。
 * 为什么这样做：
 * SDK 只提供承载父布局（container_custom_content）和当前调试弹框对象，
 * 业务方拿到二者后可以自行填充任意视图、绑定业务逻辑，并在需要时主动关闭弹框；
 * 未注册提供者或提供者内部异常时，仍保留内置引导与异常兜底，避免宿主崩溃。
 */
public final class CustomPanelPageView extends FrameLayout {

    private FrameLayout mContainerCustomContent;
    private ScrollView mScrollCustomEmptyGuide;

    /**
     * 当前调试弹框对象。
     * 为什么这样做：只有把真实弹框交给业务方，业务方才能在自定义交互中主动控制关闭时机。
     */
    @Nullable
    private Dialog mHostDialog;

    /**
     * 当前已绑定的提供者。
     * 用于识别提供者是否发生变化，避免用户切换 Tab 时重复回调 onCreateView 导致业务面板状态丢失。
     */
    @Nullable
    private CustomPanelProvider mBoundProvider;

    /** 与 Hub 页签标题同源的固定注册快照，保证单个弹框内配置一致。 */
    @Nullable
    private CustomPanelManager.Registration mRegistration;

    /**
     * 是否正在执行业务方 onCreateView 回调。
     * 为什么这样做：业务方可能在回调内直接调用 dialog.dismiss()，需要延迟 onDestroyView，
     * 避免出现 onCreateView 尚未返回就被销毁的逆序生命周期。
     */
    private boolean mBinding;

    /** 绑定过程中收到的释放请求，待 onCreateView 返回后统一执行。 */
    private boolean mReleasePending;

    public CustomPanelPageView(@NonNull Context context) {
        this(context, null);
    }

    public CustomPanelPageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomPanelPageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.show_layout_custom_panel, this, true);
        initViews();
    }

    private void initViews() {
        mContainerCustomContent = findViewById(R.id.container_custom_content);
        mScrollCustomEmptyGuide = findViewById(R.id.scroll_custom_empty_guide);
    }

    /**
     * 注入宿主调试弹框。
     * 为什么这样做：承载视图创建时弹框对象尚未构造完成，只能在宿主弹框初始化阶段回调注入。
     */
    public void setHostDialog(@NonNull Dialog hostDialog) {
        mHostDialog = hostDialog;
    }

    /**
     * 注入当前弹框的扩展配置快照。
     * 为什么这样做：标题与面板实现必须属于同一次注册，避免弹框显示期间并发注册造成配置错配。
     */
    public void setRegistration(@NonNull CustomPanelManager.Registration registration) {
        mRegistration = registration;
    }

    /**
     * 渲染自定义内容。
     * 为什么这样做：
     * 同一个提供者在同一弹框生命周期内只绑定一次，切换 Tab 时直接复用已有视图，
     * 既避免业务方重复创建控件，也避免输入框、开关等内部状态被重置。
     */
    public void renderCustomContent() {
        CustomPanelManager.Registration registration = mRegistration;
        CustomPanelProvider provider = registration != null ? registration.provider : null;

        if (provider == null) {
            releaseBoundPanel();
            showEmptyGuide();
            return;
        }

        // 提供者未变化时直接复用已创建的视图，避免切换 Tab 重复走业务方 onCreateView
        if (provider == mBoundProvider) {
            return;
        }

        // 未注入弹框时禁止回调，避免把 null 传给业务方引发空指针
        if (mHostDialog == null) {
            releaseBoundPanel();
            showEmptyGuide();
            return;
        }

        releaseBoundPanel();
        mBoundProvider = provider;
        // 先让承载父布局可见，业务方在回调中才能正确测量宽高、执行动画或加载占位图
        mContainerCustomContent.setVisibility(VISIBLE);
        mScrollCustomEmptyGuide.setVisibility(GONE);
        mBinding = true;
        try {
            provider.onCreateView(mContainerCustomContent, mHostDialog);
        } catch (Throwable e) {
            // 业务方自定义 UI 异常不能拖垮宿主，先结束绑定态再清理，确保 onDestroyView 正常回调
            mBinding = false;
            mReleasePending = false;
            releaseBoundPanel();
            showLoadError(e);
            return;
        } finally {
            mBinding = false;
        }

        // 业务方在 onCreateView 内主动关闭弹框时，释放动作会延迟到这里执行，保证生命周期顺序正确
        if (mReleasePending) {
            mReleasePending = false;
            release();
        }
    }

    /**
     * 释放业务面板。
     * 为什么这样做：弹框彻底关闭后及时回调 onDestroyView，给业务方释放监听器、定时器与网络请求的机会。
     */
    public void release() {
        // 回调尚未结束时只记录释放请求，避免 onDestroyView 早于 onCreateView 返回
        if (mBinding) {
            mReleasePending = true;
            return;
        }
        releaseBoundPanel();
        showEmptyGuide();
    }

    private void releaseBoundPanel() {
        CustomPanelProvider provider = mBoundProvider;
        mBoundProvider = null;
        if (provider != null) {
            try {
                provider.onDestroyView();
            } catch (Throwable ignored) {
                // 业务方清理逻辑异常不能影响 SDK 自身的关闭流程
            }
        }
        mContainerCustomContent.removeAllViews();
    }

    private void showEmptyGuide() {
        mContainerCustomContent.setVisibility(GONE);
        mScrollCustomEmptyGuide.setVisibility(VISIBLE);
    }

    private void showLoadError(@NonNull Throwable e) {
        TextView errorTv = new TextView(getContext());
        errorTv.setText(getContext().getString(R.string.showlog_custom_panel_load_error, safeMessage(e)));
        errorTv.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_error));
        // 使用 PX 直接消费 dimen 资源，避免 setTextSize 默认按 SP 二次缩放
        errorTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.sp_13));
        int padding = getResources().getDimensionPixelSize(R.dimen.dp_16);
        errorTv.setPadding(padding, padding, padding, padding);

        mContainerCustomContent.addView(errorTv, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mContainerCustomContent.setVisibility(VISIBLE);
        mScrollCustomEmptyGuide.setVisibility(GONE);
    }

    @NonNull
    private String safeMessage(@NonNull Throwable e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
