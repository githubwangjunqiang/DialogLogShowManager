package com.xq.dialoglogshow.core.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;

/**
 * 非 OkHttp 或自定义网络库统一上报契约接口。
 * 为什么全部采用纯抽象函数：
 * 绝不使用 default，确保开发者在 IDE 中一键（Alt + Enter）自动生成全部方法骨架。
 * 所有属性必须强制显式实现，有值返回值，无值明确返回 null，彻底告别翻查文档！
 */
public interface HttpLogPayload {

    /**
     * 1. 请求完整 URL (必填，不可为 null)。
     * 例如："https://api.example.com/v1/user/login"
     */
    @NonNull
    String getUrl();

    /**
     * 2. 请求方式 (必填，不可为 null)。
     * 例如："GET", "POST", "PUT", "DELETE"
     */
    @NonNull
    String getMethod();

    /**
     * 3. HTTP 传输层响应状态码。
     * 例如：200, 404, 500；若网络断开或握手失败未收到响应则传 -1。
     */
    int getHttpCode();

    /**
     * 4. 接口返回的业务提示信息、错误说明或状态描述 (必填，不可为 null)。
     * 例如："OK", "登录成功", "token已过期", "无法连接到服务器"
     */
    @NonNull
    String getMessage();

    /**
     * 5. 接口本次调用业务上是否算作成功。
     * 注意：控制台的状态指示、红绿染色以及“成功/失败”筛选分类将严格以此字段为金标准！
     * 例如：即使 HTTP 状态码为 200，但业务报文返回 code != 0，此处也应返回 false。
     */
    boolean isSuccess();

    /**
     * 6. 本次请求完整耗时（毫秒）。
     */
    long getDurationMs();

    /**
     * 7. 请求 Header 字典（若无自定义请求头，明确 return null 即可）。
     */
    @Nullable
    Map<String, String> getRequestHeaders();

    /**
     * 8. 响应 Header 字典（若无响应头，明确 return null 即可）。
     */
    @Nullable
    Map<String, String> getResponseHeaders();

    /**
     * 9. 请求报文体 (JSONObject 对象；GET 请求或无请求体明确 return null 即可)。
     * 内部会自动将其转换为格式化 JSON 字符串持久化。
     */
    @Nullable
    JSONObject getRequestBody();

    /**
     * 10. 响应报文体 (JSONObject 对象；无响应体明确 return null 即可)。
     * 内部会自动将其转换为格式化 JSON 字符串持久化。
     */
    @Nullable
    JSONObject getResponseBody();

    /**
     * 11. 异常对象（若抛出 Exception/Throwable 传入此对象；成功请求明确 return null 即可）。
     */
    @Nullable
    Throwable getThrowable();
}
