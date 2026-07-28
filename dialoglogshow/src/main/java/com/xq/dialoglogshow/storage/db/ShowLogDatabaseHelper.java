package com.xq.dialoglogshow.storage.db;

import android.content.Context;
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
    private static final int DATABASE_VERSION = 1;

    public ShowLogDatabaseHelper(@NonNull Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(VpnTrafficLogTable.CREATE_TABLE_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_TIMESTAMP_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_HOST_SQL);
        db.execSQL(VpnTrafficLogTable.CREATE_INDEX_PROTOCOL_SQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 为什么这样做：SDK 侧禁止 silently drop 表重建，一期先保留空实现，后续版本升级必须在这里显式追加迁移逻辑。
    }
}
