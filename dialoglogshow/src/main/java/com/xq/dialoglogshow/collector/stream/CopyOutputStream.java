package com.xq.dialoglogshow.collector.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 旁路镜像 OutputStream 包装类
 * 
 * 为什么这样做：
 * 1. 宿主 App 在向服务器写出请求数据（如 POST/PUT 的 Header 与 Body）时，本类在将数据透传给原始网络 Socket 的同时，在旁路复制一份。
 * 2. 限制最大镜像容量为 500KB，防止大文件上传时耗尽内存。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class CopyOutputStream extends OutputStream {

    private static final int MAX_COPY_SIZE = 500 * 1024;

    private final OutputStream mOriginalStream;
    private final ByteArrayOutputStream mCopiedBuffer = new ByteArrayOutputStream();
    private boolean mOverflow = false;

    /**
     * 构造旁路镜像输出流
     *
     * @param originalStream 宿主原始网络输出流
     */
    public CopyOutputStream(OutputStream originalStream) {
        this.mOriginalStream = originalStream;
    }

    @Override
    public void write(int b) throws IOException {
        mOriginalStream.write(b);
        if (!mOverflow) {
            if (mCopiedBuffer.size() < MAX_COPY_SIZE) {
                mCopiedBuffer.write(b);
            } else {
                mOverflow = true;
            }
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        mOriginalStream.write(b, off, len);
        if (!mOverflow) {
            if (mCopiedBuffer.size() + len <= MAX_COPY_SIZE) {
                mCopiedBuffer.write(b, off, len);
            } else {
                int remaining = MAX_COPY_SIZE - mCopiedBuffer.size();
                if (remaining > 0) {
                    mCopiedBuffer.write(b, off, remaining);
                }
                mOverflow = true;
            }
        }
    }

    /**
     * 获取已被镜像复制的数据字节数组
     */
    public byte[] getCopiedData() {
        return mCopiedBuffer.toByteArray();
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        mOriginalStream.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        mOriginalStream.close();
    }
}
