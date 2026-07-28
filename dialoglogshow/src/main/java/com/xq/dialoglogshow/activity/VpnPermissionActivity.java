package com.xq.dialoglogshow.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.collector.vpn.VpnController;
import com.xq.dialoglogshow.collector.vpn.VpnStatusManager;

/**
 * VPN 权限申请中转页
 *
 * 为什么这样做：
 * 1. `VpnService.prepare(...)` 必须通过 Activity 与系统交互，而控制台弹窗和 SDK 初始化入口未必持有 Activity 上下文，因此需要一个透明中转页代为申请。
 * 2. 把权限申请动作隔离出来后，控制台只关心“开始申请”这一件事，避免把系统授权细节耦合进弹窗代码。
 */
public class VpnPermissionActivity extends Activity {

    private static final int REQUEST_CODE_PREPARE_VPN = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            VpnController.getInstance().startVpn(this);
            finish();
            return;
        }
        startActivityForResult(prepareIntent, REQUEST_CODE_PREPARE_VPN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PREPARE_VPN) {
            finish();
            return;
        }
        if (resultCode == RESULT_OK) {
            VpnController.getInstance().startVpn(this);
            Toast.makeText(this, R.string.show_sdk_vpn_permission_granted, Toast.LENGTH_SHORT).show();
        } else {
            VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.PREPARE_REQUIRED, getString(R.string.show_sdk_vpn_permission_denied));
            Toast.makeText(this, R.string.show_sdk_vpn_permission_denied, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
