---
name: adb-wireless-debug
description: 用户说“无线连接手机”时使用。
---

# Android 无线 ADB

## 1. 安全前置

1. 先执行 `adb devices -l`。
2. 已存在目标无线 endpoint 且状态为 `device`时直接验证，不重复连接。
3. 存在目标 USB serial 时，可按“USB 转 TCP/IP”流程操作该 serial。
4. 没有可用目标设备时，再按 Android 11+ 配对码流程获取配对端口、连接端口和配对码。
5. 多设备、模拟器与真机共存时，所有设备级命令必须带 `-s <serial>`。
6. 记录本次目标 endpoint，后续验证和断开只操作该 endpoint。

禁止默认执行：

```bash
adb disconnect
adb kill-server
```

无参数 `adb disconnect`会断开全部无线设备；`kill-server`会影响其他项目和会话。

## 2. Android 11+ 配对码方式

先向用户分别确认：

- 设备 IP；
- “使用配对码配对设备”页面显示的配对端口和配对码；
- “无线调试”主页面显示的连接端口。

配对端口与连接端口通常不同，禁止复用猜测。

```bash
adb pair <ip>:<pair-port>
adb connect <ip>:<connect-port>
adb -s <ip>:<connect-port> get-state
```

成功标准：最后一条输出 `device`，且 `adb devices -l`中目标 endpoint 状态为 `device`。

## 3. USB 转 TCP/IP

先从 `adb devices -l`确定 USB serial：

```bash
adb -s <usb-serial> get-state
adb -s <usb-serial> tcpip 5555
adb -s <usb-serial> shell ip route
adb connect <device-ip>:5555
adb -s <device-ip>:5555 get-state
```

禁止在多设备环境执行不带 `-s`的 `adb tcpip 5555`。

## 4. 定向排障

按顺序检查：

1. 电脑和设备是否在允许互通的同一网络；
2. endpoint 的端口是否仍是设备当前显示值；
3. `adb devices -l`是否为 `offline`或`unauthorized`；
4. VPN、防火墙、访客网络或 AP isolation 是否阻断；
5. 只重连目标设备：

```bash
adb disconnect <ip>:<port>
adb connect <ip>:<port>
adb -s <ip>:<port> get-state
```

`unauthorized`需要用户在设备端确认授权。配对信息失效时重新获取新配对端口和配对码。

只有上述检查均失败、且用户确认没有其他 ADB 会话时，才允许把 `adb kill-server && adb start-server`作为最后方案。

## 5. 完成与断开

完成时报告目标 serial、endpoint 和最终状态。仅断开本次连接：

```bash
adb disconnect <ip>:<port>
```

不得声称“已连接”而未执行带 `-s`的 `get-state`验证。
