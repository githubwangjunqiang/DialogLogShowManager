package com.xq.dialoglogshow.collector.vpn.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshow.storage.entity.VpnTrafficLogEntity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * VPN 日志模型映射器
 *
 * 为什么这样做：
 * 1. 数据库存储与 UI 展示字段隔离后，所有序列化和反序列化必须走同一入口，避免 JSON 结构散落在各层各处。
 * 2. 一期先支持最基础的 Map-JSON 转换，后续如果要换成更稳定的序列化方式，只改这里即可。
 */
public class VpnTrafficLogMapper {

    @NonNull
    public VpnTrafficLogEntity toEntity(@NonNull VpnTrafficLogModel model) {
        VpnTrafficLogEntity entity = new VpnTrafficLogEntity();
        entity.logId = model.logId;
        entity.protocol = model.protocol;
        entity.host = model.host;
        entity.url = model.url;
        entity.method = model.method;
        entity.statusCode = model.statusCode;
        entity.timestamp = model.timestamp;
        entity.startTime = model.startTime;
        entity.endTime = model.endTime;
        entity.costTimeMs = model.costTimeMs;
        entity.requestBytes = model.requestBytes;
        entity.responseBytes = model.responseBytes;
        entity.encrypted = model.encrypted ? 1 : 0;
        entity.parseStatus = model.parseStatus;
        entity.requestHeadersJson = toJson(model.requestHeaders);
        entity.requestBody = model.requestBody;
        entity.responseHeadersJson = toJson(model.responseHeaders);
        entity.responseBody = model.responseBody;
        entity.errorMessage = model.errorMessage;
        entity.sourceDescription = model.sourceDescription;
        entity.appPackageName = model.appPackageName;
        return entity;
    }

    @NonNull
    public VpnTrafficLogModel toModel(@NonNull VpnTrafficLogEntity entity) {
        VpnTrafficLogModel model = new VpnTrafficLogModel();
        model.logId = entity.logId;
        model.protocol = entity.protocol;
        model.host = entity.host;
        model.url = entity.url;
        model.method = entity.method;
        model.statusCode = entity.statusCode;
        model.timestamp = entity.timestamp;
        model.startTime = entity.startTime;
        model.endTime = entity.endTime;
        model.costTimeMs = entity.costTimeMs;
        model.requestBytes = entity.requestBytes;
        model.responseBytes = entity.responseBytes;
        model.encrypted = entity.encrypted == 1;
        model.parseStatus = entity.parseStatus;
        model.requestHeaders = fromJson(entity.requestHeadersJson);
        model.requestBody = entity.requestBody;
        model.responseHeaders = fromJson(entity.responseHeadersJson);
        model.responseBody = entity.responseBody;
        model.errorMessage = entity.errorMessage;
        model.sourceDescription = entity.sourceDescription;
        model.appPackageName = entity.appPackageName;
        return model;
    }

    @Nullable
    private String toJson(@Nullable Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new JSONObject(map).toString();
    }

    @Nullable
    private Map<String, String> fromJson(@Nullable String json) {
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JSONObject jsonObject = new JSONObject(json);
            HashMap<String, String> resultMap = new HashMap<>();
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                resultMap.put(key, jsonObject.optString(key));
            }
            return resultMap;
        } catch (JSONException ignored) {
            return null;
        }
    }
}
