# Android

Die native Checkit-App lädt den normalisierten HTTPS-Vertrag vom Checkit-Backend. MainActivity bietet Haltestellensuche, lokale Auswahl, Vorschau und Widget-Pinning.

`BremenDepartureWidget` rendert ausschließlich Repository-Daten mit `SizeMode.Exact`, festen Linien-/Zeitspalten und einer flexiblen Zielspalte. `RefreshWorker` erledigt Netzwerkzugriffe im IO-Kontext, aktualisiert danach Glance und plant Hintergrundupdates mit 15 Minuten Mindestintervall. Restzeiten laufen über native Android-Chronometer im Launcher weiter, ohne zusätzliche Netzwerkabfragen. MIN:SEK ist die verbleibende Zeit; negative Werte bedeuten eine bereits vergangene Abfahrt. Ergänzende Uhrzeiten sind Europe/Berlin. Lange Ziele werden einzeilig mit Ellipsis dargestellt.

Build, Release-Signierung, Installation und Grenzen stehen in der [Projekt-README](../README.md).

Tests: `gradlew connectedDebugAndroidTest`. Der Debug-Testhost hostet das echte Widget in 180×110, 250×150 und 300×240 dp. APKs unter `app/build/outputs/apk/`.

