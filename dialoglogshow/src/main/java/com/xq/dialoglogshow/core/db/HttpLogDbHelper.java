package com.xq.dialoglogshow.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.core.model.HttpLogRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 专用于 HTTP 网络日志持久化的全新轻量 SQLite 帮助类。
 * 为什么这样做：
 * 1. 采用专用表 http_traffic_log 与复合索引（timestamp, host, path, status_code），大幅提升按天、按域名查询的吞吐率；
 * 2. 独立管理表结构，彻底斩断与旧版杂乱表格的历史包袱。
 */
public final class HttpLogDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "showlog_traffic.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NAME = "http_traffic_log";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TRACE_ID = "trace_id";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_HOST = "host";
    public static final String COLUMN_PATH = "path";
    public static final String COLUMN_METHOD = "method";
    public static final String COLUMN_STATUS_CODE = "status_code";
    public static final String COLUMN_SUCCESS = "success";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_REQUEST_HEADERS = "request_headers";
    public static final String COLUMN_REQUEST_BODY = "request_body";
    public static final String COLUMN_RESPONSE_HEADERS = "response_headers";
    public static final String COLUMN_RESPONSE_BODY = "response_body";
    public static final String COLUMN_CONTENT_TYPE = "content_type";
    public static final String COLUMN_REQUEST_SIZE = "request_size";
    public static final String COLUMN_RESPONSE_SIZE = "response_size";
    public static final String COLUMN_DURATION_MS = "duration_ms";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_ERROR_MESSAGE = "error_message";
    public static final String COLUMN_IS_FAILED = "is_failed";

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_TRACE_ID + " TEXT, "
            + COLUMN_URL + " TEXT NOT NULL, "
            + COLUMN_HOST + " TEXT NOT NULL, "
            + COLUMN_PATH + " TEXT, "
            + COLUMN_METHOD + " TEXT NOT NULL, "
            + COLUMN_STATUS_CODE + " INTEGER NOT NULL, "
            + COLUMN_SUCCESS + " INTEGER DEFAULT 1, "
            + COLUMN_MESSAGE + " TEXT, "
            + COLUMN_REQUEST_HEADERS + " TEXT, "
            + COLUMN_REQUEST_BODY + " TEXT, "
            + COLUMN_RESPONSE_HEADERS + " TEXT, "
            + COLUMN_RESPONSE_BODY + " TEXT, "
            + COLUMN_CONTENT_TYPE + " TEXT, "
            + COLUMN_REQUEST_SIZE + " INTEGER DEFAULT 0, "
            + COLUMN_RESPONSE_SIZE + " INTEGER DEFAULT 0, "
            + COLUMN_DURATION_MS + " INTEGER DEFAULT 0, "
            + COLUMN_TIMESTAMP + " INTEGER NOT NULL, "
            + COLUMN_ERROR_MESSAGE + " TEXT, "
            + COLUMN_IS_FAILED + " INTEGER DEFAULT 0"
            + ");";

    private static final String CREATE_INDEX_TIMESTAMP = "CREATE INDEX IF NOT EXISTS idx_traffic_timestamp ON "
            + TABLE_NAME + " (" + COLUMN_TIMESTAMP + " DESC);";

    private static final String CREATE_INDEX_HOST = "CREATE INDEX IF NOT EXISTS idx_traffic_host ON "
            + TABLE_NAME + " (" + COLUMN_HOST + ");";

    private static final String CREATE_INDEX_PATH = "CREATE INDEX IF NOT EXISTS idx_traffic_path ON "
            + TABLE_NAME + " (" + COLUMN_PATH + ");";

    private static final String CREATE_INDEX_STATUS = "CREATE INDEX IF NOT EXISTS idx_traffic_status ON "
            + TABLE_NAME + " (" + COLUMN_STATUS_CODE + ");";

    private static final String CREATE_INDEX_SUCCESS = "CREATE INDEX IF NOT EXISTS idx_traffic_success ON "
            + TABLE_NAME + " (" + COLUMN_SUCCESS + ");";

    public HttpLogDbHelper(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        db.execSQL(CREATE_INDEX_TIMESTAMP);
        db.execSQL(CREATE_INDEX_HOST);
        db.execSQL(CREATE_INDEX_PATH);
        db.execSQL(CREATE_INDEX_STATUS);
        db.execSQL(CREATE_INDEX_SUCCESS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_SUCCESS + " INTEGER DEFAULT 1;");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_MESSAGE + " TEXT;");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_traffic_success ON " + TABLE_NAME + " (" + COLUMN_SUCCESS + ");");
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 批量插入网络日志记录（使用单事务保障极致性能与原子性）。
     * 为什么这样做：避免逐条插入触发高频磁盘 I/O 阻塞主线程或导致数据库死锁。
     */
    public synchronized void insertBatch(@NonNull List<HttpLogRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            for (HttpLogRecord record : records) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_TRACE_ID, record.traceId);
                values.put(COLUMN_URL, record.url);
                values.put(COLUMN_HOST, record.host);
                values.put(COLUMN_PATH, record.path);
                values.put(COLUMN_METHOD, record.method);
                values.put(COLUMN_STATUS_CODE, record.statusCode);
                values.put(COLUMN_SUCCESS, record.success ? 1 : 0);
                values.put(COLUMN_MESSAGE, record.message);
                values.put(COLUMN_REQUEST_HEADERS, record.requestHeaders);
                values.put(COLUMN_REQUEST_BODY, record.requestBody);
                values.put(COLUMN_RESPONSE_HEADERS, record.responseHeaders);
                values.put(COLUMN_RESPONSE_BODY, record.responseBody);
                values.put(COLUMN_CONTENT_TYPE, record.contentType);
                values.put(COLUMN_REQUEST_SIZE, record.requestSize);
                values.put(COLUMN_RESPONSE_SIZE, record.responseSize);
                values.put(COLUMN_DURATION_MS, record.durationMs);
                values.put(COLUMN_TIMESTAMP, record.timestamp);
                values.put(COLUMN_ERROR_MESSAGE, record.errorMessage);
                values.put(COLUMN_IS_FAILED, record.isFailed ? 1 : 0);
                db.insert(TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 根据时间范围查询已持久化的历史网络日志。
     *
     * @param startTime 开始时间戳（包含）
     * @param endTime   结束时间戳（包含）
     * @param isAsc     是否按时间正序排列
     * @return 匹配的日志记录列表
     */
    @NonNull
    public List<HttpLogRecord> queryRange(long startTime, long endTime, boolean isAsc) {
        List<HttpLogRecord> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selection = COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " <= ?";
        String[] selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        String orderBy = COLUMN_TIMESTAMP + (isAsc ? " ASC" : " DESC");

        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, orderBy);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    result.add(parseRecord(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    /**
     * 执行 LRU 容量修剪：仅保留最新的 maxLimit 条记录，多余的在后台轻量剔除。
     */
    public synchronized void trimToLimit(int maxLimit) {
        if (maxLimit <= 0) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        try {
            // 为什么这样做：先查计数再决定是否修剪，可避免每次入库都执行昂贵的子查询删除。
            long count = 0;
            Cursor cursor = db.rawQuery("SELECT count(" + COLUMN_ID + ") FROM " + TABLE_NAME, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    count = cursor.getLong(0);
                }
                cursor.close();
            }
            if (count > maxLimit) {
                db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_ID + " NOT IN ("
                        + "SELECT " + COLUMN_ID + " FROM " + TABLE_NAME
                        + " ORDER BY " + COLUMN_ID + " DESC LIMIT " + maxLimit + ")");
            }
        } catch (Throwable ignored) {
            // 为什么这样做：修剪属于后台轻量维护，异常不应阻断业务正常日志记录。
        }
    }

    /**
     * 清空指定时间范围内的全部日志。
     */
    public synchronized int clearRange(long startTime, long endTime) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_NAME, COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});
    }

    /**
     * 清空整个数据表中的所有日志。
     */
    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    /**
     * 删除指定的一条网络日志记录。
     */
    public synchronized int deleteRecord(@NonNull HttpLogRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        if (record.traceId != null && !record.traceId.isEmpty()) {
            return db.delete(TABLE_NAME, COLUMN_TRACE_ID + " = ?", new String[]{record.traceId});
        } else if (record.id > 0) {
            return db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(record.id)});
        }
        return 0;
    }

    /**
     * 批量删除一组网络日志记录（在单事务中执行以提升吞吐量）。
     */
    public synchronized int deleteRecordsBatch(@NonNull List<HttpLogRecord> records) {
        if (records.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        int count = 0;
        try {
            for (HttpLogRecord record : records) {
                if (record.traceId != null && !record.traceId.isEmpty()) {
                    count += db.delete(TABLE_NAME, COLUMN_TRACE_ID + " = ?", new String[]{record.traceId});
                } else if (record.id > 0) {
                    count += db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(record.id)});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    @NonNull
    private HttpLogRecord parseRecord(@NonNull Cursor cursor) {
        int successCol = cursor.getColumnIndex(COLUMN_SUCCESS);
        boolean success = successCol != -1 ? (cursor.getInt(successCol) == 1) : (cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FAILED)) == 0);
        int msgCol = cursor.getColumnIndex(COLUMN_MESSAGE);
        String message = msgCol != -1 ? cursor.getString(msgCol) : null;
        if (message == null || message.isEmpty()) {
            message = success ? "OK" : "FAILED";
        }

        return new HttpLogRecord.Builder()
                .id(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)))
                .traceId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRACE_ID)))
                .url(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)))
                .host(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HOST)))
                .path(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PATH)))
                .method(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METHOD)))
                .statusCode(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS_CODE)))
                .success(success)
                .message(message)
                .requestHeaders(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REQUEST_HEADERS)))
                .requestBody(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REQUEST_BODY)))
                .responseHeaders(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESPONSE_HEADERS)))
                .responseBody(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESPONSE_BODY)))
                .contentType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT_TYPE)))
                .requestSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REQUEST_SIZE)))
                .responseSize(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RESPONSE_SIZE)))
                .durationMs(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION_MS)))
                .timestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)))
                .errorMessage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ERROR_MESSAGE)))
                .isFailed(!success)
                .build();
    }
}
