package com.xq.dialoglogshow.collector.proxy;

import com.xq.dialoglogshow.collector.JavaHttpLogCollector;
import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.collector.parser.RawHttpStreamParser;
import com.xq.dialoglogshow.collector.stream.CopyInputStream;
import com.xq.dialoglogshow.collector.stream.CopyOutputStream;
import com.xq.dialoglogshow.entity.HttpLogData;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;

/**
 * HTTP 80 端口 TCP SocketImpl 代理类
 *
 * 为什么这样做：
 * 1. 代理 Java 标准库底层 SocketImpl，在连接建立、流传输以及 Socket 关闭时进行旁路拦截。
 * 2. 只有在 Socket 80 端口（普通 HTTP 请求）且数据流读写结束时，触发异步报文解析，推送到 JavaHttpLogCollector。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class ProxySocketImpl extends SocketImpl {

    private final SocketImpl mRealImpl;
    private CopyInputStream mCopyInputStream;
    private CopyOutputStream mCopyOutputStream;
    private long mStartTime = 0;
    private int mPort = 0;

    public ProxySocketImpl(SocketImpl realImpl) {
        this.mRealImpl = realImpl;
    }

    @Override
    protected void create(boolean stream) throws IOException {
        invokeRealMethod("create", boolean.class, stream);
    }

    @Override
    protected void connect(String host, int port) throws IOException {
        this.mPort = port;
        this.mStartTime = System.currentTimeMillis();
        invokeRealMethod("connect", new Class<?>[]{String.class, int.class}, host, port);
    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        this.mPort = port;
        this.mStartTime = System.currentTimeMillis();
        invokeRealMethod("connect", new Class<?>[]{InetAddress.class, int.class}, address, port);
    }

    @Override
    protected void connect(SocketAddress address, int timeout) throws IOException {
        this.mStartTime = System.currentTimeMillis();
        invokeRealMethod("connect", new Class<?>[]{SocketAddress.class, int.class}, address, timeout);
    }

    @Override
    protected void bind(InetAddress host, int port) throws IOException {
        invokeRealMethod("bind", new Class<?>[]{InetAddress.class, int.class}, host, port);
    }

    @Override
    protected void listen(int backlog) throws IOException {
        invokeRealMethod("listen", int.class, backlog);
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        invokeRealMethod("accept", SocketImpl.class, s);
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        InputStream originalIn = (InputStream) invokeRealMethod("getInputStream");
        if (mCopyInputStream == null && originalIn != null) {
            mCopyInputStream = new CopyInputStream(originalIn, this::collectLogIfNeed);
        }
        return mCopyInputStream != null ? mCopyInputStream : originalIn;
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        OutputStream originalOut = (OutputStream) invokeRealMethod("getOutputStream");
        if (mCopyOutputStream == null && originalOut != null) {
            mCopyOutputStream = new CopyOutputStream(originalOut);
        }
        return mCopyOutputStream != null ? mCopyOutputStream : originalOut;
    }

    @Override
    protected int available() throws IOException {
        Integer res = (Integer) invokeRealMethod("available");
        return res != null ? res : 0;
    }

    @Override
    protected void close() throws IOException {
        try {
            // Socket 关闭时提交捕获到的日志
            collectLogIfNeed();
        } catch (Throwable ignored) {
        }
        invokeRealMethod("close");
    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        invokeRealMethod("sendUrgentData", int.class, data);
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
        mRealImpl.setOption(optID, value);
    }

    @Override
    public Object getOption(int optID) throws SocketException {
        return mRealImpl.getOption(optID);
    }

    private boolean mIsCollected = false;

    /**
     * 提取旁路镜像字节流并提交解析
     */
    private synchronized void collectLogIfNeed() {
        if (mIsCollected) {
            return;
        }
        byte[] reqBytes = mCopyOutputStream != null ? mCopyOutputStream.getCopiedData() : null;
        byte[] resBytes = mCopyInputStream != null ? mCopyInputStream.getCopiedData() : null;

        boolean hasReqData = reqBytes != null && reqBytes.length > 0;
        boolean hasResData = resBytes != null && resBytes.length > 0;

        if (hasReqData || hasResData) {
            long cost = System.currentTimeMillis() - mStartTime;
            AutoHttpLogModel logModel = RawHttpStreamParser.parse(reqBytes, resBytes, cost, false);
            if (logModel != null && logModel.getStatusCode() != null) {
                mIsCollected = true;
                android.util.Log.d("ProxySocketImpl", "collectLogIfNeed: PUSH AUTO LOG SUCCESS -> " + logModel.getUrl());
                JavaHttpLogCollector.getInstance().addAutoLog(logModel);
            }
        }
    }

    /**
     * 反射调用真实 SocketImpl 方法，防受保护方法限制
     */
    private Object invokeRealMethod(String methodName, Class<?>[] paramTypes, Object... args) throws IOException {
        try {
            java.lang.reflect.Method method = SocketImpl.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(mRealImpl, args);
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("ProxySocketImpl invoke method failed: " + methodName, e);
        }
    }

    private Object invokeRealMethod(String methodName, Class<?> paramType, Object arg) throws IOException {
        return invokeRealMethod(methodName, new Class<?>[]{paramType}, arg);
    }

    private Object invokeRealMethod(String methodName) throws IOException {
        return invokeRealMethod(methodName, new Class<?>[]{});
    }
}
