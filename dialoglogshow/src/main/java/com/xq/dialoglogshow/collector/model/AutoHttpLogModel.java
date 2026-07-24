package com.xq.dialoglogshow.collector.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自动网络日志专用独立数据模型
 * 
 * 为什么这样做：
 * 1. 彻底与存量老的 BaseShowData / HttpLogData 解耦，不污染老的展示体系。
 * 2. 结构化保存 HTTP 请求全生命周期数据，完美支持单列表模式与父子树状模式。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/24
 */
public class AutoHttpLogModel {

    private int index;
    private String url;
    private String method;
    private String statusCode;
    private long time;
    private long costTimeMs;
    private boolean isHttps;
    private int reqSize;
    private int resSize;

    private Map<String, String> requestHeaders;
    private String requestBody;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private String curlCommand;

    // 模式扩展字段
    private boolean isGroupParent = false;
    private boolean isExpanded = false;
    private List<AutoHttpLogModel> childList = new ArrayList<>();

    public AutoHttpLogModel() {
        this.time = System.currentTimeMillis();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getCostTimeMs() {
        return costTimeMs;
    }

    public void setCostTimeMs(long costTimeMs) {
        this.costTimeMs = costTimeMs;
    }

    public boolean isHttps() {
        return isHttps;
    }

    public void setHttps(boolean https) {
        isHttps = https;
    }

    public int getReqSize() {
        return reqSize;
    }

    public void setReqSize(int reqSize) {
        this.reqSize = reqSize;
    }

    public int getResSize() {
        return resSize;
    }

    public void setResSize(int resSize) {
        this.resSize = resSize;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getCurlCommand() {
        return curlCommand;
    }

    public void setCurlCommand(String curlCommand) {
        this.curlCommand = curlCommand;
    }

    public boolean isGroupParent() {
        return isGroupParent;
    }

    public void setGroupParent(boolean groupParent) {
        isGroupParent = groupParent;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public List<AutoHttpLogModel> getChildList() {
        return childList;
    }

    public void setChildList(List<AutoHttpLogModel> childList) {
        this.childList = childList;
    }

    /**
     * 生成完整的报文详情综合文本
     */
    public String getFullDetailText() {
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("================【请求概览】================\n")
                .append("URL: ").append(url != null ? url : "").append("\n")
                .append("Method: ").append(method != null ? method : "GET").append("\n")
                .append("Status: ").append(statusCode != null ? statusCode : "200 OK").append("\n")
                .append("Protocol: ").append(isHttps ? "HTTPS" : "HTTP").append("\n")
                .append("Cost Time: ").append(costTimeMs).append(" ms\n")
                .append("Req Size: ").append(reqSize).append(" bytes\n")
                .append("Res Size: ").append(resSize).append(" bytes\n\n");

        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            fullContent.append("================【Request Headers】================\n");
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                fullContent.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            fullContent.append("\n");
        }

        if (!TextUtils.isEmpty(requestBody)) {
            fullContent.append("================【Request Body】================\n")
                    .append(requestBody).append("\n\n");
        }

        if (!TextUtils.isEmpty(responseBody)) {
            fullContent.append("================【Response Body】================\n")
                    .append(responseBody).append("\n\n");
        }

        if (!TextUtils.isEmpty(curlCommand)) {
            fullContent.append("================【cURL 调试命令】================\n")
                    .append(curlCommand);
        }

        return fullContent.toString();
    }
}
