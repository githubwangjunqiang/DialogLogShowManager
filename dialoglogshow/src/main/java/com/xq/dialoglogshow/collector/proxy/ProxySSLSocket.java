package com.xq.dialoglogshow.collector.proxy;

import com.xq.dialoglogshow.collector.JavaHttpLogCollector;
import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.collector.parser.RawHttpStreamParser;
import com.xq.dialoglogshow.collector.stream.CopyInputStream;
import com.xq.dialoglogshow.collector.stream.CopyOutputStream;
import com.xq.dialoglogshow.entity.HttpLogData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * HTTPS 443 端口 SSLSocket 代理类
 *
 * 为什么这样做：
 * 1. 在 TLS 握手成功（startHandshake）之后，代理输入/输出流，拿到 TLS 解密后的 HTTP 明文数据。
 * 2. 握手与证书信任链 100% 委派给真实 SSLSocket 执行，零安全风险。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class ProxySSLSocket extends SSLSocket {

    private final SSLSocket mRealSSLSocket;
    private CopyInputStream mCopyInputStream;
    private CopyOutputStream mCopyOutputStream;
    private long mStartTime = System.currentTimeMillis();
    private boolean mHandshakeDone = false;

    public ProxySSLSocket(SSLSocket realSSLSocket) {
        this.mRealSSLSocket = realSSLSocket;
    }

    @Override
    public void startHandshake() throws IOException {
        mRealSSLSocket.startHandshake();
        mHandshakeDone = true;
    }

    private void ensureHandshake() {
        if (!mHandshakeDone) {
            try {
                startHandshake();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ensureHandshake();
        android.util.Log.d("ProxySSLSocket", "getInputStream called!");
        InputStream originalIn = mRealSSLSocket.getInputStream();
        if (mCopyInputStream == null && originalIn != null) {
            mCopyInputStream = new CopyInputStream(originalIn, this::collectHttpsLogIfNeed);
        }
        return mCopyInputStream != null ? mCopyInputStream : originalIn;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        ensureHandshake();
        android.util.Log.d("ProxySSLSocket", "getOutputStream called!");
        OutputStream originalOut = mRealSSLSocket.getOutputStream();
        if (mCopyOutputStream == null && originalOut != null) {
            mCopyOutputStream = new CopyOutputStream(originalOut);
        }
        return mCopyOutputStream != null ? mCopyOutputStream : originalOut;
    }

    private boolean mIsCollected = false;

    @Override
    public void close() throws IOException {
        try {
            collectHttpsLogIfNeed();
        } catch (Throwable ignored) {
        }
        mRealSSLSocket.close();
    }

    private synchronized void collectHttpsLogIfNeed() {
        if (mIsCollected) {
            return;
        }
        byte[] reqBytes = mCopyOutputStream != null ? mCopyOutputStream.getCopiedData() : null;
        byte[] resBytes = mCopyInputStream != null ? mCopyInputStream.getCopiedData() : null;

        boolean hasReqData = reqBytes != null && reqBytes.length > 0;
        boolean hasResData = resBytes != null && resBytes.length > 0;

        if (hasReqData || hasResData) {
            long cost = System.currentTimeMillis() - mStartTime;
            AutoHttpLogModel logModel = RawHttpStreamParser.parse(reqBytes, resBytes, cost, true);
            if (logModel != null && logModel.getStatusCode() != null) {
                mIsCollected = true;
                android.util.Log.d("ProxySSLSocket", "collectHttpsLogIfNeed: PUSH AUTO LOG SUCCESS -> " + logModel.getUrl());
                JavaHttpLogCollector.getInstance().addAutoLog(logModel);
            }
        }
    }

    // ---------------- 以下方法全部透传委派给底层真实 SSLSocket ----------------

    @Override
    public String[] getSupportedCipherSuites() {
        return mRealSSLSocket.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return mRealSSLSocket.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        mRealSSLSocket.setEnabledCipherSuites(suites);
    }

    @Override
    public String[] getSupportedProtocols() {
        return mRealSSLSocket.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return mRealSSLSocket.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        mRealSSLSocket.setEnabledProtocols(protocols);
    }

    @Override
    public SSLSession getSession() {
        return mRealSSLSocket.getSession();
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        mRealSSLSocket.addHandshakeCompletedListener(listener);
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        mRealSSLSocket.removeHandshakeCompletedListener(listener);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        mRealSSLSocket.setUseClientMode(mode);
    }

    @Override
    public boolean getUseClientMode() {
        return mRealSSLSocket.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        mRealSSLSocket.setNeedClientAuth(need);
    }

    @Override
    public boolean getNeedClientAuth() {
        return mRealSSLSocket.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        mRealSSLSocket.setWantClientAuth(want);
    }

    @Override
    public boolean getWantClientAuth() {
        return mRealSSLSocket.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        mRealSSLSocket.setEnableSessionCreation(flag);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return mRealSSLSocket.getEnableSessionCreation();
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        mRealSSLSocket.connect(endpoint, timeout);
    }
}
