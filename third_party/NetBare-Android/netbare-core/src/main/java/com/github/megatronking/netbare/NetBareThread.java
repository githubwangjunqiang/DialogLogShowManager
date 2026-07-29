/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.megatronking.netbare;

import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import com.github.megatronking.netbare.ip.IpAddress;
import com.github.megatronking.netbare.ip.IpHeader;
import com.github.megatronking.netbare.ip.Protocol;
import com.github.megatronking.netbare.net.UidDumper;
import com.github.megatronking.netbare.proxy.IcmpProxyServerForwarder;
import com.github.megatronking.netbare.proxy.ProxyServerForwarder;
import com.github.megatronking.netbare.proxy.TcpProxyServerForwarder;
import com.github.megatronking.netbare.proxy.UdpProxyServerForwarder;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A work thread running NetBare core logic. NetBase established the VPN connection is this thread
 * and read packets from the VPN file descriptor and transfer them to local proxy servers. Every
 * IP protocol runs an independent local proxy server to receive the packets.
 *
 * @author Megatron King
 * @since 2018-10-08 19:38
 */
/* package */ final class NetBareThread extends Thread {

	private final NetBareConfig mConfig;
	private final VpnService mVpnService;

	private ParcelFileDescriptor vpnDescriptor;
	private FileInputStream input;
	private FileOutputStream output;

	private PacketsTransfer packetsTransfer;
	/**
	 * 为什么这样做：只有虚拟网卡和代理都真正就绪后才广播 started，避免构建失败时上层短暂显示 RUNNING。
	 */
	private boolean serviceStartedNotified;

	/* package */ NetBareThread(VpnService vpnService, NetBareConfig config) {
		super("NetBare");
		this.mVpnService = vpnService;
		this.mConfig = config;
	}

	@Override
	public void interrupt() {
		super.interrupt();
		// 为什么这样做：关闭描述符足以唤醒阻塞读，代理集合统一由工作线程 finally 回收可避免和初始化过程并发修改。
		NetBareUtils.closeQuietly(vpnDescriptor);
		NetBareUtils.closeQuietly(input);
		NetBareUtils.closeQuietly(output);
	}

	@Override
	public void run() {
		super.run();
		try {
			packetsTransfer = new PacketsTransfer(mVpnService, mConfig);
			if (isInterrupted()) {
				return;
			}
			// Establish VPN, it runs a while loop unless failed.
			establishVpn(packetsTransfer);
		} catch (IOException e) {
			NetBareLog.wtf(e);
			notifyStartFailureIfNeed(e);
		} catch (RuntimeException e) {
			// 为什么这样做：Builder.establish 在授权失效、配置非法或系统限制下可能抛运行时异常，必须退出前台服务并把失败传给 SDK。
			NetBareLog.wtf(e);
			notifyStartFailureIfNeed(e);
		} finally {
			if (packetsTransfer != null) {
				packetsTransfer.stop();
			}
			NetBareUtils.closeQuietly(vpnDescriptor);
			NetBareUtils.closeQuietly(input);
			NetBareUtils.closeQuietly(output);
			if (serviceStartedNotified) {
				NetBare.get().notifyServiceStopped();
			}
		}
	}

	/**
	 * Report a failure that happened before the VPN became usable.
	 * 为什么这样做：线程层初始化失败不会回到 Service 的 try/catch，必须在这里结束前台服务并恢复上层 ERROR 状态。
	 */
	private void notifyStartFailureIfNeed(Throwable throwable) {
		if (serviceStartedNotified || isInterrupted()) {
			return;
		}
		NetBare.get().notifyServiceStartFailed(throwable);
		mVpnService.stopSelf();
	}

	private void establishVpn(PacketsTransfer packetsTransfer) {
		VpnService.Builder builder = mVpnService.new Builder();
		builder.setBlocking(true);
		builder.setMtu(mConfig.mtu);
		builder.addAddress(mConfig.address.address, mConfig.address.prefixLength);
		if (mConfig.session != null) {
			builder.setSession(mConfig.session);
		}
		if (mConfig.configureIntent != null) {
			builder.setConfigureIntent(mConfig.configureIntent);
		}
		for (IpAddress ip : mConfig.routes) {
			builder.addRoute(ip.address, ip.prefixLength);
		}
		for (String address : mConfig.dnsServers) {
			builder.addDnsServer(address);
		}
		for (String packageName : mConfig.allowedApplications) {
			try {
				builder.addAllowedApplication(packageName);
			} catch (PackageManager.NameNotFoundException e) {
				// 为什么这样做：单个动态目标未安装时只跳过该包，不能让后续有效目标和宿主 App 一起失效。
				NetBareLog.wtf(e);
			}
		}
		for (String packageName : mConfig.disallowedApplications) {
			try {
				builder.addDisallowedApplication(packageName);
			} catch (PackageManager.NameNotFoundException e) {
				NetBareLog.wtf(e);
			}
		}
		// Add self to allowed list.
		if (!mConfig.allowedApplications.isEmpty()
				&& !mConfig.allowedApplications.contains(mVpnService.getPackageName())) {
			try {
				builder.addAllowedApplication(mVpnService.getPackageName());
			} catch (PackageManager.NameNotFoundException e) {
				NetBareLog.wtf(e);
			}
		}
		vpnDescriptor = builder.establish();
		if (vpnDescriptor == null) {
			// 为什么这样做：系统未建立出虚拟网卡时不能让前台服务空转，转为失败才能让 SDK 状态和通知及时收敛。
			throw new IllegalStateException("VpnService.Builder.establish() returned null.");
		}

		// Open io with the VPN descriptor.
		FileDescriptor descriptor = vpnDescriptor.getFileDescriptor();
		if (descriptor == null) {
			throw new IllegalStateException("VPN file descriptor is null.");
		}
		input = new FileInputStream(descriptor);
		output = new FileOutputStream(descriptor);

		packetsTransfer.start();
		serviceStartedNotified = true;
		// 为什么这样做：只有虚拟网卡、文件流和代理服务器全部就绪后，SDK 的 RUNNING 状态才代表真实可抓包。
		NetBare.get().notifyServiceStarted();

		try {
			// Read packets from input io and forward them to proxy servers.
			while (!isInterrupted()) {
				packetsTransfer.transfer(input, output);
			}
		} catch (IOException e) {
			if (!isInterrupted()) {
				NetBareLog.wtf(e);
			}
		}
	}

	private static class PacketsTransfer {

		private final Map<Protocol, ProxyServerForwarder> mForwarderRegistry;

		private byte[] buffer;

		private PacketsTransfer(VpnService service, NetBareConfig config) throws IOException {
			int mtu = config.mtu;
			String localIp = config.address.address;
			UidDumper uidDumper = config.dumpUid ? new UidDumper(localIp, config.uidProvider) : null;
			// Register all supported protocols here.
			this.mForwarderRegistry = new LinkedHashMap<>(3);
			// TCP
			this.mForwarderRegistry.put(Protocol.TCP, new TcpProxyServerForwarder(service, localIp, mtu,
					uidDumper));
			// UDP
			this.mForwarderRegistry.put(Protocol.UDP, new UdpProxyServerForwarder(service, mtu,
					uidDumper));
			// ICMP
			this.mForwarderRegistry.put(Protocol.ICMP, new IcmpProxyServerForwarder());

			buffer = new byte[mtu];
		}

		private void start()  {
			for (ProxyServerForwarder forwarder : mForwarderRegistry.values()) {
				forwarder.prepare();
			}
		}

		private void stop() {
			for (ProxyServerForwarder forwarder : mForwarderRegistry.values()) {
				forwarder.release();
			}
			mForwarderRegistry.clear();
		}

		private void transfer(InputStream input, OutputStream output) throws IOException {
			// The thread would be blocked if there is no outgoing packets from input stream.
			transfer(buffer, input.read(buffer), output);
		}

		private void transfer(byte[] packet, int len, OutputStream output) {
			if (len < IpHeader.MIN_HEADER_LENGTH) {
				NetBareLog.w("Ip header length < " + IpHeader.MIN_HEADER_LENGTH);
				return;
			}
			IpHeader ipHeader = new IpHeader(packet, 0);
			Protocol protocol = Protocol.parse(ipHeader.getProtocol());
			ProxyServerForwarder forwarder = mForwarderRegistry.get(protocol);
			if (forwarder != null) {
				forwarder.forward(packet, len, output);
			} else {
				NetBareLog.w("Unknown ip protocol: " + ipHeader.getProtocol());
			}
		}

	}

}
