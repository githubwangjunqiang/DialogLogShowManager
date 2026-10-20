package com.xq.dialoglogshow.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.custom.CustomPanelManager;
import com.xq.dialoglogshow.core.custom.CustomPanelProvider;

/**
 * 自定义扩展面板承载视图。
 * 为什么这样做：
 * 赋予接入方自由扩展能力，若业务方注册了 CustomPanelProvider，则安全渲染其业务 View；
 * 若未注册，则优雅降级呈现图文并茂的开发者接入引导，确保界面不报错且指引清晰。
 */
public final class CustomPanelPageView extends FrameLayout {

    private FrameLayout mContainerCustomContent;
    private ScrollView mScrollCustomEmptyGuide;
    private View mCurrentCustomView;

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
        renderCustomContent();
    }

    private void initViews() {
        mContainerCustomContent = findViewById(R.id.container_custom_content);
        mScrollCustomEmptyGuide = findViewById(R.id.scroll_custom_empty_guide);
    }

    public void renderCustomContent() {
        CustomPanelManager manager = CustomPanelManager.getInstance();
        CustomPanelProvider provider = manager.getProvider();

        if (provider != null) {
            try {
                mContainerCustomContent.removeAllViews();
                mCurrentCustomView = provider.onCreateView(getContext());
                if (mCurrentCustomView != null) {
                    mContainerCustomContent.addView(mCurrentCustomView, new LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    mContainerCustomContent.setVisibility(VISIBLE);
                    mScrollCustomEmptyGuide.setVisibility(GONE);
                    return;
                }
            } catch (Throwable e) {
                // 防御性异常处理：即便业务方自定义 View 抛出异常，也降级捕获不导致宿主崩溃
                TextView errorTv = new TextView(getContext());
                errorTv.setTextColor(0xFFF43F5E);
                errorTv.setText("自定义扩展面板加载异常:\n" + e.getMessage());
                mContainerCustomContent.removeAllViews();
                mContainerCustomContent.addView(errorTv);
                mContainerCustomContent.setVisibility(VISIBLE);
                mScrollCustomEmptyGuide.setVisibility(GONE);
                return;
            }
        }

        // 未注册自定义面板，展示默认接入指引
        mContainerCustomContent.setVisibility(GONE);
        mScrollCustomEmptyGuide.setVisibility(VISIBLE);
    }
}
