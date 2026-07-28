package com.xq.dialoglogshow.collector.vpn.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.storage.db.ShowLogDatabaseHelper;
import com.xq.dialoglogshow.storage.db.VpnTrafficLogTable;
import com.xq.dialoglogshow.storage.entity.VpnTrafficLogEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * VPN 日志持久化存储
 *
 * 为什么这样做：
 * 1. Repository 负责业务策略，Store 负责数据库细节，两层分开后查询和迁移更容易维护。
 * 2. Step 4 当前先支持插入、单条删除、整天查询和按天清理，为后续控制台历史记录功能打地基。
 */
public class VpnTrafficLogStore {

    private final ShowLogDatabaseHelper mDatabaseHelper;

    public VpnTrafficLogStore(@NonNull Context context) {
        mDatabaseHelper = new ShowLogDatabaseHelper(context);
    }

    /**
     * 插入或替换日志
     */
    public void insertOrReplace(@NonNull VpnTrafficLogEntity entity) {
        SQLiteDatabase database = mDatabaseHelper.getWritableDatabase();
        database.insertWithOnConflict(
                VpnTrafficLogTable.TABLE_NAME,
                null,
                toContentValues(entity),
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    /**
     * 查询指定时间范围日志
     */
    @NonNull
    public List<VpnTrafficLogEntity> queryByTimeRange(long startTime, long endTime) {
        ArrayList<VpnTrafficLogEntity> entityList = new ArrayList<>();
        SQLiteDatabase database = mDatabaseHelper.getReadableDatabase();
        Cursor cursor = database.query(
                VpnTrafficLogTable.TABLE_NAME,
                null,
                VpnTrafficLogTable.COLUMN_TIMESTAMP + ">=? AND " + VpnTrafficLogTable.COLUMN_TIMESTAMP + "<?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)},
                null,
                null,
                VpnTrafficLogTable.COLUMN_TIMESTAMP + " DESC"
        );
        try {
            while (cursor.moveToNext()) {
                entityList.add(readEntity(cursor));
            }
        } finally {
            cursor.close();
        }
        return entityList;
    }

    /**
     * 删除单条日志
     */
    public void deleteByLogId(@NonNull String logId) {
        SQLiteDatabase database = mDatabaseHelper.getWritableDatabase();
        database.delete(
                VpnTrafficLogTable.TABLE_NAME,
                VpnTrafficLogTable.COLUMN_LOG_ID + "=?",
                new String[]{logId}
        );
    }

    /**
     * 清空指定日期范围日志
     */
    public void deleteByTimeRange(long startTime, long endTime) {
        SQLiteDatabase database = mDatabaseHelper.getWritableDatabase();
        database.delete(
                VpnTrafficLogTable.TABLE_NAME,
                VpnTrafficLogTable.COLUMN_TIMESTAMP + ">=? AND " + VpnTrafficLogTable.COLUMN_TIMESTAMP + "<?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)}
        );
    }

    /**
     * 清理早于指定时间的历史数据
     */
    public void deleteBefore(long expiredTime) {
        SQLiteDatabase database = mDatabaseHelper.getWritableDatabase();
        database.delete(
                VpnTrafficLogTable.TABLE_NAME,
                VpnTrafficLogTable.COLUMN_TIMESTAMP + "<?",
                new String[]{String.valueOf(expiredTime)}
        );
    }

    @NonNull
    private ContentValues toContentValues(@NonNull VpnTrafficLogEntity entity) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(VpnTrafficLogTable.COLUMN_LOG_ID, entity.logId);
        contentValues.put(VpnTrafficLogTable.COLUMN_PROTOCOL, entity.protocol);
        contentValues.put(VpnTrafficLogTable.COLUMN_HOST, entity.host);
        contentValues.put(VpnTrafficLogTable.COLUMN_URL, entity.url);
        contentValues.put(VpnTrafficLogTable.COLUMN_METHOD, entity.method);
        contentValues.put(VpnTrafficLogTable.COLUMN_STATUS_CODE, entity.statusCode);
        contentValues.put(VpnTrafficLogTable.COLUMN_TIMESTAMP, entity.timestamp);
        contentValues.put(VpnTrafficLogTable.COLUMN_START_TIME, entity.startTime);
        contentValues.put(VpnTrafficLogTable.COLUMN_END_TIME, entity.endTime);
        contentValues.put(VpnTrafficLogTable.COLUMN_COST_TIME_MS, entity.costTimeMs);
        contentValues.put(VpnTrafficLogTable.COLUMN_REQUEST_BYTES, entity.requestBytes);
        contentValues.put(VpnTrafficLogTable.COLUMN_RESPONSE_BYTES, entity.responseBytes);
        contentValues.put(VpnTrafficLogTable.COLUMN_ENCRYPTED, entity.encrypted);
        contentValues.put(VpnTrafficLogTable.COLUMN_PARSE_STATUS, entity.parseStatus);
        contentValues.put(VpnTrafficLogTable.COLUMN_REQUEST_HEADERS_JSON, entity.requestHeadersJson);
        contentValues.put(VpnTrafficLogTable.COLUMN_REQUEST_BODY, entity.requestBody);
        contentValues.put(VpnTrafficLogTable.COLUMN_RESPONSE_HEADERS_JSON, entity.responseHeadersJson);
        contentValues.put(VpnTrafficLogTable.COLUMN_RESPONSE_BODY, entity.responseBody);
        contentValues.put(VpnTrafficLogTable.COLUMN_ERROR_MESSAGE, entity.errorMessage);
        contentValues.put(VpnTrafficLogTable.COLUMN_SOURCE_DESCRIPTION, entity.sourceDescription);
        contentValues.put(VpnTrafficLogTable.COLUMN_APP_PACKAGE_NAME, entity.appPackageName);
        return contentValues;
    }

    @NonNull
    private VpnTrafficLogEntity readEntity(@NonNull Cursor cursor) {
        VpnTrafficLogEntity entity = new VpnTrafficLogEntity();
        entity.logId = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_LOG_ID));
        entity.protocol = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_PROTOCOL));
        entity.host = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_HOST));
        entity.url = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_URL));
        entity.method = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_METHOD));
        entity.statusCode = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_STATUS_CODE));
        entity.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_TIMESTAMP));
        entity.startTime = cursor.getLong(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_START_TIME));
        entity.endTime = cursor.getLong(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_END_TIME));
        entity.costTimeMs = cursor.getLong(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_COST_TIME_MS));
        entity.requestBytes = cursor.getInt(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_REQUEST_BYTES));
        entity.responseBytes = cursor.getInt(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_RESPONSE_BYTES));
        entity.encrypted = cursor.getInt(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_ENCRYPTED));
        entity.parseStatus = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_PARSE_STATUS));
        entity.requestHeadersJson = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_REQUEST_HEADERS_JSON));
        entity.requestBody = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_REQUEST_BODY));
        entity.responseHeadersJson = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_RESPONSE_HEADERS_JSON));
        entity.responseBody = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_RESPONSE_BODY));
        entity.errorMessage = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_ERROR_MESSAGE));
        entity.sourceDescription = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_SOURCE_DESCRIPTION));
        entity.appPackageName = cursor.getString(cursor.getColumnIndexOrThrow(VpnTrafficLogTable.COLUMN_APP_PACKAGE_NAME));
        return entity;
    }
}
