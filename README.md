# Easy User Switcher (based on aShell v7.1.0)

⚠️ Experimental Proof of Concept  
This application was created as an experiment using a ChatGPT agent and is intended to explore usability improvements for Android multi-user environments — especially on GrapheneOS.

---

## About This Project

This repository contains a **modified version of the official aShell v7.1.0 release**, extended with an additional **Easy User Switcher module**.

aShell is used as the foundation because it is a well-designed, open-source Android application that provides a polished Material Design interface to execute ADB commands locally or over wireless debugging.

This makes it an ideal base to prototype advanced user switching behavior.

---

## Why This Exists

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

---

## Key Features

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

---

## Using the Easy User Switcher

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

---

## Experimental Transparency

This application was created and iterated with the assistance of a ChatGPT agent as part of an exploration into automated Android multi-user workflows.

The goal is to experiment, prototype, and explore — not to replace the original aShell project.

---
