package com.xq.dialoglogshow.storage.db;

/**
 * VPN 日志表定义
 *
 * 为什么这样做：
 * 1. 把表名、列名和建索引语句收口到一个位置，后续数据库升级时不会在多个类里复制粘贴常量。
 * 2. 一期查询重点是按时间、host、protocol 检索，索引也在这里统一声明，便于对照计划书落地。
 */
public final class VpnTrafficLogTable {

    public static final String TABLE_NAME = "vpn_traffic_log";

    public static final String COLUMN_LOG_ID = "log_id";
    public static final String COLUMN_PROTOCOL = "protocol";
    public static final String COLUMN_HOST = "host";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_METHOD = "method";
    public static final String COLUMN_STATUS_CODE = "status_code";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";
    public static final String COLUMN_COST_TIME_MS = "cost_time_ms";
    public static final String COLUMN_REQUEST_BYTES = "request_bytes";
    public static final String COLUMN_RESPONSE_BYTES = "response_bytes";
    public static final String COLUMN_ENCRYPTED = "encrypted";
    public static final String COLUMN_PARSE_STATUS = "parse_status";
    public static final String COLUMN_REQUEST_HEADERS_JSON = "request_headers_json";
    public static final String COLUMN_REQUEST_BODY = "request_body";
    public static final String COLUMN_RESPONSE_HEADERS_JSON = "response_headers_json";
    public static final String COLUMN_RESPONSE_BODY = "response_body";
    public static final String COLUMN_ERROR_MESSAGE = "error_message";
    public static final String COLUMN_SOURCE_DESCRIPTION = "source_description";
    public static final String COLUMN_APP_PACKAGE_NAME = "app_package_name";

    public static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + COLUMN_LOG_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_PROTOCOL + " TEXT,"
                    + COLUMN_HOST + " TEXT,"
                    + COLUMN_URL + " TEXT,"
                    + COLUMN_METHOD + " TEXT,"
                    + COLUMN_STATUS_CODE + " TEXT,"
                    + COLUMN_TIMESTAMP + " INTEGER NOT NULL,"
                    + COLUMN_START_TIME + " INTEGER,"
                    + COLUMN_END_TIME + " INTEGER,"
                    + COLUMN_COST_TIME_MS + " INTEGER,"
                    + COLUMN_REQUEST_BYTES + " INTEGER,"
                    + COLUMN_RESPONSE_BYTES + " INTEGER,"
                    + COLUMN_ENCRYPTED + " INTEGER,"
                    + COLUMN_PARSE_STATUS + " TEXT,"
                    + COLUMN_REQUEST_HEADERS_JSON + " TEXT,"
                    + COLUMN_REQUEST_BODY + " TEXT,"
                    + COLUMN_RESPONSE_HEADERS_JSON + " TEXT,"
                    + COLUMN_RESPONSE_BODY + " TEXT,"
                    + COLUMN_ERROR_MESSAGE + " TEXT,"
                    + COLUMN_SOURCE_DESCRIPTION + " TEXT,"
                    + COLUMN_APP_PACKAGE_NAME + " TEXT"
                    + ");";

    public static final String CREATE_INDEX_TIMESTAMP_SQL =
            "CREATE INDEX IF NOT EXISTS idx_vpn_traffic_log_timestamp ON "
                    + TABLE_NAME + "(" + COLUMN_TIMESTAMP + ");";

    public static final String CREATE_INDEX_HOST_SQL =
            "CREATE INDEX IF NOT EXISTS idx_vpn_traffic_log_host ON "
                    + TABLE_NAME + "(" + COLUMN_HOST + ");";

    public static final String CREATE_INDEX_PROTOCOL_SQL =
            "CREATE INDEX IF NOT EXISTS idx_vpn_traffic_log_protocol ON "
                    + TABLE_NAME + "(" + COLUMN_PROTOCOL + ");";

    /**
     * 为什么这样做：多 App 查询同时固定按时间倒序，组合索引能避免先扫描整张日志表再过滤包名。
     */
    public static final String CREATE_INDEX_APP_TIMESTAMP_SQL =
            "CREATE INDEX IF NOT EXISTS idx_vpn_traffic_log_app_timestamp ON "
                    + TABLE_NAME + "(" + COLUMN_APP_PACKAGE_NAME + ","
                    + COLUMN_TIMESTAMP + " DESC);";

    private VpnTrafficLogTable() {
    }
}
