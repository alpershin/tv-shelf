package ru.local.tvshelf;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class ShelfStore {
    private final SharedPreferences prefs;
    private static final String[] DEFAULT_SHORTCUTS = {"wifi", "vpn", "browser"};

    ShelfStore(Context context) {
        prefs = context.getSharedPreferences("shelf", Context.MODE_PRIVATE);
    }

    boolean hasFavorites() {
        return prefs.contains("favorites");
    }

    List<String> favorites() {
        return readList("favorites");
    }

    void saveFavorites(List<String> packages) {
        saveList("favorites", packages);
    }

    List<String> recent() {
        return readList("recent");
    }

    void rememberLaunch(String packageName) {
        List<String> recent = recent();
        recent.remove(packageName);
        recent.add(0, packageName);
        saveList("recent", recent.subList(0, Math.min(10, recent.size())));
    }

    String shortcut(int slot) {
        return prefs.getString("shortcut_" + slot, DEFAULT_SHORTCUTS[slot]);
    }

    void saveShortcut(int slot, String value) {
        prefs.edit().putString("shortcut_" + slot, value).apply();
    }

    private List<String> readList(String key) {
        List<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
        } catch (JSONException ignored) {
            // A missing or unreadable local list can be rebuilt from the launcher.
        }
        return values;
    }

    private void saveList(String key, List<String> values) {
        prefs.edit().putString(key, new JSONArray(values).toString()).apply();
    }
}
