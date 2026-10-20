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
import com.xq.dialoglogshow.core.config.ShowLogConfig;
import com.xq.dialoglogshow.core.custom.CustomPanelManager;
import com.xq.dialoglogshow.ui.view.AppDeviceInfoView;
import com.xq.dialoglogshow.ui.view.CustomPanelPageView;
import com.xq.dialoglogshow.ui.view.HttpLogConsoleView;
import com.xq.dialoglogshow.ui.view.FileBrowserView;
import com.xq.dialoglogshow.ui.view.ShowLogHubTabPanelBackground;

import java.util.ArrayList;
import java.util.List;

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
    public static final int TAB_FILE_BROWSER = 3;

    /** 默认页签数量；文件 Tab 是否追加由初始化配置决定。 */
    private static final int BASE_TAB_COUNT = 3;

    private static final long THROTTLE_MS = 350L;
    private static int sLastSelectedTab = TAB_NETWORK;

    private long mLastClickTime = 0L;
    private final int mInitialTab;
    private final ShowLogConfig mConfig;
    private final int mTabCount;
    /** 同一弹框的页签标题和面板实现固定取自同一次注册，避免运行中注册造成标题与内容错配。 */
    private final CustomPanelManager.Registration mCustomPanelRegistration;

    private TextView mTabHubNetwork;
    private TextView mTabHubDevice;
    private TextView mTabHubCustom;
    private TextView mTabHubFileBrowser;
    private ViewPager2 mVpPages;
    private ShowLogHubTabPanelBackground mTabPanelBackground;

    private HttpLogConsoleView mNetworkView;
    private AppDeviceInfoView mDeviceView;
    private CustomPanelPageView mCustomView;
    private FileBrowserView mFileBrowserView;

    public ShowLogHubDialog(@NonNull Context context) {
        this(context, sLastSelectedTab, ShowLogConfig.defaultConfig());
    }

    public ShowLogHubDialog(@NonNull Context context, int initialTab) {
        this(context, initialTab, ShowLogConfig.defaultConfig());
    }

    /**
     * 按 SDK 初始化配置构建中枢，确保文件 Tab 与配置的路径/标题来自同一不可变快照。
     */
    public ShowLogHubDialog(@NonNull Context context, @NonNull ShowLogConfig config) {
        this(context, sLastSelectedTab, config);
    }

    /**
     * 创建指定初始页签的中枢。
     */
    public ShowLogHubDialog(@NonNull Context context, int initialTab, @NonNull ShowLogConfig config) {
        super(context);
        this.mConfig = config != null ? config : ShowLogConfig.defaultConfig();
        this.mTabCount = mConfig.fileBrowserRootPath == null ? BASE_TAB_COUNT : BASE_TAB_COUNT + 1;
        this.mInitialTab = (initialTab >= TAB_NETWORK && initialTab < mTabCount) ? initialTab : TAB_NETWORK;
        // 为什么这样做：配置为空时上一轮记忆的文件页索引已不存在，立即纠正静态记忆避免下次再次越界。
        sLastSelectedTab = mInitialTab;
        this.mCustomPanelRegistration = CustomPanelManager.getInstance().getRegistration();
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

    @Override
    protected void onStart() {
        super.onStart();
        if (mDeviceView != null && mVpPages != null) {
            mDeviceView.setDeviceTabActive(mVpPages.getCurrentItem() == TAB_DEVICE);
        }
        // 为什么这样做：同一弹框实例重新 show 时，ViewPager2 不一定会再次派发 onPageSelected，
        // 需要主动恢复自定义面板，否则业务方看到的仍是释放后的空态。
        if (mCustomView != null && mVpPages != null && mVpPages.getCurrentItem() == TAB_CUSTOM) {
            mCustomView.renderCustomContent();
        }
    }

    @Override
    protected void onStop() {
        if (mDeviceView != null) {
            mDeviceView.setDeviceTabActive(false);
        }
        super.onStop();
    }

    @Override
    public void dismiss() {
        // 为什么这样做：dismiss 是弹框关闭的统一出口（返回键 cancel 最终也会走到这里），
        // 在此回调业务方 onDestroyView，确保自定义面板持有的资源被及时释放。
        if (mCustomView != null) {
            mCustomView.release();
        }
        if (mFileBrowserView != null) {
            mFileBrowserView.release();
        }
        super.dismiss();
    }

    private void initViews() {
        mTabHubNetwork = findViewById(R.id.tab_hub_network);
        mTabHubDevice = findViewById(R.id.tab_hub_device);
        mTabHubCustom = findViewById(R.id.tab_hub_custom);
        mTabHubFileBrowser = findViewById(R.id.tab_hub_file_browser);
        mVpPages = findViewById(R.id.vp_hub_pages);
        mTabPanelBackground = findViewById(R.id.v_tab_panel_background);
        if (mTabCount > BASE_TAB_COUNT) {
            mTabHubFileBrowser.setVisibility(View.VISIBLE);
            String fileTabTitle = mConfig.fileBrowserTabTitle;
            mTabHubFileBrowser.setText(fileTabTitle != null
                    ? fileTabTitle
                    : getContext().getString(R.string.file_browser));
        } else {
            mTabHubFileBrowser.setVisibility(View.GONE);
        }

        // 关闭事件绑定到整个挂钩容器，图标本身不再单独响应，避免只有 20dp 图标可点
        View closeHook = findViewById(R.id.btn_close_hub_container);
        closeHook.setOnClickListener(v -> dismiss());
        expandCloseHookTouchArea(closeHook);

        // 动态设置自定义面板的标题
        String customTitle = mCustomPanelRegistration.title;
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
        if (mTabCount > BASE_TAB_COUNT) {
            mTabHubFileBrowser.setOnClickListener(v -> {
                if (isThrottled() || mVpPages.getCurrentItem() == TAB_FILE_BROWSER) return;
                mVpPages.setCurrentItem(TAB_FILE_BROWSER, true);
            });
        }
    }

    private void setupViewPager() {
        mNetworkView = new HttpLogConsoleView(getContext());
        mDeviceView = new AppDeviceInfoView(getContext());
        mCustomView = new CustomPanelPageView(getContext());
        mCustomView.setRegistration(mCustomPanelRegistration);
        // 把真实弹框对象注入承载视图，业务方回调中才能主动控制弹框关闭
        mCustomView.setHostDialog(this);
        List<View> pageList = new ArrayList<>();
        pageList.add(mNetworkView);
        pageList.add(mDeviceView);
        pageList.add(mCustomView);
        if (mTabCount > BASE_TAB_COUNT) {
            mFileBrowserView = new FileBrowserView(getContext(), mConfig.fileBrowserRootPath);
            pageList.add(mFileBrowserView);
        }
        View[] pages = pageList.toArray(new View[0]);

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
                    mDeviceView.setDeviceTabActive(true);
                    mDeviceView.loadData();
                } else {
                    if (mDeviceView != null) {
                        mDeviceView.setDeviceTabActive(false);
                    }
                    if (position == TAB_CUSTOM && mCustomView != null) {
                        mCustomView.renderCustomContent();
                    }
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
        if (mTabHubFileBrowser != null) {
            resetTab(mTabHubFileBrowser);
        }

        if (selectedPosition == TAB_NETWORK) {
            highlightTab(mTabHubNetwork);
        } else if (selectedPosition == TAB_DEVICE) {
            highlightTab(mTabHubDevice);
        } else if (selectedPosition == TAB_CUSTOM) {
            highlightTab(mTabHubCustom);
        } else if (selectedPosition == TAB_FILE_BROWSER && mTabHubFileBrowser != null) {
            highlightTab(mTabHubFileBrowser);
        }

        // 选中页签的轮廓由统一背景 View 绘制，切换时必须同步索引，否则边框会停留在旧位置
        if (mTabPanelBackground != null) {
            mTabPanelBackground.setSelectedTab(selectedPosition, mTabCount);
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
