# Android

Die native Checkit-App lädt den normalisierten HTTPS-Vertrag vom Checkit-Backend. MainActivity bietet Haltestellensuche, lokale Auswahl, Vorschau und Widget-Pinning.

`BremenDepartureWidget` zeigt verbleibende Minuten ohne frei laufenden Chronometer. `WidgetUpdates` koordiniert Netzwerkzugriffe und aktualisiert den Glance-Zustand jedes Widgets. Der abschaltbare `LiveUpdateService` ist ein sichtbarer Foreground-Service (specialUse: dauerhafte, vom Nutzer aktivierte Homescreen-Abfahrtsanzeige). Bei eingeschaltetem Bildschirm lädt er jede Minute Daten und rendert alle zehn Sekunden Minuten und noch kommende Abfahrten neu. Bei ausgeschaltetem Bildschirm pausiert er ohne Wake-Lock; beim Einschalten lädt er erneut. Eine dauerhafte Benachrichtigung bietet einen Stopp-Knopf. Ohne Live-Modus greift der 15-Minuten-WorkManager-Fallback. Hersteller-Energiesparregeln und Force-Stop können Updates verhindern; der Datenstand bleibt sichtbar. Nach Installation die App einmal öffnen.

Build, Release-Signierung, Installation und Grenzen stehen in der [Projekt-README](../README.md).

Tests: `gradlew connectedDebugAndroidTest`. Der Debug-Testhost hostet das echte Widget in 180×110, 250×150 und 300×240 dp. APKs unter `app/build/outputs/apk/`.

