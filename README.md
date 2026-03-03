# Easy User Switcher (based on aShell v7.1.0)

⚠️ **Experimental Proof of Concept**

This application was created using a ChatGPT agent as an experiment to explore usability improvements for Android’s multi‑user environment, particularly on **GrapheneOS**. The goal is to demonstrate that it is possible to automate profile switching on Android using ADB commands and a polished Material Design interface.

---

## About This Project

This repository contains a **modified version of the official aShell v7.1.0 release**, extended with an additional **Easy User Switcher** module. The original project, aShell, is an open‑source Android application that provides a refined interface for executing ADB commands locally or over wireless debugging. It serves as a solid foundation for prototyping advanced user switching behaviour.

Unlike the original project, this fork is designed to show how ChatGPT can be used to build a custom Android app. It incorporates features tailored to multi‑user workflows on GrapheneOS, where up to 32 profiles can be created. It can be cumbersome to manually switch back to the owner profile after using a secondary profile. By automating ADB commands, the app can return to the main account when the device becomes idle, ensuring you don’t miss notifications or access to essential apps.

---

## Why This Exists

Multi‑user support on Android is powerful but not always convenient. In day‑to‑day use, you might:

* Forget to switch back to the owner (main) profile after using another profile.
* Lose access to messaging apps, contacts, or other tools not installed in your secondary profiles.
* Miss calls or notifications because you remained logged in to a secondary user.

Android already includes commands like:

```
am switch-user <USER_ID>
```

These allow you to change user profiles programmatically. When ADB runs on the host (or via Shizuku/root locally), you can switch back to the owner profile from any guest profile. This project demonstrates that concept by providing an interface to pair with a device’s wireless ADB service, list user profiles, switch to a selected user, and automatically return to the owner account when the device becomes idle.

This proof of concept paves the way for more advanced ideas, such as exposing app icons from secondary profiles in the owner profile’s launcher and switching automatically when you tap them.

---

## Key Features

* **Material Design 3** UI with light and dark themes.
* **Execute ADB and shell commands** on the local device via Shizuku or root, or remotely through OTG and wireless debugging.
* **Easy User Switcher module**
  * Pair with a device’s wireless debugging service.
  * List all user profiles.
  * Switch to a selected user.
  * Automatically return to the owner (user 0) when the device becomes idle or the screen turns off.
* **ADB‑based file explorer** for copying, moving, deleting and sharing files on connected devices.
* **Bookmarks and history** for frequently used commands.
* **Continuous command support**, e.g. `logcat`, `top` or `watch`.
* **Search and save command output** to a text file.
* **Backup and restore** of application settings and database.

---

## Using the Easy User Switcher

1. On your Android device, enable **Wireless debugging**: open *Settings → Developer options → Wireless debugging*.
2. Tap **Pair device with pairing code** and note the **IP address**, **pairing port** and **pairing code** presented.
3. In **Easy User Switcher**, open the **User Switcher** card on the home screen.
4. Enter the values from step 2 and tap **Pair**. After pairing succeeds, tap **Connect** (leave the port at `5555` unless your device uses a different port).
5. Tap **Load Users** to fetch the list of user profiles. Tap a user to switch; the app will automatically return to the owner (user 0) when the device becomes idle or the screen turns off.

---

## Important Notes

* This project is experimental and intended as a proof of concept.
* It is not affiliated with GrapheneOS and is not a security tool.
* The multi‑user features rely on ADB capabilities; ensure you understand the security implications of enabling wireless debugging.

---

## Credits & Licensing

This project is based on **aShell v7.1.0**, designed and developed by DP Hridayan. The original project is licensed under the **GNU General Public License v3.0**.

The modifications and the Easy User Switcher module were designed and developed by **Goohuizan** (the author of this fork) with the assistance of a ChatGPT agent. The original GPL v3.0 license still applies; see the `LICENSE.md` file for full details.

---

## Experimental Transparency

This fork was created through a series of iterations with a ChatGPT agent. It aims to explore automated user switching and improve multi‑user workflows on Android by leveraging ADB commands and aShell’s clean UI. It is not intended to replace the original aShell project but to demonstrate what might be possible in future.
