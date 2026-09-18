# Verification — 0.2.0

- Node test runner: 4 passing tests covering normalization, invalid times/colors, upstream caching/failure, API validation and static traversal rejection.
- Public Vercel health and live departures verified. Bremen Hbf and Bremen Domsheide search/selection exercised in the browser.
- Android release builds successfully; APK signature checked with Android SDK apksigner.
- Android API 36.1 emulator: actual Glance RemoteViews hosted at 180×110, 250×150 and 300×240 dp. Checks include data status, countdown/target separation, inner-frame bounds, and countdown text changing without a network refresh. Screenshots visually inspected after correcting launcher-padding row overflow.
- Widget picker now has a native Android 12+ preview layout and a rendered PNG fallback for older launchers. Sample departures explicitly labeled as preview.
- Windows self-contained x64 publish succeeds. Native window inspected with real data: countdown primary, absolute departure secondary, visible frame and saved stop selection.
- Source repository connected to Vercel for website deployments.

Limitations: no physical Android phone/iPhone tested. Windows application is an independent desktop window, not a Windows Widgets-panel extension. iOS delivery is a PWA, not native WidgetKit. Android periodic network updates are OS-controlled (minimum 15 minutes); the native countdown continues independently and may become negative after a departure until refresh. Shared stop settings for all widgets. GitHub build workflow is provided as a template because the connected OAuth credential lacks workflow write scope. Windows binary has no commercial Authenticode certificate.
