package com.xq.dialoglogshow.storage.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/**
 * ShowLog 数据库帮助类
 *
 * 为什么这样做：
 * 1. VPN 日志后续要支持历史查询与清理，SQLite 生命周期和升级策略必须集中管理，避免仓库类直接写裸 SQL 造成升级不可控。
 * 2. 一期数据库版本固定为 1，先保证建表和索引稳定，后续升级只需要沿着 onUpgrade 追加迁移逻辑。
 */
public class ShowLogDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "show_log_debug.db";
    private static final int DATABASE_VERSION = 2;

    public ShowLogDatabaseHelper(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(VpnTrafficLogTable.CREATE_TABLE_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_TIMESTAMP_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_HOST_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_PROTOCOL_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_APP_TIMESTAMP_SQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 为什么这样做：部分早期数据库可能与当前 version=1 表定义不一致，先查列再迁移可同时兼容“已有字段”和“旧表缺字段”两种安装状态。
            if (!hasColumn(db, VpnTrafficLogTable.TABLE_NAME,
                    VpnTrafficLogTable.COLUMN_APP_PACKAGE_NAME)) {
                db.execSQL(
                        "ALTER TABLE " + VpnTrafficLogTable.TABLE_NAME
                                + " ADD COLUMN "
                                + VpnTrafficLogTable.COLUMN_APP_PACKAGE_NAME
                                + " TEXT"
                );
            }
            db.execSQL(VpnTrafficLogTable.CREATE_INDEX_APP_TIMESTAMP_SQL);
        }
    }

    /**
     * 检查旧表是否已有目标列。
     * 为什么这样做：SQLite 旧版本不支持通用的 ADD COLUMN IF NOT EXISTS，只能通过 PRAGMA 安全判断并确保 Cursor 总是释放。
     */
    private boolean hasColumn(SQLiteDatabase db,
                              @NonNull String tableName,
                              @NonNull String columnName) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        try {
            int nameColumnIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(nameColumnIndex))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }
}
