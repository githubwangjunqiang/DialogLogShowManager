package com.xq.dialoglogshow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.R;

/**
 * 网络日志关键字搜索弹框。
 * 为什么这样做：主控制台常驻搜索框会在打开页面时抢占焦点、弹出键盘并挤压日志列表；
 * 将输入收敛到独立弹框后，主界面只保留一个搜索入口，确定/清除/取消后再统一刷新列表。
 */
public final class SearchKeywordDialog extends Dialog {

    /**
     * 搜索动作回调。
     * 三个按钮都会先关闭弹框，再回调对应动作；取消不改变主界面现有的搜索条件。
     */
    public interface OnSearchActionListener {
        void onConfirm(@NonNull String keyword);

        void onClear();

        void onCancel();
    }

    private final String mInitialKeyword;
    private final OnSearchActionListener mListener;

    /** 防止键盘“完成”与按钮点击在极短时间内重复分发同一个动作。 */
    private boolean mActionDispatched;

    public SearchKeywordDialog(@NonNull Context context,
                               @Nullable String initialKeyword,
                               @Nullable OnSearchActionListener listener) {
        super(context);
        mInitialKeyword = initialKeyword != null ? initialKeyword : "";
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.show_dialog_search_keyword);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setAttributes(params);
            // 搜索场景需要主动唤起键盘；ADJUST_RESIZE 保证小屏上底部按钮不被键盘遮挡
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        EditText input = findViewById(R.id.et_search_keyword_input);
        input.setText(mInitialKeyword);
        input.setSelection(input.getText().length());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                confirm(input);
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_search_confirm).setOnClickListener(v -> confirm(input));
        findViewById(R.id.btn_search_clear).setOnClickListener(v -> {
            if (!beginAction()) {
                return;
            }
            dismiss();
            if (mListener != null) {
                mListener.onClear();
            }
        });
        findViewById(R.id.btn_search_cancel).setOnClickListener(v -> {
            if (!beginAction()) {
                return;
            }
            dismiss();
            if (mListener != null) {
                mListener.onCancel();
            }
        });

        // onCreate 阶段窗口尚未获得焦点，post 到下一帧再请求焦点与键盘，成功率更高
        input.post(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /**
     * 确认搜索：空白关键字按“清除筛选”处理，保证主列表能回到全量数据。
     */
    private void confirm(@NonNull EditText input) {
        if (!beginAction()) {
            return;
        }
        String keyword = input.getText() != null ? input.getText().toString().trim() : "";
        dismiss();
        if (mListener != null) {
            mListener.onConfirm(keyword);
        }
    }

    private boolean beginAction() {
        if (mActionDispatched) {
            return false;
        }
        mActionDispatched = true;
        return true;
    }
}
