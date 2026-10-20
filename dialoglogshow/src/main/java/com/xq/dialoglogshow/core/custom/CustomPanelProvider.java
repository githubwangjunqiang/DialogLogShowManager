package com.xq.dialoglogshow.core.custom;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 自定义扩展面板提供者接口。
 * 为什么这样做：
 * 赋予接入方极简的自定义扩展能力，使「自定义扩展」与「网络日志」、「设备信息」成为同等地位的三大核心类目。
 * 开发者可在此随意注入环境切换、Mock数据开关、用户Token查看等业务调试工具。
 */
public interface CustomPanelProvider {

    /**
     * 创建自定义面板的视图。
     *
     * @param context 上下文（通常为宿主 Activity）
     * @return 业务自定义的 View
     */
    @NonNull
    View onCreateView(@NonNull Context context);

    /**
     * 面板销毁或关闭时的清理回调（可选实现）。
     */
    default void onDestroyView() {
    }
}
