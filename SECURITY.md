# Security

## Reporting

Please report vulnerabilities privately through GitHub's "Report a vulnerability" (Security tab) rather than in a public issue.

## Threat model

Listen Here turns speech into a line in your Obsidian vault. What it protects:

- **Your speech and tasks stay on the device.** The app does not request `INTERNET`, so Android blocks every network connection it might attempt. Speech recognition runs in-process. Recordings live only in memory and are never written to disk or logged.
- **Model files are verified.** Whisper models are downloaded by your browser, not the app. An imported file is kept only if its size and SHA-256 match an entry in the pinned catalog. Anything else is deleted, so a tampered or swapped file never reaches the native parser.
- **The lock screen stays locked.** Capture works over the lock screen, but nothing else does there. Settings refuse to open, and filing to Obsidian waits until you unlock: queued tasks sit in app-private storage behind a single notification that shows only a count.
- **Other apps can't abuse it.** The only exported entry points are the launcher, the assistant intent, `ACTION_SEND` text sharing and the Quick Settings tile, which is bound only by the system. Settings and queue filing are not exported.

## Hardening in place

| Area | Measure |
|---|---|
| Shared text (untrusted, from other apps) | Capped at 10 lines / 2,000 characters, so nothing hides behind "+N more". It is saved only after you tap Save. |
| Markdown injection | Backticks, HTML and `![` are escaped in task text. This stops Dataview inline JS (`` `$= …` ``) and stops remote images that would fetch, and leak your IP, when the note renders. |
| Parser robustness | Input is capped at 300 characters per task, with no recursive regex repetition, bounded numbers, and a fallback that never crashes. Fuzz tests are in `ParserFuzzTest`. |
| Native boundary | The transcript crosses JNI as UTF-8 bytes (`NewStringUTF` aborts on 4-byte characters), and the sample count is clamped to the array length. |
| Tapjacking | Taps are ignored while another app's window covers the card or the Save button. |
| Supply chain | whisper.cpp is a pinned submodule built from source. Whistle (`full` build only) is fetched at pinned revisions and SHA-256 checked (`scripts/fetch-whistle.sh`). Model imports are hash-checked. The Gradle distribution is checksum-pinned. There are no runtime library dependencies. |
| Logging | The app writes nothing to logcat. |

## Known limits

- **Prebuilt engine (`full` build only).** `libneedle.a` (Cactus Compute, Apache-2.0) is a binary without published source. We have checked that it imports no networking functions. The missing `INTERNET` permission is the enforced boundary. The `fdroid` build contains no prebuilt code.
- **Exported capture activity.** Another app in the foreground can open the capture card, and with the default settings that starts the microphone. Android shows the mic indicator, and the transcript goes only to your vault; the other app never receives it. Turn off "Start listening as soon as it opens" if this matters to you.
- **Physical access.** Anyone holding your locked phone can add tasks to your queue. They can't read the queue or your vault.
- **Release signing.** `assembleRelease` signs with the local debug key for convenience. Sign published builds with your own key.
