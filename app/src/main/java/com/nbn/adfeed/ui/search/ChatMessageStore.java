package com.nbn.adfeed.ui.search;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ChatMessageStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "ai_search_chat.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TEXT = "text";
    private static final String COLUMN_FROM_USER = "from_user";
    private static final String COLUMN_MATCHED_AD_IDS = "matched_ad_ids";
    private static final String COLUMN_CREATED_AT = "created_at";

    public ChatMessageStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 建表
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TEXT + " TEXT NOT NULL, "
                + COLUMN_FROM_USER + " INTEGER NOT NULL, "
                + COLUMN_MATCHED_AD_IDS + " TEXT, "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 首版表结构没有迁移历史，后续升级时在这里补 ALTER 逻辑。
    }

    //用户信息或ai回复插入
    public long insert(ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TEXT, message.getText());
        values.put(COLUMN_FROM_USER, message.isFromUser() ? 1 : 0);
        values.put(COLUMN_CREATED_AT, message.getCreatedAt());
        List<String> matchedAdIds = message.getMatchedAdIds();
        if (matchedAdIds == null || matchedAdIds.isEmpty()) {
            values.putNull(COLUMN_MATCHED_AD_IDS);
        } else {
            values.put(COLUMN_MATCHED_AD_IDS, ChatMessageIdsCodec.encode(matchedAdIds));
        }
        return db.insertOrThrow(TABLE_MESSAGES, null, values);
    }

    //读取聊天记录
    public List<ChatMessage> loadAll() {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_MESSAGES,
                new String[]{
                        COLUMN_ID,
                        COLUMN_TEXT,
                        COLUMN_FROM_USER,
                        COLUMN_MATCHED_AD_IDS,
                        COLUMN_CREATED_AT
                },
                null,
                null,
                null,
                null,
                COLUMN_CREATED_AT + " ASC, " + COLUMN_ID + " ASC"
        )) {
            while (cursor.moveToNext()) {
                messages.add(readMessage(cursor));
            }
        }
        return messages;
    }

    private static ChatMessage readMessage(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
        String text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT));
        boolean fromUser = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_FROM_USER)) == 1;
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
        String matchedIdsJson = getNullableString(cursor, COLUMN_MATCHED_AD_IDS);
        return new ChatMessage(
                id,
                text,
                fromUser,
                createdAt,
                ChatMessageIdsCodec.decode(matchedIdsJson)
        );
    }

    @Nullable
    private static String getNullableString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
