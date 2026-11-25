# DataVault

LSPosed / LSPatch module by **Rs118**. It adds a floating folder button to any app you pick,
which opens a panel for taking **multiple full data backups** of that app and **loading any of them
later** – handy for switching between accounts.

## Build

Push this folder to a GitHub repo. The workflow in `.github/workflows/build.yml` builds the APK
(Actions -> Build APK -> artifact `DataVault-apk`). Or locally: `gradle assembleDebug`.

## Use

1. Install the APK, enable **DataVault** in LSPosed, tick the apps you want it in.
2. Force-stop those apps and open them again.
3. Tap the round folder button at the top (drag it if it is in the way).
4. Log into account 1 -> type a name -> **Create backup**. Log into account 2 -> **Create backup**.
5. Tap a saved backup -> **Load & restart** to switch accounts.

## How it works

* A backup copies the whole private data directory of the app (`/data/user/0/<pkg>`), optionally the
  device-protected (`/data/user_de/0/<pkg>`) and external (`Android/data/<pkg>`) folders, into
  `<data dir>/vault_backups/<timestamp>/`. Cache folders are skipped unless enabled.
* **Load** never overwrites files while the app is running. It queues the load, kills all processes
  of the app and relaunches it. The load runs at the very start of the new process (from the Xposed
  `handleLoadPackage` callback), before the app opens any database or preference file, so nothing
  can write the old state back on top of the restored one.
* "Backup before loading" (on by default) first saves the current state as an `[auto]` backup (last 3 kept).
* **Clean backup** uses the same mechanism: the app is restarted and the backup is taken while
  nothing has files open. **Create backup** copies live, which is fine for most apps.
* Settings are stored in `vault_backups/settings.properties`, not in the app's SharedPreferences,
  so loading a backup never changes them.

## Notes / limits

* Backups live inside the app's own data folder: **clearing the app's data (or uninstalling) deletes them.**
* Apps that bind login to the device/Keystore, or that re-verify on a server, may still log you out.
* If the app does not reopen by itself after "Load & restart" (some ROMs block background launches),
  open it manually – the queued load runs on that start.
* Cloning a package inside a virtual space: hook the virtual-space host app, as with other data tools.
