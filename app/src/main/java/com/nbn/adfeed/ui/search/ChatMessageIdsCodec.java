package com.nbn.adfeed.ui.search;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//负责聊天消息的序列化List<String> matchedAdIds和反序列化
final class ChatMessageIdsCodec {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

    private ChatMessageIdsCodec() {
    }

    static String encode(List<String> matchedAdIds) {
        if (matchedAdIds == null || matchedAdIds.isEmpty()) {
            return "[]";
        }
        return GSON.toJson(matchedAdIds, STRING_LIST_TYPE);
    }

    static List<String> decode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> decoded = GSON.fromJson(value, STRING_LIST_TYPE);
            if (decoded == null) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (String item : decoded) {
                if (item != null && !item.isEmpty()) {
                    result.add(item);
                }
            }
            return result;
        } catch (JsonSyntaxException | ClassCastException ignored) {
            return Collections.emptyList();
        }
    }
}
