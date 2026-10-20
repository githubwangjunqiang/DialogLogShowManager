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
package com.github.megatronking.netbare.http;

import androidx.annotation.NonNull;

import com.github.megatronking.netbare.gateway.VirtualGateway;
import com.github.megatronking.netbare.gateway.VirtualGatewayFactory;
import com.github.megatronking.netbare.gateway.Request;
import com.github.megatronking.netbare.gateway.Response;
import com.github.megatronking.netbare.net.Session;
import com.github.megatronking.netbare.ssl.JKS;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link VirtualGatewayFactory} that produces the {@link HttpVirtualGateway}.
 *
 * @author Megatron King
 * @since 2018-11-20 23:50
 */
public class HttpVirtualGatewayFactory implements VirtualGatewayFactory {

    private List<HttpInterceptorFactory> mFactories;
    private JKS mJKS;
    private HttpsFallbackLogCallback mFallbackLogCallback;

    /**
     * Constructs a {@link HttpVirtualGatewayFactory} instance with {@link JKS} and a collection of
     * {@link HttpInterceptorFactory}.
     *
     * @param factories a collection of {@link HttpInterceptorFactory}.
     * @return A instance of {@link HttpVirtualGatewayFactory}.
     */
    public HttpVirtualGatewayFactory(@NonNull JKS jks,
                                     @NonNull List<HttpInterceptorFactory> factories) {
        this(jks, factories, null);
    }

    /**
     * Constructs a {@link HttpVirtualGatewayFactory} instance with {@link JKS}, a collection of
     * {@link HttpInterceptorFactory} and an optional HTTPS fallback callback.
     *
     * 为什么这样做：没有调试证书时，核心库仍然要把连接级元数据回传给宿主，
     * 这样宿主可以在自己的仓库里保存“已经拦到 HTTPS 请求”这一事实。
     */
    public HttpVirtualGatewayFactory(@NonNull JKS jks,
                                     @NonNull List<HttpInterceptorFactory> factories,
                                     @NonNull HttpsFallbackLogCallback fallbackLogCallback) {
        this.mJKS = jks;
        this.mFactories = factories;
        this.mFallbackLogCallback = fallbackLogCallback;
    }

    @Override
    public VirtualGateway create(Session session, Request request, Response response) {
        return new HttpVirtualGateway(session, request, response, mJKS,
                new ArrayList<>(mFactories), mFallbackLogCallback);
    }

    /**
     * Create a {@link HttpVirtualGatewayFactory} instance with {@link JKS} and a collection of
     * {@link HttpInterceptorFactory}.
     *
     * @param factories a collection of {@link HttpInterceptorFactory}.
     * @return A instance of {@link HttpVirtualGatewayFactory}.
     */
    public static VirtualGatewayFactory create(@NonNull JKS authority,
                                               @NonNull List<HttpInterceptorFactory> factories) {
        return new HttpVirtualGatewayFactory(authority, factories);
    }

}
