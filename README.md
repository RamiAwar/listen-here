# Listen Here

An Android voice inbox for GTD in Obsidian. Hold the power button, say the task, and it lands in your inbox note as a checkbox with its date:

```
"call the dentist tomorrow at three thirty pm"  →  - [ ] Call the dentist (@2026-11-01 15:30)
```

Everything happens on the phone. The app has **no internet permission**: speech recognition runs on-device and nothing can leave the phone.

## How it works

- **Speech to text**: [Cactus Whistle](https://huggingface.co/Cactus-Compute/whistle) is bundled in the app. It's a 17 MB model that handles en/nl/de/fr/es/it/pl. The app records you itself and stops when you stop talking.
- **Parsing**: rule-based and on-device ([`TaskParser`](app/src/main/java/dev/inbox/capture/TaskParser.java)). It strips filler like "remind me to" and "add a task to", and it understands dictated dates and times:
  - `tomorrow`, `tonight`, `this weekend`, `end of the month`
  - `by friday`, `next tuesday`, `in two weeks`, `in twenty minutes`
  - `November fifth`, `the twelfth of December`, `by the twenty-first`
  - `at three thirty pm`, `half past four`, `quarter to five`, `noon`
- **Output**: the default `- [ ] {task}{due}` with ` (@{date} {time})` matches the [Reminder plugin](https://github.com/uphy/obsidian-reminder). You can change it in settings, for example to the Tasks plugin's `📅 {date}`.
- **Saving**: through `obsidian://new?…&append=true&silent=true`, so sync plugins like LiveSync pick the task up immediately. If your vault lives in shared storage, you can instead have it write the file directly.
- **Lock screen**: capture works without unlocking. Tasks queue inside the app, and one silent notification (however many are queued) files them all in a single append after you unlock. Anything still queued also goes along with your next unlocked capture.

## Triggers

- **Hold power** (best). Make the app your digital assistant under *Settings → Apps → Default apps → Digital assistant app*. If your ROM hides the "press and hold power" option (GrapheneOS does), set it over adb:
  ```sh
  adb shell settings put global power_button_long_press 5    # hold power = assistant
  adb shell settings put global key_chord_power_volume_up 2  # power + vol up = power menu
  ```
- **Quick Settings tile** "Capture task".
- **Corner swipe** (gesture navigation; not available on the lock screen).
- **Share** text from any app. Each line becomes one task.

## Build

```sh
./scripts/fetch-whistle.sh          # engine + model at pinned revisions, SHA-256 checked
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

You need the Android SDK 35, NDK 29.0.14206865 and CMake 3.22. The build targets arm64 only, which the speech engine requires.

The release build is signed with the debug key so it installs directly. Use your own key if you publish it.

## Third-party

The Whistle model and the Needle engine (`libneedle.a`) are © Cactus Compute and licensed Apache-2.0. The engine ships as a prebuilt static library. It imports only memory, math, threading and file functions, with no networking, and the app has no internet permission.
