# Listen Here

An Android voice inbox for GTD in Obsidian. Hold the power button, say the task, and it lands in your inbox note as a checkbox with its date:

```
"call the dentist tomorrow at three thirty pm"  →  - [ ] Call the dentist (@2026-11-01 15:30)
```

Everything happens on the phone. The app has **no internet permission**: speech recognition runs on-device and nothing can leave the phone.

## How it works

- **Speech to text**: on-device, with the model you choose in settings. The app records you itself and stops when you stop talking.
  - **Whisper** ([whisper.cpp](https://github.com/ggml-org/whisper.cpp), built from source): tiny, base or small, English-only or multilingual, 32–190 MB. A model downloads in your browser; you import the file and the app keeps it only if its SHA-256 matches the pinned catalog ([`Models`](app/src/main/java/io/github/ramiawar/listenhere/Models.java)). The app itself never goes online.
  - **Cactus Whistle** ([model](https://huggingface.co/Cactus-Compute/whistle)): 17 MB, en/nl/de/fr/es/it/pl, bundled in the GitHub build. Its engine is prebuilt, so the F-Droid build leaves it out.
- **Parsing**: rule-based and on-device ([`TaskParser`](app/src/main/java/io/github/ramiawar/listenhere/TaskParser.java)). It strips filler like "remind me to" and "add a task to", and it understands dictated dates and times:
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

## Builds

| Flavor | Speech | Where |
|---|---|---|
| `fdroid` | Whisper only. Everything is built from source, and no model is bundled. | F-Droid |
| `full` | Whisper, plus Cactus Whistle bundled so it works out of the box. | GitHub releases, IzzyOnDroid |

```sh
git clone --recurse-submodules https://github.com/RamiAwar/listen-here.git
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testFdroidDebugUnitTest assembleFdroidRelease   # unsigned; F-Droid signs it

./scripts/fetch-whistle.sh                                 # full only: Whistle at pinned revisions, SHA-256 checked
./gradlew assembleFullRelease                              # debug-signed for local installs
adb install app/build/outputs/apk/full/release/app-full-release.apk
```

You need the Android SDK 35, NDK 29.0.14206865 and CMake 3.22. The build targets arm64 with ARMv8.2 (any phone from about 2018 on). Sign GitHub releases with your own key.

## F-Droid

Store metadata lives in [`fastlane/metadata`](fastlane/metadata/android/en-US). The draft recipe for [fdroiddata](https://gitlab.com/fdroid/fdroiddata) is in [`docs/fdroid`](docs/fdroid/io.github.ramiawar.listenhere.yml). It builds the `fdroid` flavor from a `vX.Y.Z` tag, with submodules.

## License

GPL-3.0. See [LICENSE](LICENSE).

## Third-party

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) is built from source. Whisper models are © OpenAI, MIT-licensed.
- The Whistle model and the Needle engine (`libneedle.a`) are © Cactus Compute, Apache-2.0, and are in the `full` build only. The engine ships as a prebuilt static library. It imports only memory, math, threading and file functions, with no networking, and the app has no internet permission.
