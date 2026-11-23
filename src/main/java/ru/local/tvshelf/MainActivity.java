package ru.local.tvshelf;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.content.res.ColorStateList;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private static final String APP_PREFIX = "app:";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private ShelfStore store;
    private TvApps apps;
    private ConnectivityManager connectivity;
    private TextView appName;
    private TextView shortcutName;
    private Glyph networkGlyph;
    private ShelfLighting lighting;
    private ShelfViewport shelfScroll;
    private LinearLayout shelf;
    private LinearLayout dock;
    private LinearLayout moveDock;
    private View moveDone;
    private String focusedPackage;
    private String movingPackage;
    private List<String> moveOrder;
    private boolean ignoreHeldConfirmRelease;
    private boolean confirmKeyDown;
    private final Map<String, View> tiles = new LinkedHashMap<>();
    private final Map<View, Shortcut> shortcuts = new LinkedHashMap<>();
    private boolean observing;
    private boolean wifiConnected;
    private boolean wifiValidated;
    private boolean vpnActive;

    private final ConnectivityManager.NetworkCallback networkEvents =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    runOnUiThread(MainActivity.this::refreshConnection);
                }

                @Override
                public void onLost(Network network) {
                    runOnUiThread(MainActivity.this::refreshConnection);
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ShelfStore(this);
        connectivity = getSystemService(ConnectivityManager.class);
        if (state != null) focusedPackage = state.getString("focusedPackage");
        buildScreen();
        refreshApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        NetworkRequest request = new NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        connectivity.registerNetworkCallback(request, networkEvents);
        observing = true;
        refreshConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TvApps current = new TvApps(this);
        if (!current.byId.keySet().equals(apps.byId.keySet())) {
            apps = current;
            rebuild();
        }
    }

    @Override
    protected void onStop() {
        observing = false;
        connectivity.unregisterNetworkCallback(networkEvents);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (lighting != null) lighting.setWindowActive(hasFocus);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("focusedPackage", focusedPackage);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        int action = event.getAction();
        boolean confirm = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (confirm) confirmKeyDown = action == KeyEvent.ACTION_DOWN;
        View focused = getCurrentFocus();
        boolean onTile = focused != null && focused == tiles.get(focusedPackage);
        if (confirm && action == KeyEvent.ACTION_DOWN && event.isLongPress()
                && moveOrder == null && onTile && tiles.size() > 1) {
            beginMove(focusedPackage, true);
            return true;
        }
        if (moveOrder == null) return super.dispatchKeyEvent(event);
        if (confirm && ignoreHeldConfirmRelease) {
            if (action == KeyEvent.ACTION_UP) ignoreHeldConfirmRelease = false;
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (action == KeyEvent.ACTION_UP) finishMove(false);
            return true;
        }
        if (onTile) {
            if (confirm) {
                if (action == KeyEvent.ACTION_UP) {
                    if (movingPackage == null) pickTile(focusedPackage);
                    else dropTile();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (action == KeyEvent.ACTION_DOWN) {
                    dropTile();
                    moveDone.requestFocus();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (movingPackage != null && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (action == KeyEvent.ACTION_DOWN) nudgeFavorite(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (action == KeyEvent.ACTION_DOWN) selectShelfTile();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void refreshApps() {
        apps = new TvApps(this);
        rebuild();
    }

    private void rebuild() {
        buildTiles();
        buildShortcuts();
        refreshConnection();
    }

    private void buildScreen() {
        FrameLayout stage = new FrameLayout(this);
        lighting = new ShelfLighting(this);
        stage.addView(lighting, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout root = vertical();
        root.setPadding(dp(54), dp(29), dp(54), dp(24));
        stage.addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(stage);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));
        FrameLayout account = iconButton("Профиль Google TV", "profile", dp(52), 26);
        account.setOnClickListener(view -> settings(Settings.ACTION_SYNC_SETTINGS));
        top.addView(account, new LinearLayout.LayoutParams(dp(52), dp(52)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        networkGlyph = new Glyph(this, "wifi", color(R.color.on_surface_variant));
        top.addView(networkGlyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextClock clock = new TextClock(this);
        clock.setTextSize(18);
        clock.setTextColor(color(R.color.on_surface_variant));
        clock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        clockParams.leftMargin = dp(12);
        top.addView(clock, clockParams);

        LinearLayout middle = vertical();
        middle.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(middle, new LinearLayout.LayoutParams(MATCH, 0, 1));
        appName = text("", 40, R.color.on_surface, true);
        appName.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        appName.setSingleLine(true);
        appName.setEllipsize(TextUtils.TruncateAt.END);
        appName.setAccessibilityHeading(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(MATCH, WRAP);
        nameParams.leftMargin = dp(8);
        nameParams.bottomMargin = dp(10);
        middle.addView(appName, nameParams);

        shelfScroll = new ShelfViewport(this);
        shelfScroll.setHorizontalScrollBarEnabled(false);
        shelfScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelfScroll.setClipToPadding(false);
        shelfScroll.setFocusable(false);
        shelfScroll.setPadding(dp(8), dp(6), dp(8), dp(6));
        shelfScroll.setOnScrollChangeListener((view, x, y, previousX, previousY) -> {
            View selected = tiles.get(focusedPackage);
            if (selected != null && selected.hasFocus()) lighting.follow(selected);
        });
        middle.addView(shelfScroll, new LinearLayout.LayoutParams(MATCH, WRAP));
        shelf = horizontal();
        shelf.setClipChildren(false);
        shelfScroll.addView(shelf, new HorizontalScrollView.LayoutParams(WRAP, WRAP));

        LinearLayout bottom = vertical();
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(MATCH, WRAP);
        bottomParams.topMargin = dp(24);
        middle.addView(bottom, bottomParams);
        dock = horizontal();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(shape(color(R.color.surface_group), 22, color(R.color.outline), 1));
        dock.setPadding(dp(10), dp(7), dp(10), dp(7));
        bottom.addView(dock, new LinearLayout.LayoutParams(WRAP, WRAP));

        moveDock = horizontal();
        moveDock.setGravity(Gravity.CENTER_VERTICAL);
        moveDock.setBackground(shape(color(R.color.surface_panel), 22, color(R.color.outline), 1));
        moveDock.setPadding(dp(10), dp(7), dp(10), dp(7));
        moveDock.setVisibility(View.GONE);
        bottom.addView(moveDock, new LinearLayout.LayoutParams(WRAP, dp(63)));
        TextView done = actionText("Готово");
        done.setOnClickListener(view -> finishMove(true));
        moveDone = done;
        moveDock.addView(done, new LinearLayout.LayoutParams(dp(116), dp(47)));
        TextView cancel = actionText("Отмена");
        cancel.setOnClickListener(view -> finishMove(false));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(116), dp(47));
        cancelParams.leftMargin = dp(7);
        moveDock.addView(cancel, cancelParams);

        shortcutName = text("", 15, R.color.on_surface_variant, false);
        shortcutName.setGravity(Gravity.CENTER);
        shortcutName.setMinHeight(dp(26));
        bottom.addView(shortcutName, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void buildTiles() {
        shelf.removeAllViews();
        tiles.clear();
        if (!store.hasFavorites()) store.saveFavorites(apps.starterFavorites());

        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int size = Math.max(112, Math.min(166, (screenWidth - 108) / 4 - 15));
        for (String id : moveOrder == null ? store.favorites() : moveOrder) {
            TvApps.Entry app = apps.byId.get(id);
            if (app == null || tiles.containsKey(id)) continue;
            ShelfArtwork artwork = new ShelfArtwork(apps.icon(app), color(R.color.accent));
            ShelfTile tile = new ShelfTile(this);
            tile.setId(View.generateViewId());
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setContentDescription(app.name);
            tile.setDefaultFocusHighlightEnabled(false);
            tile.setForeground(focusOutline(lighting.outline(artwork.accent)));
            tile.setActivated(id.equals(movingPackage));

            ImageView art = new ImageView(this);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            art.setBackground(shape(color(R.color.surface_group), 14, 0));
            art.setClipToOutline(true);
            art.setImageBitmap(artwork.image);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(art, new FrameLayout.LayoutParams(MATCH, MATCH));

            tile.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    focusedPackage = id;
                    appName.setText(app.name);
                    lighting.selectColor(artwork.accent);
                    lighting.follow(tile);
                    tile.post(() -> {
                        if (tile.hasFocus()) {
                            shelfScroll.revealTerminalTile(tile);
                            lighting.follow(tile);
                        }
                    });
                }
                lighting.showFocus(focused);
            });
            tile.setOnClickListener(view -> {
                if (moveOrder == null) launchPackage(id);
                else if (movingPackage == null) pickTile(id);
                else dropTile();
            });
            tile.setOnLongClickListener(view -> {
                if (moveOrder == null && tiles.size() > 1) beginMove(id, confirmKeyDown);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
            params.rightMargin = dp(20);
            shelf.addView(tile, params);
            tiles.put(id, tile);
        }

        if (tiles.isEmpty()) {
            appName.setText("Избранное");
            lighting.showFocus(false);
            TextView add = text("Добавить приложения", 19, R.color.on_surface, true);
            add.setGravity(Gravity.CENTER);
            add.setId(View.generateViewId());
            add.setFocusable(true);
            add.setBackground(focusBackground(color(R.color.tile_a), 14));
            add.setOnClickListener(view -> editFavorites());
            shelf.addView(add, new LinearLayout.LayoutParams(dp(220), dp(96)));
        } else {
            if (!tiles.containsKey(focusedPackage)) focusedPackage = tiles.keySet().iterator().next();
            appName.setText(apps.byId.get(focusedPackage).name);
        }
        selectShelfTile();
    }

    private void selectShelfTile() {
        View tile = tiles.get(focusedPackage);
        if (tile == null) tile = shelf.getChildAt(0);
        if (tile != null) tile.requestFocus();
    }

    private void buildShortcuts() {
        dock.removeAllViews();
        shortcuts.clear();
        addShortcut("apps");
        for (int i = 0; i < 3; i++) addShortcut(store.shortcut(i));
        addShortcut("recent");
        addShortcut("settings");
        View divider = new View(this);
        divider.setBackgroundColor(color(R.color.outline));
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(26));
        dividerParams.leftMargin = dp(4);
        dock.addView(divider, dividerParams);
        TextView edit = text("Настроить полку", 15, R.color.on_surface, true);
        edit.setId(View.generateViewId());
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(11), 0, dp(11), 0);
        edit.setFocusable(true);
        edit.setBackground(focusBackground(color(R.color.surface_group), 12));
        edit.setOnClickListener(view -> editShelf());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP, dp(54));
        params.leftMargin = dp(8);
        dock.addView(edit, params);
        edit.setOnFocusChangeListener((view, focused) -> shortcutName.setText(focused ? "Избранное и быстрые действия" : ""));
    }

    private void addShortcut(String id) {
        String name = label(id);
        String glyphType = id;
        if (id.startsWith(APP_PREFIX) && apps.vpnClients.contains(id.substring(APP_PREFIX.length()))) {
            glyphType = "vpn";
        }
        FrameLayout button = iconButton(name, glyphType, dp(49), 13);
        button.setBackground(focusBackground(color(R.color.surface_key), 13));
        Glyph glyph = (Glyph) button.getChildAt(0);
        if (id.startsWith(APP_PREFIX) && !"vpn".equals(glyphType)) {
            TvApps.Entry entry = apps.byId.get(id.substring(APP_PREFIX.length()));
            if (entry != null) {
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(apps.icon(entry));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                glyph.setVisibility(View.INVISIBLE);
                button.addView(icon, new FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER));
            }
        }
        Shortcut shortcut = new Shortcut(glyphType, glyph, name);
        shortcuts.put(button, shortcut);
        button.setOnClickListener(view -> openShortcut(id));
        button.setOnFocusChangeListener((view, focused) -> shortcutName.setText(focused ? shortcut.status : ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(51), dp(49));
        params.rightMargin = dp(5);
        dock.addView(button, params);
    }

    private void refreshConnection() {
        if (!observing) return;
        wifiConnected = false;
        wifiValidated = false;
        vpnActive = false;
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities info = connectivity.getNetworkCapabilities(network);
            if (info == null) continue;
            boolean vpn = info.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            vpnActive |= vpn;
            if (info.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !vpn) {
                wifiConnected = true;
                wifiValidated |= info.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        int wifiColor = !wifiConnected ? color(R.color.on_surface_variant)
                : wifiValidated ? color(R.color.accent) : color(R.color.warning);
        networkGlyph.state(wifiColor, !wifiConnected);
        for (Map.Entry<View, Shortcut> entry : shortcuts.entrySet()) {
            View button = entry.getKey();
            Shortcut shortcut = entry.getValue();
            if ("wifi".equals(shortcut.style)) {
                shortcut.status = !wifiConnected ? "Wi-Fi · отключён"
                        : wifiValidated ? "Wi-Fi · подключён" : "Wi-Fi · интернет не подтверждён";
                shortcut.glyph.state(wifiColor, !wifiConnected);
            } else if ("vpn".equals(shortcut.style)) {
                shortcut.status = vpnActive ? "VPN · активен" : "VPN · выключен";
                shortcut.glyph.state(color(vpnActive ? R.color.accent : R.color.on_surface_variant), !vpnActive);
            }
            button.setContentDescription(shortcut.status);
            if (button.hasFocus()) shortcutName.setText(shortcut.status);
        }
    }

    private void openShortcut(String id) {
        switch (id) {
            case "apps":
                showAppList(false);
                break;
            case "recent":
                showAppList(true);
                break;
            case "wifi":
                settings(Settings.ACTION_WIFI_SETTINGS);
                break;
            case "display":
                settings(Settings.ACTION_SOUND_SETTINGS);
                break;
            case "settings":
                settings(Settings.ACTION_SETTINGS);
                break;
            case "vpn":
                if (!apps.vpnClients.isEmpty()) launchPackage(apps.vpnClients.get(0));
                else if (!open(new Intent(Settings.ACTION_VPN_SETTINGS))) {
                    toast("Установите VPN-клиент, чтобы открыть его с полки.");
                }
                break;
            case "browser":
                if (apps.browser != null) launchPackage(apps.browser);
                else toast("Выберите установленный браузер в настройках полки.");
                break;
            default:
                if (id.startsWith(APP_PREFIX)) launchPackage(id.substring(APP_PREFIX.length()));
        }
    }

    private void showAppList(boolean recent) {
        List<TvApps.Entry> entries = new ArrayList<>();
        if (recent) {
            for (String id : store.recent()) {
                TvApps.Entry item = apps.byId.get(id);
                if (item != null) entries.add(item);
            }
        } else {
            entries.addAll(apps.entries);
        }
        Panel panel = createPanel(recent ? "Недавние" : "Все приложения", 764);
        if (entries.isEmpty()) {
            panel.content.addView(text(recent ? "Здесь появятся приложения, открытые с «Полки»."
                    : "Установленные ТВ-приложения появятся здесь.", 17, R.color.on_surface_variant, false));
            showPanel(panel, panel.close, 182);
            return;
        }

        TextView selection = panel.caption;
        selection.setVisibility(View.VISIBLE);
        selection.setText(entries.get(0).name);
        int columns = Math.min(6, Math.max(3, (panel.width - 40) / 96));
        int gap = 14;
        int size = Math.min(104, (panel.width - 48 - gap * (columns - 1)) / columns);
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % columns == 0) {
                row = horizontal();
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(MATCH, WRAP);
                rowParams.bottomMargin = dp(gap);
                panel.content.addView(row, rowParams);
            }
            TvApps.Entry entry = entries.get(i);
            ShelfArtwork artwork = new ShelfArtwork(apps.icon(entry), color(R.color.accent));
            FrameLayout item = new FrameLayout(this);
            item.setFocusable(true);
            item.setClickable(true);
            item.setContentDescription(entry.name);
            item.setBackground(shape(color(R.color.surface_group), 12, 0));
            item.setForeground(focusOutline(color(R.color.accent)));
            item.setClipToOutline(true);
            item.setDefaultFocusHighlightEnabled(false);
            ImageView image = new ImageView(this);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            image.setImageBitmap(artwork.image);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            item.addView(image, new FrameLayout.LayoutParams(MATCH, MATCH));
            item.setOnFocusChangeListener((view, focused) -> {
                if (focused) selection.setText(entry.name);
            });
            item.setOnClickListener(view -> {
                panel.dialog.dismiss();
                launchPackage(entry.id);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
            if (i % columns != columns - 1) params.rightMargin = dp(gap);
            row.addView(item, params);
            if (first == null) first = item;
        }
        int rows = (entries.size() + columns - 1) / columns;
        showPanel(panel, first, Math.min(rows, 3) * (size + gap) + 120);
    }

    private void editShelf() {
        showOptions("Настроить полку",
                new String[]{"Избранные приложения", "Быстрые действия", "Домашний экран"},
                new String[]{"apps", "settings", "display"},
                index -> {
                    if (index == 0) editFavorites();
                    else if (index == 1) editShortcuts();
                    else editHomeScreen();
                });
    }

    private void editHomeScreen() {
        String status = isHomeRedirectEnabled() ? "включено" : "выключено";
        showOptions("Домашний экран",
                new String[]{getString(R.string.home_redirect_label) + " · " + status,
                        "Выбор домашнего экрана Android"},
                new String[]{"display", "settings"},
                index -> {
                    if (index == 0) {
                        settings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    } else if (!open(new Intent(Settings.ACTION_HOME_SETTINGS))) {
                        toast("Выбор домашнего экрана недоступен на этом устройстве.");
                    }
                });
    }

    private boolean isHomeRedirectEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) return false;

        for (AccessibilityServiceInfo service :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (HomeRedirectService.class.getName().equals(service.getResolveInfo().serviceInfo.name)
                    && getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)) return true;
        }
        return false;
    }

    private void editShortcuts() {
        String[] slots = new String[3];
        for (int i = 0; i < slots.length; i++) slots[i] = (i + 1) + "  " + label(store.shortcut(i));
        showOptions("Быстрые действия", slots, null, this::chooseShortcut);
    }

    private void chooseShortcut(int slot) {
        List<String> ids = new ArrayList<>();
        ids.add("wifi");
        ids.add("vpn");
        ids.add("browser");
        ids.add("display");
        for (TvApps.Entry entry : apps.entries) ids.add(APP_PREFIX + entry.id);
        String[] names = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            names[i] = label(ids.get(i));
            if (ids.get(i).equals(store.shortcut(slot))) names[i] = "✓  " + names[i];
        }
        showOptions("Заменить ярлык", names, null, index -> {
            store.saveShortcut(slot, ids.get(index));
            buildShortcuts();
            refreshConnection();
            editShortcuts();
        });
    }

    private void editFavorites() {
        String[] names = new String[tiles.size() + 1];
        names[0] = "Добавить приложения";
        List<String> ids = new ArrayList<>(tiles.keySet());
        for (int i = 0; i < ids.size(); i++) names[i + 1] = apps.byId.get(ids.get(i)).name;
        showOptions("Избранное", names, null, index -> {
            if (index == 0) addFavorites();
            else editFavorite(ids.get(index - 1));
        });
    }

    private void addFavorites() {
        List<TvApps.Entry> entries = new ArrayList<>();
        for (TvApps.Entry entry : apps.entries) {
            if (!tiles.containsKey(entry.id)) entries.add(entry);
        }
        if (entries.isEmpty()) {
            toast("Все ТВ-приложения уже на полке.");
            return;
        }
        Panel panel = createPanel("Добавить приложения", 480);
        List<CheckBox> choices = new ArrayList<>();
        for (TvApps.Entry entry : entries) {
            CheckBox choice = new CheckBox(this);
            choice.setText(entry.name);
            choice.setTextSize(17);
            choice.setTextColor(color(R.color.on_surface));
            choice.setButtonTintList(ColorStateList.valueOf(color(R.color.accent)));
            choice.setPadding(dp(12), 0, dp(12), 0);
            choice.setBackground(focusBackground(color(R.color.surface_group), 9));
            choices.add(choice);
            appendOption(panel, choice);
        }
        TextView save = actionText("Добавить");
        panel.root.addView(save, new LinearLayout.LayoutParams(MATCH, dp(48)));
        save.setOnClickListener(view -> {
            List<String> current = store.favorites();
            for (int i = 0; i < choices.size(); i++) {
                String id = entries.get(i).id;
                if (choices.get(i).isChecked() && !current.contains(id)) current.add(id);
            }
            store.saveFavorites(current);
            panel.dialog.dismiss();
            buildTiles();
        });
        showPanel(panel, choices.get(0), 345);
    }

    private void editFavorite(String id) {
        if (!tiles.containsKey(id)) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (tiles.size() > 1) {
            labels.add("Переместить");
            actions.add(() -> beginMove(id, false));
        }
        labels.add("Убрать с полки");
        actions.add(() -> {
            List<String> favorites = store.favorites();
            favorites.remove(id);
            store.saveFavorites(favorites);
            buildTiles();
        });
        showOptions(apps.byId.get(id).name, labels.toArray(new String[0]),
                null, choice -> actions.get(choice).run());
    }

    private void beginMove(String id, boolean heldConfirm) {
        moveOrder = store.favorites();
        ignoreHeldConfirmRelease = heldConfirm;
        dock.setVisibility(View.GONE);
        moveDock.setVisibility(View.VISIBLE);
        focusedPackage = id;
        selectShelfTile();
        pickTile(id);
    }

    private void pickTile(String id) {
        View tile = tiles.get(id);
        if (tile == null) return;
        movingPackage = id;
        tile.setActivated(true);
        shortcutName.setText("←  →  Переместить       OK  Поставить       ↓  Готово и отмена");
    }

    private void dropTile() {
        View tile = tiles.get(movingPackage);
        if (tile != null) tile.setActivated(false);
        movingPackage = null;
        shortcutName.setText("OK  Взять плитку       ↓  Готово и отмена");
    }

    private void nudgeFavorite(int direction) {
        View selected = tiles.get(movingPackage);
        if (selected == null) {
            finishMove(false);
            return;
        }
        int destination = shelf.indexOfChild(selected) + direction;
        if (destination < 0 || destination >= shelf.getChildCount()) return;
        View neighbor = shelf.getChildAt(destination);
        for (Map.Entry<String, View> other : tiles.entrySet()) {
            if (other.getValue() != neighbor) continue;
            Collections.swap(moveOrder, moveOrder.indexOf(movingPackage), moveOrder.indexOf(other.getKey()));
            break;
        }
        shelf.removeView(selected);
        shelf.addView(selected, destination);
        focusedPackage = movingPackage;
        selected.requestFocus();
    }

    private void finishMove(boolean save) {
        if (moveOrder == null) return;
        if (movingPackage != null) focusedPackage = movingPackage;
        if (save) store.saveFavorites(moveOrder);
        movingPackage = null;
        moveOrder = null;
        ignoreHeldConfirmRelease = false;
        moveDock.setVisibility(View.GONE);
        dock.setVisibility(View.VISIBLE);
        shortcutName.setText("");
        buildTiles();
    }

    private Panel createPanel(String title, int preferredWidth) {
        int width = Math.min(preferredWidth,
                Math.max(260, getResources().getConfiguration().screenWidthDp - 96));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = vertical();
        root.setPadding(dp(24), dp(17), dp(24), dp(16));
        root.setBackground(shape(color(R.color.surface_panel), 24, color(R.color.outline), 1));
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 30, R.color.on_surface, true);
        heading.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        heading.setAccessibilityHeading(true);
        header.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView close = actionText("Закрыть");
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(WRAP, dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView caption = text("", 17, R.color.on_surface_variant, false);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setVisibility(View.GONE);
        root.addView(caption, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = vertical();
        scroll.addView(content, new ScrollView.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(MATCH, 0, 1);
        scrollParams.topMargin = dp(12);
        root.addView(scroll, scrollParams);
        dialog.setContentView(root);
        return new Panel(dialog, root, content, caption, close, width);
    }

    private void showPanel(Panel panel, View initialFocus, int height) {
        Window window = panel.dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(0));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.53f);
        panel.dialog.show();
        int usableHeight = Math.max(148, getResources().getConfiguration().screenHeightDp - 54);
        window.setLayout(dp(panel.width), dp(Math.min(height, usableHeight)));
        initialFocus.requestFocus();
    }

    private void showOptions(String title, String[] labels, String[] glyphs, IntConsumer callback) {
        Panel panel = createPanel(title, 480);
        View first = panel.close;
        for (int i = 0; i < labels.length; i++) {
            int choice = i;
            LinearLayout row = horizontal();
            row.setPadding(dp(15), 0, dp(14), 0);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setDefaultFocusHighlightEnabled(false);
            row.setBackground(focusBackground(color(R.color.surface_key), 10));
            row.setContentDescription(labels[i]);
            if (glyphs != null) {
                Glyph glyph = new Glyph(this, glyphs[i], color(R.color.accent));
                LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(dp(24), dp(24));
                icon.rightMargin = dp(14);
                row.addView(glyph, icon);
            }
            TextView name = text(labels[i], 19, R.color.on_surface, true);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1));
            TextView arrow = text("›", 27, R.color.on_surface_variant, false);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow);
            row.setOnClickListener(view -> {
                panel.dialog.dismiss();
                callback.accept(choice);
            });
            appendOption(panel, row);
            if (i == 0) first = row;
        }
        showPanel(panel, first, 104 + Math.min(labels.length, 6) * 58);
    }

    private void appendOption(Panel panel, View option) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH, dp(52));
        params.bottomMargin = dp(6);
        panel.content.addView(option, params);
    }

    private TextView actionText(String name) {
        TextView action = text(name, 16, R.color.on_surface, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), 0, dp(14), 0);
        action.setFocusable(true);
        action.setDefaultFocusHighlightEnabled(false);
        action.setBackground(focusBackground(color(R.color.surface_group), 10));
        return action;
    }

    private String label(String id) {
        switch (id) {
            case "apps": return "Все приложения";
            case "recent": return "Недавние";
            case "wifi": return "Wi-Fi";
            case "display": return "Экран и звук";
            case "vpn": return "VPN";
            case "browser": return "Браузер";
            case "settings": return "Настройки";
            default:
                TvApps.Entry entry = apps.byId.get(id.substring(APP_PREFIX.length()));
                return entry == null ? "Приложение недоступно" : entry.name;
        }
    }

    private void launchPackage(String id) {
        Intent intent = apps.launch(id);
        if (intent != null && open(intent)) store.rememberLaunch(id);
        else toast("Приложение недоступно.");
    }

    private void settings(String action) {
        if (!open(new Intent(action)) && !open(new Intent(Settings.ACTION_SETTINGS))) {
            toast("Настройки устройства недоступны.");
        }
    }

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private FrameLayout iconButton(String label, String icon, int size, int radius) {
        FrameLayout button = new FrameLayout(this);
        button.setId(View.generateViewId());
        button.setMinimumHeight(size);
        button.setFocusable(true);
        button.setClickable(true);
        button.setContentDescription(label);
        button.setBackground(focusBackground(color(R.color.surface_group), radius));
        Glyph glyph = new Glyph(this, icon, color(R.color.on_surface));
        button.addView(glyph, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        return button;
    }

    private StateListDrawable focusBackground(int fill, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(color(R.color.accent_container), radius, color(R.color.accent)));
        states.addState(new int[]{android.R.attr.state_focused},
                shape(fill, radius, color(R.color.accent)));
        states.addState(new int[0], shape(fill, radius, 0));
        return states;
    }

    private StateListDrawable focusOutline(int accent) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated},
                shape(0, 14, color(R.color.warning), 4));
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(0x16000000, 14, accent, 4));
        states.addState(new int[]{android.R.attr.state_focused},
                shape(0, 14, accent, 3));
        states.addState(new int[0], shape(0, 14, 0));
        return states;
    }

    private GradientDrawable shape(int fill, int radius, int border) {
        return shape(fill, radius, border, 3);
    }

    private GradientDrawable shape(int fill, int radius, int border, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(width), border);
        return drawable;
    }

    private TextView text(String value, int size, int tone, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(tone));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        return row;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private static final class Shortcut {
        final String style;
        final Glyph glyph;
        String status;

        Shortcut(String style, Glyph glyph, String status) {
            this.style = style;
            this.glyph = glyph;
            this.status = status;
        }
    }

    private static final class Panel {
        final Dialog dialog;
        final LinearLayout root;
        final LinearLayout content;
        final TextView caption;
        final TextView close;
        final int width;

        Panel(Dialog dialog, LinearLayout root, LinearLayout content, TextView caption,
              TextView close, int width) {
            this.dialog = dialog;
            this.root = root;
            this.content = content;
            this.caption = caption;
            this.close = close;
            this.width = width;
        }
    }
}
