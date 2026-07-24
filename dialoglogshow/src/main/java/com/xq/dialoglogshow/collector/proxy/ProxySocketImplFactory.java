package com.xq.dialoglogshow.collector.proxy;

import java.net.SocketImpl;
import java.net.SocketImplFactory;

/**
 * SocketImpl 代理工厂
 *
 * 为什么这样做：
 * 1. 替换系统全局 SocketImplFactory，拦截普通 HTTP (80 端口) 套接字创建。
 * 2. 内部保持一个默认的原生 SocketImpl 生成机制，确保所有未代理的 TCP 连接原样正常透传。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class ProxySocketImplFactory implements SocketImplFactory {

    private final Class<?> mDefaultSocketImplClass;

    public ProxySocketImplFactory() {
        Class<?> clazz = null;
        try {
            // 获取系统 PlainSocketImpl 默认类
            clazz = Class.forName("java.net.PlainSocketImpl");
        } catch (ClassNotFoundException ignored) {
            try {
                clazz = Class.forName("java.net.SocksSocketImpl");
            } catch (ClassNotFoundException ignored2) {
            }
        }
        this.mDefaultSocketImplClass = clazz;
    }

    @Override
    public SocketImpl createSocketImpl() {
        try {
            if (mDefaultSocketImplClass != null) {
                java.lang.reflect.Constructor<?> constructor = mDefaultSocketImplClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                SocketImpl realImpl = (SocketImpl) constructor.newInstance();
                return new ProxySocketImpl(realImpl);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
