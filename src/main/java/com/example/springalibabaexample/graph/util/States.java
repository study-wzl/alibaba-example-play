package com.example.springalibabaexample.graph.util;

import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OverAllState 取值工具，消除重复的 .value().map().orElse() 样板代码
 */
public final class States {

    private States() {}

    @SuppressWarnings("unchecked")
    public static <T> T get(OverAllState state, String key) {
        return (T) state.value(key).orElse(null);
    }

    public static String getString(OverAllState state, String key, String defaultValue) {
        return state.value(key).map(v -> (String) v).orElse(defaultValue);
    }

    public static String getString(OverAllState state, String key) {
        return getString(state, key, "");
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(OverAllState state, String key) {
        return (List<T>) state.value(key).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(OverAllState state, String key) {
        return (Map<String, Object>) state.value(key).orElse(Map.of());
    }
}
