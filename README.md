# oralyvo-mobile

Native iOS (Swift/SwiftUI) and Android (Kotlin/Jetpack Compose) apps for
[Oralyvo](https://oralyvo.app) -- two separate codebases, not a shared
Flutter/React Native app, by design.

Both apps are clients to the **existing** Cloudflare Worker backend in
[Speech2Text-Pro](https://github.com/Tmanbuiz/Speech2Text-Pro) (same one that
powers oralyvo.app). No backend changes are needed for either app -- they
call the same REST API the web app already uses.

## Layout

- `/ios` -- SwiftUI app. Project file is **generated**, not committed --
  see below.
- `/android` -- Kotlin + Jetpack Compose app, plain Gradle project.
- `/codemagic.yaml` -- CI config. Builds both apps on push to `main`.

## Why no Mac is needed

There's no Mac anywhere in this project's toolchain. The iOS project is
defined by `ios/project.yml` ([XcodeGen](https://github.com/yonaskolb/XcodeGen)
format) instead of a committed `.xcodeproj` -- XcodeGen turns that into a
real Xcode project on demand. [Codemagic](https://codemagic.io) runs
`xcodegen generate` on a macOS cloud runner, then builds it there. Android
needs no special handling -- Gradle builds happen fine on Linux, which is
what Codemagic (and the Android SDK/JDK already set up on the Windows dev
machine) uses.

No `gradlew` wrapper is committed yet (the wrapper jar is a binary file,
awkward to hand-author correctly without running `gradle wrapper` once
somewhere). Opening `/android` in Android Studio will generate it
automatically on first sync; CI calls the system `gradle` directly in the
meantime.

## Current state (scaffold stage)

Both apps are placeholder screens right now -- just enough to prove the
build pipeline works end to end in CI, since there's no local way to verify
an iOS build, and no Android Studio GUI used here either. Sign-in,
recording, live/accurate transcription, and history come next, layered in
incrementally so each change can be checked in CI before the next one lands
on top of it.

Sign-in requires solving a Cloudflare Turnstile challenge (the same bot
protection the web app uses) -- each app's sign-in screen will embed a small
WebView/WKWebView just for that one step, since there's no native Turnstile
SDK for iOS/Android.

## CI

See `codemagic.yaml`. Right now both workflows build unsigned/debug
artifacts on every push to `main`, so a broken build fails fast. Code
signing (Apple Developer team provisioning, Play Console upload key) gets
added once the apps are far enough along to actually install on a real
device / submit to TestFlight or Play's internal testing track.
