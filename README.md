# Oralyvo mobile

Native iOS (SwiftUI) and Android (Kotlin/Jetpack Compose) clients for [Oralyvo](https://oralyvo.app), using the existing [Speech2Text-Pro](https://github.com/Tmanbuiz/Speech2Text-Pro) Cloudflare backend.

## iPhone version 0.2.0

Replaces the static scaffold screen with:

- Sign-in, account creation and password recovery through the existing Oralyvo account pages. Only authentication uses WKWebView; email/password is supported here. Social-provider buttons are hidden because embedded OAuth is restricted by some providers. Social-only users can set a password using password recovery.
- Keychain session storage and refresh through the public account configuration returned by `/api/health`.
- Native microphone recording, up to 20 minutes per clip, and accurate transcription after stopping via `/api/transcribe`.
- Translation and AI notes via `/api/process`.
- Save transcripts/results to account history, read existing cloud history, and share text using the native share sheet.
- Visible errors and retry controls. Failed transcription preserves the current recording for retry within the current screen session.

This is recording-then-transcription, not streaming Live AI. Keep the app open while recording. Moving the app out of the foreground or receiving an audio interruption stops recording. Unsaved recordings and results are temporary: save the text to history or share it before starting again, signing out, or closing the app. Audio replay, background recording and real-time streaming are not included in this version.

## Build and release

The Xcode project is generated from `ios/project.yml`:

```sh
cd ios
xcodegen generate
xcodebuild build -project OralyvoApp.xcodeproj -scheme OralyvoApp \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO
```

The GitHub `iOS build` workflow compiles on macOS and saves a simulator app artifact. A simulator app cannot be installed on an iPhone.

The signed workflow checks installed App Store provisioning profiles against the signing identities in the build keychain, then writes an explicit `signing.xcconfig` and export-options plist for the exact app bundle. Archive and export use the same profile, team and certificate. If no matching profile/private-key pair exists, the preflight stops with a specific error before archiving. Optional Codemagic secret `CERTIFICATE_PRIVATE_KEY` can supply a reusable PEM signing key; keep it out of the repository and logs. Without it, the existing fresh-key flow remains in use and is subject to the Apple account's certificate availability.

After the build passes, run Codemagic's existing **ios-testflight** workflow on the approved branch/commit. It uses the existing App Store Connect integration and uploads a signed build. Wait for Apple processing, then install the new version in TestFlight. Updating source alone does not change an already-installed app. Confirm the build number exceeds the previous App Store Connect build number.

## Device verification before distribution

1. Launch without an account: welcome screen offers sign-in rather than the old scaffold text.
2. Sign in with email/password and complete Turnstile. Verify wrong credentials and offline loading give recoverable errors.
3. Deny microphone permission: explanation appears. Enable permission in iOS Settings and retry.
4. Record a short clip, stop, transcribe, and compare the text with the spoken words.
5. Interrupt recording by locking/backgrounding or taking a call: recording stops and can be transcribed after returning.
6. Disconnect before transcription: error appears and reconnect/retry uses the retained recording.
7. Translate, generate AI notes, save each result, and find it in History and the web app.
8. Sign out and sign into a different account: previous account history and unsaved text are absent.
9. Leave the session long enough to expire, then verify token renewal and an authenticated request.
10. Confirm share actions work on a physical iPhone and test a near-limit clip.

## mcp-use developer checks

`tools/mcp-checks` uses [mcp-use](https://github.com/mcp-use/mcp-use) 2.10.1 for read-only developer tools. SDK telemetry is explicitly disabled before import. These tools accept no account credentials, do not read transcripts, and do not modify either repository or the backend.

```sh
cd tools/mcp-checks
npm ci --ignore-scripts
npm test
npm run check:live
npm start
```

The local MCP endpoint is `http://127.0.0.1:3000/mcp`. Tools:

- `check-backend`: public deployment readiness, returned as capability booleans only.
- `inspect-ios-entry`: source inventory and detection of the original placeholder.

`npm test` exercises real MCP initialize/list/call requests with a fixture backend and verifies safe output and network failure handling. `check:live` calls the actual public health endpoint. Neither substitutes for Xcode compilation or physical-device checks.

## Android version 0.2.0

The Android implementation in `android/` now provides the same recording-then-transcription flow, translation, AI notes, cloud history, and native sharing. Authentication uses the hosted Oralyvo email account pages for Turnstile, account creation, verification instructions and password recovery. Embedded social-provider authentication is not included.

Sessions use AES-GCM encryption with a non-exportable Android Keystore key and a session file excluded from backup. Existing plaintext sessions migrate when readable. Tokens renew through the configured account provider. Offline history is isolated by account ID. The original shared `history.json` is preserved on disk but hidden because old entries have no owner information and cannot safely be assigned to an account.

Recording stops when leaving the foreground and is capped at 20 minutes. Stop and Transcribe are separate actions; failed transcription keeps the clip available within the current screen session. Text is copied to account-specific offline history. **Save to account** uploads it to cloud history, also visible on iPhone/web. Audio clips are temporary and are replaced by a new recording or removed when leaving the recording session.

GitHub's **Android build** workflow runs JVM API contract tests, Android lint, debug APK compilation, and emulator tests for launch, encrypted storage and account isolation. On `main` it also builds the release AAB. Local equivalents with JDK 17, Android SDK 34 and Gradle 8.9 installed:

```sh
cd android
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
gradle :app:connectedDebugAndroidTest  # requires a running emulator/device
```

Download `oralyvo-android-debug` from a successful Actions run to obtain the installable debug APK. `oralyvo-android-release-aab` is a Play Console bundle; it is only release-signed when the existing keystore secrets are configured. A debug APK may require uninstalling an existing app signed with another key, which removes its local data; save needed transcripts to your account first.

Before distributing, verify email sign-in and Turnstile on a real device; deny/restore microphone permission; record, background and return; transcribe in normal/offline conditions; translate, generate notes and save; compare History with the web app; sign out and switch accounts; and confirm session renewal. Tests use fixtures and emulator accounts, not a real production user.
