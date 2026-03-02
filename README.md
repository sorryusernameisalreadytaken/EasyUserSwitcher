# aShell You

**aShell You** is a versatile Android application that provides a polished Material Design interface to execute ADB commands, manage multiple devices and explore files — all directly on your device or over wireless debugging.  This repository contains a modified version of the official **v7.1.0** release with an additional **Easy User Switcher** module.

## Key Features

- **Material Design 3 UI** with light and dark themes.
- **Run ADB and shell commands** on the local device via Shizuku or root, or remotely through OTG and wireless debugging.
- **Easy User Switcher**: pair with a device’s wireless ADB service, list all user profiles and temporarily switch to a selected user.  Once the device becomes idle, the app automatically returns to the owner account.
- **ADB‑based file explorer** for copying, moving, deleting and sharing files on connected devices.
- **Bookmarks and history** for frequently used commands.
- **Continuous command support**, e.g. `logcat`, `top` or `watch`.
- **Search and save command output** to a text file.
- **Backup and restore** of application settings and database.

## Building

1. Install Android Studio Flamingo (or later) with the Android SDK, Kotlin and Jetpack Compose support.
2. Clone this repository and open the project in Android Studio.
3. To build from the command line, run:

   ```sh
   ./gradlew assembleDebug
   ```

   to generate a debug APK.  For a signed release build, run `./gradlew assembleRelease` and ensure signing keys are configured in `app/build.gradle.kts`.

## Using the Easy User Switcher

1. On your Android device, enable **Wireless debugging**: open *Settings → Developer options → Wireless debugging*.
2. Tap **Pair device with pairing code** and note the **IP address**, **pairing port** and **pairing code** presented.
3. In **aShell You**, choose the **User Switcher** card from the home screen.
4. Enter the values from step 2 and tap **Pair**.  After a successful pairing, tap **Connect** (leave the port at `5555` unless your device uses a different port).
5. Tap **Load Users** to fetch the list of user profiles.  Tap a user to switch; aShell You will automatically return to the owner (user 0) when the device becomes idle or the screen turns off.

## License

Designed and developed by **DP Hridayan** © 2024.  This project is licensed under the **GNU General Public License v3.0**.  See [LICENSE.md](LICENSE.md) for the full license text.