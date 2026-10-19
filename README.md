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

## Android

The Android implementation is in `android/`; GitHub Actions builds it separately. This change focuses on the iPhone scaffold and does not change Android behavior.
