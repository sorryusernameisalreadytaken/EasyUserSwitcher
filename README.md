# Easy User Switcher (based on aShell v7.1.0)

<<<<<<< HEAD
⚠️ **Experimental Proof of Concept**

This application was created using a ChatGPT agent as an experiment to explore usability improvements for Android’s multi‑user environment, particularly on **GrapheneOS**. The goal is to demonstrate that it is possible to automate profile switching on Android using ADB commands and a polished Material Design interface.
=======
⚠️ Experimental Proof of Concept  
This application was created as an experiment using a ChatGPT agent and is intended to explore usability improvements for Android multi-user environments — especially on GrapheneOS.
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb

---

## About This Project

<<<<<<< HEAD
This repository contains a **modified version of the official aShell v7.1.0 release**, extended with an additional **Easy User Switcher** module. The original project, aShell, is an open‑source Android application that provides a refined interface for executing ADB commands locally or over wireless debugging. It serves as a solid foundation for prototyping advanced user switching behaviour.

Unlike the original project, this fork is designed to show how ChatGPT can be used to build a custom Android app. It incorporates features tailored to multi‑user workflows on GrapheneOS, where up to 32 profiles can be created. It can be cumbersome to manually switch back to the owner profile after using a secondary profile. By automating ADB commands, the app can return to the main account when the device becomes idle, ensuring you don’t miss notifications or access to essential apps.
=======
This repository contains a **modified version of the official aShell v7.1.0 release**, extended with an additional **Easy User Switcher module**.

aShell is used as the foundation because it is a well-designed, open-source Android application that provides a polished Material Design interface to execute ADB commands locally or over wireless debugging.

This makes it an ideal base to prototype advanced user switching behavior.
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb

---

## Why This Exists

<<<<<<< HEAD
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
=======
GrapheneOS supports up to 32 user profiles.

In daily usage, switching between profiles can become cumbersome:

- It is easy to forget switching back to the main (owner) profile.
- Secondary profiles may not contain apps like Signal, contacts, or other daily tools.
- Users can unintentionally remain in a secondary profile and miss notifications or functionality.

Android already provides ADB commands to switch between users:
am switch-user <USER_ID>

If ADB is running on the host (or locally via Shizuku/root), it is possible to switch back to the owner profile from any guest profile.

This project demonstrates:

> It is technically possible to automate safe and intelligent profile switching behavior.

---

## Vision

This project is currently a Proof of Concept.

The long-term idea could be:

- App icons representing apps from other profiles
- Tap an icon → automatically switch to that profile
- Launch the selected app
- Automatically return to the owner profile when the device becomes idle

This repository demonstrates the first building block of that idea.
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb

---

## Key Features

<<<<<<< HEAD
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
=======
- Material Design 3 UI (light and dark theme)
- Execute ADB and shell commands locally (via Shizuku or root)
- Wireless ADB pairing and connection
- **Easy User Switcher module**
  - Pair with device wireless debugging
  - List all user profiles
  - Switch to selected user
  - Automatically return to owner (User 0) when:
    - Device becomes idle
    - Screen turns off
- Command history and bookmarks
- Continuous command execution (logcat, top, etc.)
- Output search and save
- Backup and restore settings
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb

---

## Using the Easy User Switcher

<<<<<<< HEAD
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
=======
1. Enable **Wireless debugging**  
   Settings → Developer Options → Wireless debugging

2. Tap **Pair device with pairing code**  
   Note:
   - IP address  
   - Pairing port  
   - Pairing code  

3. Open **Easy User Switcher**

4. Enter pairing data and tap **Pair**

5. Tap **Connect** (default port 5555 unless your device differs)

6. Tap **Load Users**

7. Select a user profile

The app will automatically return to the owner account (User 0) when the device becomes idle or the display turns off.

---

## Important Notes

- This is an experimental project.
- It is not affiliated with GrapheneOS.
- It is not intended as a security tool.
- It is a usability exploration.

Multi-user behavior on Android is heavily permission-restricted.  
This app relies on ADB-level capabilities.

---

## Credits & Licensing

This project is based on:

**aShell v7.1.0**  
Designed and developed by DP Hridayan  
Licensed under GNU General Public License v3.0

This modified version (Easy User Switcher module and experimental extensions)  
is designed and developed by **Goohuizan**.

The original license (GPL v3.0) applies.  
See `LICENSE.md` for full details.
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb

---

## Experimental Transparency

<<<<<<< HEAD
This fork was created through a series of iterations with a ChatGPT agent. It aims to explore automated user switching and improve multi‑user workflows on Android by leveraging ADB commands and aShell’s clean UI. It is not intended to replace the original aShell project but to demonstrate what might be possible in future.
=======
This application was created and iterated with the assistance of a ChatGPT agent as part of an exploration into automated Android multi-user workflows.

The goal is to experiment, prototype, and explore — not to replace the original aShell project.

---
>>>>>>> 98e5d2b2a1b3095b8fb48368c8037bfe466ddfeb
