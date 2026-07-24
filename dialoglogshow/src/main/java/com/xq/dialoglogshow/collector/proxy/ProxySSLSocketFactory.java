package com.xq.dialoglogshow.collector.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SSLSocketFactory 全局代理类
 *
 * 为什么这样做：
 * 1. 代理 JVM / Android 默认的 SSLSocketFactory，为创建的所有 SSLSocket 套上一层 ProxySSLSocket。
 * 2. 对外透传原始 SSLSocketFactory 的全部逻辑，防破坏系统 TLS 校验。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class ProxySSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory mDelegate;

    public ProxySSLSocketFactory(SSLSocketFactory delegate) {
        this.mDelegate = delegate;
    }

    public SSLSocketFactory getDelegate() {
        return mDelegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return mDelegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        android.util.Log.d("ProxySSLSocketFactory", "createSocket wrapped: host=" + host + ", port=" + port);
        Socket realSocket = mDelegate.createSocket(s, host, port, autoClose);
        if (realSocket instanceof SSLSocket) {
            return new ProxySSLSocket((SSLSocket) realSocket);
        }
        return realSocket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        android.util.Log.d("ProxySSLSocketFactory", "createSocket: host=" + host + ", port=" + port);
        Socket realSocket = mDelegate.createSocket(host, port);
        if (realSocket instanceof SSLSocket) {
            return new ProxySSLSocket((SSLSocket) realSocket);
        }
        return realSocket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        android.util.Log.d("ProxySSLSocketFactory", "createSocket: host=" + host + ", port=" + port);
        Socket realSocket = mDelegate.createSocket(host, port, localHost, localPort);
        if (realSocket instanceof SSLSocket) {
            return new ProxySSLSocket((SSLSocket) realSocket);
        }
        return realSocket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        android.util.Log.d("ProxySSLSocketFactory", "createSocket: host=" + host + ", port=" + port);
        Socket realSocket = mDelegate.createSocket(host, port);
        if (realSocket instanceof SSLSocket) {
            return new ProxySSLSocket((SSLSocket) realSocket);
        }
        return realSocket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        android.util.Log.d("ProxySSLSocketFactory", "createSocket: address=" + address + ", port=" + port);
        Socket realSocket = mDelegate.createSocket(address, port, localAddress, localPort);
        if (realSocket instanceof SSLSocket) {
            return new ProxySSLSocket((SSLSocket) realSocket);
        }
        return realSocket;
    }
}
