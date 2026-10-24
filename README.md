# Painticons

Generate colourful Lawnicons.
<p align="center">
  <img width="200" alt="Not painted icon pack" src="https://github.com/user-attachments/assets/a465a7c8-2c0f-4f14-bcb5-a2f02d68a0ea" />
  &nbsp;&nbsp;
  <img width="200" alt="Painting settings" src="https://github.com/user-attachments/assets/0a940c51-d8d3-4110-9912-f9dd605ece2e" />
  <img width="200" alt="Painted icon pack" src="https://github.com/user-attachments/assets/fd0faffb-3dec-499b-902d-e6b72713a8f5" />
</p>

> [!NOTE]
> Based on [Lawnicons](https://github.com/lawnchairlauncher/lawnicons) by the Lawnchair team and contributors.

- Use the app itself as an icon pack: `#C6C6C6` foreground on `#222224`.
- Or generate a coloured icon pack:
  - foregrounds from a fixed HEX or the apps' original icons' top 3 non-background colours;
  - fixed-HEX background;
  - one of 34 Material 3 shapes.

## Get Started

Download the APK from Releases (https://github.com/galib-i/painticons/releases/latest) (Android 8.0+).

Apply it directly in your launcher's icon pack settings - or open the app, use the pencil icon to tweak individual foreground colours and icons, then the wrench to export your own icon pack APK.

## Build

**JDK 17+** and the Android SDK with **API 37**.

```bash
git clone https://github.com/galib-i/painticons
cd painticons
./gradlew assembleAppDebug
```

With Gradle pointing to your SDK in `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Credits

Icons and the base app come from [Lawnicons](https://github.com/lawnchairlauncher/lawnicons), licensed under Apache 2.0.
