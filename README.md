# Checkit

**Deine Haltestelle. Bei dir.** Kostenlose, nichtkommerzielle Abfahrtsanzeige für Bremen.

[Website & Downloads](https://checkit-omega-two.vercel.app) · [Live-Board](https://checkit-omega-two.vercel.app/board.html) · [Releases](https://github.com/Hamido212/checkit/releases)

## Was funktioniert

- Bremen Hauptbahnhof als Standard, Haltestellensuche und gespeicherte Auswahl.
- Bus, Straßenbahn und Regionalverkehr über Transitous/MOTIS; Verspätungen, Ausfälle und Kennzeichnung von Echtzeit gegenüber Fahrplan.
- Responsive Website mit Live-Vorschau und eigenem Abfahrtsboard; installierbare Web-App mit lokalem Offline-Stand.
- Native Android-App mit Glance-Homescreen-Widget, echten Spalten, anpassbarer Zeilenanzahl, manueller Aktualisierung, WorkManager und lokalem Cache.
- Natives portables Windows-Desktop-Widget: verschieben, skalieren, im Vordergrund halten, Haltestelle wählen, Einstellungen speichern.
- Vorlage für GitHub Actions unter `docs/workflows/build.yml` (noch nicht aktiviert; der verbundene GitHub-Zugang hat keinen Workflow-Schreibzugriff).

Windows ist ein eigenständiges Desktop-Fenster, keine Erweiterung des Windows-11-Widgets-Panels. iPhone verwendet die Web-App (Safari → Teilen → Zum Home-Bildschirm). Ein natives iOS-Widget ist nicht enthalten; dafür sind Xcode, Apple-Signierung und ein separater WidgetKit-Build erforderlich.

## Installieren

**Android:** `checkit-android.apk` aus den Releases installieren, Checkit öffnen, Haltestelle auswählen und „Widget hinzufügen“ tippen. Alternativ über das Widgets-Menü des Launchers. Alle Widgets verwenden dieselbe Haltestelle. Ein vorhandener alter Debug-Build hat einen anderen Signaturschlüssel und muss vor dem Wechsel zur Release-APK deinstalliert werden (lokale Auswahl geht dabei verloren).

**Windows:** `checkit-windows-x64.zip` entpacken und `Checkit.exe` starten. Keine zusätzliche .NET-Installation nötig. Über die Kopfzeile verschieben, unten rechts skalieren; ◇ schaltet „Immer im Vordergrund“ um. Die EXE ist nicht mit einem kommerziellen Windows-Code-Signing-Zertifikat signiert.

## Aktualisierung

Web und Windows: 60 Sekunden. API-Cache: 45 Sekunden. Android: auf Tipp auf ↻ und ungefähr alle 15 Minuten im Hintergrund. Android kann diesen Rhythmus verzögern. Restzeiten stehen im Vordergrund: Web/App/Windows zeigen Minuten, das Android-Widget einen selbstständig laufenden MIN:SEK-Countdown. Zusätzliche Uhrzeiten sind Europe/Berlin. Ein negativer Widget-Countdown bedeutet, dass die Abfahrt laut letztem Stand bereits vorbei ist; ↻ lädt neue Daten. Datenstand und veraltete Daten werden sichtbar gekennzeichnet. Keine Garantie der Echtzeitabdeckung oder Abfahrtsdaten.

## Lokal starten

Node.js 22 oder neuer:

```sh
npm start
npm test
npm run build
```

Öffnen: http://127.0.0.1:8787. Keine npm-Laufzeitabhängigkeiten nötig.

## Android bauen

JDK 17+, Android SDK 34. `android/gradlew -p android assembleDebug`.

Unter Windows erzeugt `scripts/build-android.ps1` einen signierten Release-Build. Der private Schlüssel liegt ausschließlich in `%LOCALAPPDATA%\CheckitSigning\checkit-release.jks`, das Passwort Windows-DPAPI-verschlüsselt daneben. Beide gehören nicht in Git. Diesen Schlüssel sicher sichern: künftige APK-Updates benötigen denselben Schlüssel. Für andere Build-Umgebungen `CHECKIT_KEYSTORE` und `CHECKIT_KEY_PASSWORD` setzen. Die CI-Vorlage erzeugt absichtlich nur Debug-Testartefakte, keine anders signierten öffentlichen Updates.

API-Adresse überschreiben: `-PapiBaseUrl=https://dein-projekt.vercel.app`. Der Standard zeigt auf die veröffentlichte Checkit-API. Nur HTTPS ist für die Distribution erlaubt.

Widget-Gerätetest mit laufendem Emulator: `android/gradlew -p android connectedDebugAndroidTest`. Er lädt echte Daten, hostet das echte Glance-Widget in drei Größen und prüft Zeitspalten und Datenstand. Debug-Testhost ist nicht Bestandteil der Release-App.

## Windows bauen

```sh
dotnet publish windows/Checkit.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o artifacts/windows
```

Benötigt .NET 10 SDK. Einstellungen/Cache: `%LOCALAPPDATA%\Checkit`.

## Vercel

```sh
npx vercel deploy --prod
```

`vercel.json` verwendet statische Dateien aus `public` und eine Node-Funktion unter `api/index.js`. Website und native Clients teilen denselben normalisierten API-Vertrag. APK/ZIP liegen in GitHub Releases, nicht im Vercel-Build.

## Daten, Lizenz und Grenzen

Code: MIT. Daten unter den jeweiligen [Transitous-Quelllizenzen](https://transitous.org/sources/), einschließlich © OpenStreetMap-Mitwirkende. Beachte die [Transitous-Nutzungsbedingungen](https://transitous.org/api/): öffentliche Open-Source-Anwendung, nichtkommerziell, geringe Last. Bei größerer Nutzung muss die Infrastruktur mit dem Anbieter abgestimmt werden. Keine offizielle Anwendung von BSAG oder VBN. `VbnProvider` bleibt ein nicht aktivierter Erweiterungspunkt; ein eigener VBN-Feed ist nicht implementiert.

Keine Accounts, kein Tracking, kein Datenbankdienst. Präferenzen und Cache bleiben lokal. Vercel und GitHub verarbeiten technische Verbindungsdaten beim Abruf.


