package com.xq.dialoglogshow.collector.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 旁路镜像 InputStream 包装类
 * 
 * 为什么这样做：
 * 1. 网络数据流是一次性读取的，直接读取宿主的 InputStream 会导致宿主业务读不到数据。
 * 2. 通过重写 read 方法，在将数据正常返回给宿主的同时，旁路复制一份到内存 ByteArrayOutputStream 中。
 * 3. 增加 MAX_COPY_SIZE（500KB）硬上限，避免大文件/视频下载导致内存溢出 (OOM)。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class CopyInputStream extends InputStream {

    // 默认单条流复制的最大硬上限 (500KB)，超过后停止复制以保护堆内存
    private static final int MAX_COPY_SIZE = 500 * 1024;

    public interface OnStreamCompleteCallback {
        void onComplete();
    }

    private final InputStream mOriginalStream;
    private final ByteArrayOutputStream mCopiedBuffer = new ByteArrayOutputStream();
    private boolean mOverflow = false;
    private final OnStreamCompleteCallback mCallback;

    /**
     * 构造旁路镜像输入流
     *
     * @param originalStream 宿主原始网络输入流
     */
    public CopyInputStream(InputStream originalStream) {
        this(originalStream, null);
    }

    /**
     * 构造带完成回调的旁路镜像输入流
     */
    public CopyInputStream(InputStream originalStream, OnStreamCompleteCallback callback) {
        this.mOriginalStream = originalStream;
        this.mCallback = callback;
    }

    @Override
    public int read() throws IOException {
        int b = mOriginalStream.read();
        if (b != -1 && !mOverflow) {
            if (mCopiedBuffer.size() < MAX_COPY_SIZE) {
                mCopiedBuffer.write(b);
            } else {
                mOverflow = true;
            }
        } else if (b == -1) {
            notifyComplete();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = mOriginalStream.read(b, off, len);
        if (bytesRead > 0 && !mOverflow) {
            if (mCopiedBuffer.size() + bytesRead <= MAX_COPY_SIZE) {
                mCopiedBuffer.write(b, off, bytesRead);
            } else {
                int remaining = MAX_COPY_SIZE - mCopiedBuffer.size();
                if (remaining > 0) {
                    mCopiedBuffer.write(b, off, remaining);
                }
                mOverflow = true;
            }
            notifyComplete();
        } else if (bytesRead == -1) {
            notifyComplete();
        }
        return bytesRead;
    }

    private void notifyComplete() {
        if (mCallback != null) {
            mCallback.onComplete();
        }
    }

    /**
     * 获取已被镜像复制的数据字节数组
     *
     * @return 缓存的字节数组
     */
    public byte[] getCopiedData() {
        return mCopiedBuffer.toByteArray();
    }

    /**
     * 是否触发了最大复制限制
     */
    public boolean isOverflow() {
        return mOverflow;
    }

    @Override
    public void close() throws IOException {
        notifyComplete();
        super.close();
        mOriginalStream.close();
    }

    @Override
    public int available() throws IOException {
        return mOriginalStream.available();
    }
}
