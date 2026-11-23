<p align="center">
  <img src="src/main/res/drawable-xxxhdpi/launcher_icon.png" width="144" alt="TV Shelf icon with three white app tiles on an evergreen gradient">
</p>

<h1 align="center">TV Shelf · Полка</h1>
<p align="center"><strong>Put your favorite TV apps one button away.</strong></p>
<p align="center">A free, open-source launcher for Google TV and Android TV.</p>

<p align="center">
  <a href="https://github.com/alpershin/tv-shelf/actions/workflows/ci.yml"><img src="https://github.com/alpershin/tv-shelf/actions/workflows/ci.yml/badge.svg" alt="Android build"></a>
  <a href="https://github.com/alpershin/tv-shelf/releases/latest"><img src="https://img.shields.io/github/v/release/alpershin/tv-shelf?label=release&amp;color=B7D9C9" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-D4C8E8" alt="MIT license"></a>
  <img src="https://img.shields.io/badge/platform-Google%20TV%20%2F%20Android%20TV-E1EDF5" alt="Google TV and Android TV">
</p>

<p align="center">
  <a href="https://github.com/alpershin/tv-shelf/releases/latest">Download APK</a> ·
  <a href="#on-tv">Screenshots and demos</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="BUILD.md">Build from source</a>
</p>

> Tested on Xiaomi TV Stick 4K (2nd Gen), Google TV with Android 14. Device checks covered directional navigation, tile ordering, and returning Home from another app and system settings.

## Why TV Shelf?

- **Favorites at a glance.** Pin apps to a horizontal shelf. The focused tile grows and colors the background.
- **Quick actions.** Set up shortcuts to Wi-Fi, a VPN client, a browser, or another installed app. Wi-Fi and VPN indicators follow their connection states.
- **Apps by icon.** Browse installed TV apps or reopen apps recently launched from the shelf.
- **Made for the remote.** Use the directional pad and center button. The interface follows the system’s light or dark theme.

## On TV

<p align="center">
  <img src="docs/media/home.png" width="900" alt="TV Shelf home screen with favorite apps and the quick actions dock">
</p>

| All apps | Edit shelf apps | Favorites |
| --- | --- | --- |
| ![Installed apps displayed as icons](docs/media/apps.png) | ![App icons with circular checkmarks for keeping apps on the shelf](docs/media/add-apps.png) | ![Favorite apps with a star in the top corner of each icon](docs/media/favorites.png) |

<details>
<summary>Watch the remote demos</summary>

| Browse the shelf | Edit shelf apps | Reorder a tile |
| --- | --- | --- |
| ![Move between favorites and open the apps grid](docs/media/navigation.gif) | ![Open the plus tile and uncheck an app to remove it from the shelf](docs/media/add.gif) | ![Use the remote to move a favorite tile to a new position](docs/media/reorder.gif) |

</details>

## Quick start

1. Download **tv-shelf.apk** from [Releases](https://github.com/alpershin/tv-shelf/releases/latest).
2. Sideload it. If the TV is already connected through ADB, run `adb install -r tv-shelf.apk`.
3. Open **Полка** from the TV’s app list. Go to **+ Добавить приложение** at the right end of the shelf. Check apps to pin them or uncheck them to remove them from the shelf, then press **Сохранить**. Press Back to cancel your changes.
4. To reorder a tile, hold the center button, then move it left or right. Press Down and select **Готово** to save, or press Back to cancel.
5. Open **Настроить полку** to manage favorites and quick actions. Select a starred icon in **Избранное** to move or remove that app.

### Open TV Shelf with Home

Open **Настроить полку → Домашний экран → Выбор домашнего экрана Android** and select **Полка**. On Google TV builds that keep opening the stock home screen, enable **Полка по кнопке Home** in Accessibility settings. The optional service reacts to the stock home window appearing without permission to retrieve window content.

To restore the stock experience, turn off **Полка по кнопке Home** and select the system launcher in Android’s Home settings. If the stock packages were disabled separately via ADB, re-enable them before removing TV Shelf.

## Known limitations

- Minimum supported API level is 26. Hardware checks have focused on the Xiaomi TV Stick 4K (2nd Gen).
- Home routing varies by firmware. With the optional Accessibility service enabled, Google TV Home can briefly appear before TV Shelf.
- The recent list covers apps launched from the shelf.
- The app interface is currently in Russian.

## Contributing

See [BUILD.md](BUILD.md) for build and device checks. Include the TV model and Android version when reporting Home or remote navigation behavior.

## License

[MIT](LICENSE)
