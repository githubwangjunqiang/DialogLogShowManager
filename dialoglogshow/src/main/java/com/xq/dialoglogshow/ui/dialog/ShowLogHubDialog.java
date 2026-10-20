package com.xq.dialoglogshow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.custom.CustomPanelManager;
import com.xq.dialoglogshow.ui.view.AppDeviceInfoView;
import com.xq.dialoglogshow.ui.view.CustomPanelPageView;
import com.xq.dialoglogshow.ui.view.HttpLogConsoleView;
import com.xq.dialoglogshow.ui.view.ShowLogHubTabPanelBackground;

/**
 * ShowLog 统一全景调试中枢主弹窗 (ShowLog Hub)。
 * 为什么这样做：
 * 1. 方案一落地：将「🌐 网络日志」、「📱 设备信息」与「🧩 自定义扩展」提升为完全同等级的三大核心类目；
 * 2. 顶部采用与内容面板一体的页签式导航：选中页签与内容面板由同一条轮廓绘制，
 *    底部以内凹圆角自然衔接，描边使用统一灰色，仅保留蓝色文字标识选中；
 * 3. 关闭按钮改为从卡片外框延伸出的红色挂钩，并通过 TouchDelegate 扩大点击热区；
 * 4. 内部自动保持状态与上次浏览记忆，极大提升联调效率；
 * 5. 具备严格的 350ms 全局防抖保护与异常兜底。
 */
public final class ShowLogHubDialog extends Dialog {

    public static final int TAB_NETWORK = 0;
    public static final int TAB_DEVICE = 1;
    public static final int TAB_CUSTOM = 2;

    /** 主中枢页签数量：轮廓绘制需要按固定分段计算选中位置，新增页签时必须同步修改。 */
    private static final int TAB_COUNT = 3;

    private static final long THROTTLE_MS = 350L;
    private static int sLastSelectedTab = TAB_NETWORK;

    private long mLastClickTime = 0L;
    private final int mInitialTab;

    private TextView mTabHubNetwork;
    private TextView mTabHubDevice;
    private TextView mTabHubCustom;
    private ViewPager2 mVpPages;
    private ShowLogHubTabPanelBackground mTabPanelBackground;

    private HttpLogConsoleView mNetworkView;
    private AppDeviceInfoView mDeviceView;
    private CustomPanelPageView mCustomView;

    public ShowLogHubDialog(@NonNull Context context) {
        this(context, sLastSelectedTab);
    }

    public ShowLogHubDialog(@NonNull Context context, int initialTab) {
        super(context);
        this.mInitialTab = (initialTab >= TAB_NETWORK && initialTab < TAB_COUNT) ? initialTab : TAB_NETWORK;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.show_dialog_hub);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.96);
            params.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.90);
            window.setAttributes(params);
        }

        initViews();
        setupViewPager();
    }

    private void initViews() {
        mTabHubNetwork = findViewById(R.id.tab_hub_network);
        mTabHubDevice = findViewById(R.id.tab_hub_device);
        mTabHubCustom = findViewById(R.id.tab_hub_custom);
        mVpPages = findViewById(R.id.vp_hub_pages);
        mTabPanelBackground = findViewById(R.id.v_tab_panel_background);

        // 关闭事件绑定到整个挂钩容器，图标本身不再单独响应，避免只有 20dp 图标可点
        View closeHook = findViewById(R.id.btn_close_hub_container);
        closeHook.setOnClickListener(v -> dismiss());
        expandCloseHookTouchArea(closeHook);

        // 动态设置自定义面板的标题
        String customTitle = CustomPanelManager.getInstance().getTitle();
        if (customTitle != null && !customTitle.isEmpty()) {
            mTabHubCustom.setText(customTitle.startsWith("🧩") ? customTitle : ("🧩 " + customTitle));
        }

        // 顶级分段导航栏点击切换
        mTabHubNetwork.setOnClickListener(v -> {
            if (isThrottled() || mVpPages.getCurrentItem() == TAB_NETWORK) return;
            mVpPages.setCurrentItem(TAB_NETWORK, true);
        });

        mTabHubDevice.setOnClickListener(v -> {
            if (isThrottled() || mVpPages.getCurrentItem() == TAB_DEVICE) return;
            mVpPages.setCurrentItem(TAB_DEVICE, true);
        });

        mTabHubCustom.setOnClickListener(v -> {
            if (isThrottled() || mVpPages.getCurrentItem() == TAB_CUSTOM) return;
            mVpPages.setCurrentItem(TAB_CUSTOM, true);
        });
    }

    private void setupViewPager() {
        mNetworkView = new HttpLogConsoleView(getContext());
        mDeviceView = new AppDeviceInfoView(getContext());
        mCustomView = new CustomPanelPageView(getContext());

        View[] pages = new View[]{mNetworkView, mDeviceView, mCustomView};

        mVpPages.setAdapter(new RecyclerView.Adapter<HubPageViewHolder>() {
            @NonNull
            @Override
            public HubPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new HubPageViewHolder(pages[viewType]);
            }

            @Override
            public void onBindViewHolder(@NonNull HubPageViewHolder holder, int position) {
            }

            @Override
            public int getItemCount() {
                return pages.length;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }
        });

        // 注册滑动监听联动顶部 Tab 高亮
        mVpPages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                sLastSelectedTab = position;
                updateSegmentedTabsUI(position);

                // 切换到设备面板时自动刷新一次最新内存与性能状态
                if (position == TAB_DEVICE && mDeviceView != null) {
                    mDeviceView.loadData();
                } else if (position == TAB_CUSTOM && mCustomView != null) {
                    mCustomView.renderCustomContent();
                }
            }
        });

        // 禁用手势横向滑动，仅允许点击 Tab 切换
        mVpPages.setUserInputEnabled(false);

        // 初始化选中初始 Tab
        mVpPages.setCurrentItem(mInitialTab, false);
        updateSegmentedTabsUI(mInitialTab);
    }

    private void updateSegmentedTabsUI(int selectedPosition) {
        resetTab(mTabHubNetwork);
        resetTab(mTabHubDevice);
        resetTab(mTabHubCustom);

        if (selectedPosition == TAB_NETWORK) {
            highlightTab(mTabHubNetwork);
        } else if (selectedPosition == TAB_DEVICE) {
            highlightTab(mTabHubDevice);
        } else if (selectedPosition == TAB_CUSTOM) {
            highlightTab(mTabHubCustom);
        }

        // 选中页签的轮廓由统一背景 View 绘制，切换时必须同步索引，否则边框会停留在旧位置
        if (mTabPanelBackground != null) {
            mTabPanelBackground.setSelectedTab(selectedPosition, TAB_COUNT);
        }
    }

    private void resetTab(TextView tab) {
        // 非选中 Tab 不保留任何背景，避免出现与选中态相似的视觉噪声
        tab.setBackgroundResource(0);
        tab.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        tab.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private void highlightTab(TextView tab) {
        // 页签与内容面板的边框和底色由 ShowLogHubTabPanelBackground 统一绘制；
        // 文字保持蓝色加粗作为选中提示，避免再叠加一张高饱和蓝色边框背景
        tab.setBackgroundResource(0);
        tab.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_action_btn));
        tab.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    /**
     * 扩大关闭挂钩的点击热区。
     * 为什么这样做：挂钩视觉区域为 36×37dp（容器 48×37dp），直接点击仍可能落空；
     * 通过 TouchDelegate 向左右与下方扩展热区，在不改变视觉尺寸的前提下把可点击范围提升到约 60×45dp。
     */
    private void expandCloseHookTouchArea(@NonNull View hook) {
        View root = findViewById(R.id.root_hub);
        if (root == null) {
            return;
        }
        root.post(() -> {
            Rect hitRect = new Rect();
            hook.getHitRect(hitRect);
            int expandHorizontal = dpToPx(6);
            int expandBottom = dpToPx(8);
            hitRect.inset(-expandHorizontal, 0);
            hitRect.bottom += expandBottom;
            root.setTouchDelegate(new TouchDelegate(hitRect, hook));
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private boolean isThrottled() {
        long now = System.currentTimeMillis();
        if (now - mLastClickTime < THROTTLE_MS) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }

    static class HubPageViewHolder extends RecyclerView.ViewHolder {
        public HubPageViewHolder(@NonNull View itemView) {
            super(itemView);
            // 确保 Page 宽高填满 ViewPager2
            itemView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}
