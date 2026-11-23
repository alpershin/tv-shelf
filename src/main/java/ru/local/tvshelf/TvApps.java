package ru.local.tvshelf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TvApps {
    private static final List<String> DEFAULTS = Arrays.asList(
            "com.google.android.youtube.tv", "ru.rutube.app", "com.vk.tv",
            "ru.start.androidmobile", "mobi.zona", "ru.local.wifireconnect");
    private static final String VPN_SERVICE = "android.net.VpnService";

    static final class Entry {
        final String id;
        final String name;
        final ResolveInfo resolve;

        Entry(ResolveInfo resolve, PackageManager packages) {
            this.resolve = resolve;
            id = resolve.activityInfo.packageName;
            name = resolve.loadLabel(packages).toString();
        }
    }

    final PackageManager packages;
    final List<Entry> entries;
    final Map<String, Entry> byId = new LinkedHashMap<>();
    final List<String> vpnClients = new ArrayList<>();
    final String browser;

    TvApps(Context context) {
        packages = context.getPackageManager();
        Intent tvMain = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo item : packages.queryIntentActivities(tvMain, 0)) {
            if (context.getPackageName().equals(item.activityInfo.packageName)) continue;
            Entry entry = new Entry(item, packages);
            byId.putIfAbsent(entry.id, entry);
        }
        entries = new ArrayList<>(byId.values());
        Collator names = Collator.getInstance();
        entries.sort((left, right) -> names.compare(left.name, right.name));

        for (ResolveInfo result : packages.queryIntentServices(new Intent(VPN_SERVICE), 0)) {
            String id = result.serviceInfo.packageName;
            if (byId.containsKey(id) && !vpnClients.contains(id)) vpnClients.add(id);
        }

        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo preferred = packages.resolveActivity(web, PackageManager.MATCH_DEFAULT_ONLY);
        String browserId = preferred == null ? null : preferred.activityInfo.packageName;
        if (!byId.containsKey(browserId)) {
            browserId = null;
            for (ResolveInfo result : packages.queryIntentActivities(web, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (byId.containsKey(result.activityInfo.packageName)) {
                    browserId = result.activityInfo.packageName;
                    break;
                }
            }
        }
        browser = browserId;
    }

    List<String> starterFavorites() {
        List<String> result = new ArrayList<>();
        for (String id : DEFAULTS) {
            if (byId.containsKey(id)) result.add(id);
        }
        for (Entry entry : entries) {
            if (result.size() == DEFAULTS.size()) break;
            if (!result.contains(entry.id) && !"com.android.tv.settings".equals(entry.id)) result.add(entry.id);
        }
        return result;
    }

    Intent launch(String id) {
        Entry entry = byId.get(id);
        if (entry == null) return null;
        Intent intent = packages.getLeanbackLaunchIntentForPackage(id);
        return intent != null ? intent : new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(id, entry.resolve.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    Drawable icon(Entry entry) {
        return entry.resolve.loadIcon(packages);
    }

}
