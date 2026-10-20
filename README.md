# Claude Usage (Android)

Your Claude subscription usage on the home screen and in the status bar.
An Android port of the ideas in
[plasma-claude-usage](https://github.com/gdevenyi/plasma-claude-usage).

> **This is a fork** of [gdevenyi/android-claude-usage](https://github.com/gdevenyi/android-claude-usage).
> It adds:
>
> - **Android 10+** — upstream needs Android 13. Below 13 there is no
>   notification permission to ask for; below 12 the notification's bars keep
>   the default tint (the percentages are still colour coded).
> - **Clock mode** — an always-on screen for a spare phone: a large clock, the
>   date and the battery level beside the widget's full layout. See
>   [Clock mode](#clock-mode).
>
> It keeps upstream's application id, so it installs over (and can't sit
> beside) the upstream app. Everything else is upstream's work; the changes
> are in the commits after upstream's `master`.

- **Home-screen widget** — session (5h) and weekly (7d) percentages, colour
  coded, with progress bars, reset countdowns and the per-model breakdown.
  It scales with the size you give it, from a 2×1 chip to a full panel.
- **Ongoing notification** — the same numbers in the shade; expand for the
  full breakdown.
- **History and forecast** — the app charts the current session (5 h) and
  weekly (7 d) quota windows from start to reset, with the trend extended as
  a dashed line racing the reset, plus a 3-week history of total and
  per-model quota. When a
  trend reaches 100% before the window resets, the widget and notification
  add a warning ("out ~13:10"). History lives only on the device and is
  deleted on logout.
- Tap the widget or the notification to open the app (which also refreshes).
  The notification's **Refresh** button and the widget's ↻ icon refresh in
  place. Otherwise it updates every 15 minutes in the background.

## Screenshots

The widget shows everything at four rows — both windows with reset times, the
per-model breakdown and your plan:

<img src="screenshots/widget-4row.png" width="380" alt="Widget at four rows">

Give it less room and it drops content rather than clipping it: the reset
times go first, then the labels.

<img src="screenshots/widget-3row.png" width="300" alt="Widget at three rows">
<img src="screenshots/widget-2row.png" width="300" alt="Widget at two rows">
<img src="screenshots/widget-1row.png" width="300" alt="Widget at one row">

The notification carries the same numbers, collapsed and expanded:

<img src="screenshots/notification-collapsed.png" width="440" alt="Collapsed notification">
<img src="screenshots/notification-expanded.png" width="440" alt="Expanded notification">

Clock mode, in its dark theme:

<img src="screenshots/clock.png" width="570" alt="Clock mode">

The app holds the settings and the history charts:

<img src="screenshots/settings.png" width="330" alt="Settings screen">

## Requirements

Android 10 or later, and a Claude subscription (Pro or Max).

## Install

Every build produces an APK you can install: open the latest run under
[Actions](https://github.com/killbeezero/android-claude-usage/actions) and
download the `apk` artifact (you need to be signed in to GitHub).

> This fork signs debug APKs with a fixed key kept in the repository
> (`app/debug.keystore`, the standard `android` / `androiddebugkey`
> credentials), so newer builds install over older ones. It is a debug key,
> not a secret: anyone can sign with it. Switching from an upstream build
> means uninstalling first, since that one is signed differently.

Or build it yourself:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You need the Android SDK; point `local.properties` at it with
`sdk.dir=/path/to/Android/Sdk`.

### Signing releases in CI

Gradle leaves the release APK unsigned, and Android will not install it. To
have CI sign it, create a keystore once and add four repository secrets:

```bash
keytool -genkey -v -keystore release.jks -alias claude-usage \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # paste as the KEYSTORE_BASE64 secret
```

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keep the
keystore file itself out of the repository — losing it means future releases
can no longer upgrade installs of the old one. F-Droid does not need any of
this; it signs with its own key.

## Logging in

Open the app and tap **Log in with Claude**. Your browser opens Claude's
authorization page; approve it, copy the code it shows you, and paste it back
into the app.

The app logs in on its own and holds its own credentials. It does not read,
copy or need the credentials from Claude Code on your computer — and logging
in here does not disturb any of your other Claude Code sessions.

## How it works

The app performs the OAuth PKCE flow that Claude Code uses, then polls
`https://api.anthropic.com/api/oauth/usage` with its own access token,
refreshing that token itself as needed. Nothing is sent anywhere else, and
the tokens stay in the app's private storage.

This uses an OAuth flow that Anthropic has not documented for third parties.
It works today; it could stop working if Anthropic changes it.

One quirk inherited from that flow: the PKCE verifier travels in the `state`
parameter of the authorization URL, so it ends up in your browser history.
That only matters for a login you start and abandon — anything able to read
your browser history could finish it while the code is still valid. Completing
(or simply redoing) the login makes the code useless.

## Clock mode

Meant for an old phone left on its charger. Open it from its own launcher
icon, **Usage Clock**, or from **Clock mode** in the app.

- Landscape and full screen, and the screen stays on for as long as it is
  showing — whatever the system's screen timeout says.
- A large clock (12/24 h follows the system) and the date in the system
  language, beside the widget's full layout: both windows with reset times,
  the per-model rows and the last update.
- The battery level in the top-left corner, with a bolt and "Charging" on the
  charger and red below 15%.
- The half-circle button cycles the theme: follow system, dark (on pure
  black), light. The ↻ button refreshes.
- Everything shifts a few pixels each minute, against burn-in.
- Long-press the usage panel to get back to the app; a stray tap does nothing.

Text is sized independently of the system font size, so the layout fits a
small screen even with large fonts.

## Settings

Log in / log out, turn the ongoing notification on or off, and choose the
refresh interval (15, 30 or 60 minutes — 15 is the shortest Android's
background scheduler allows).

## Support

If this is useful to you:

<a href="https://buymeacoffee.com/gdevenyi"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" height="48" alt="Buy me a coffee"></a>

## License

GPL-3.0-or-later.
