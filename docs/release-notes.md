# Checkit 0.3.0 — Favoriten & Abfahrts-Erinnerungen

- **Favoriten:** Mehrere Haltestellen lokal speichern, per Tipp wechseln und einzeln entfernen. Web (Board), Android-App und Windows-Desktop teilen dasselbe Konzept; alles bleibt lokal auf dem Gerät, kein Account nötig. Die bisherige Auswahl wird beim ersten Start automatisch als Favorit übernommen.
- **Abfahrts-Erinnerung:** Pro Abfahrt eine Erinnerung 5, 10 oder 15 Minuten vorher. Web nutzt bei erteilter Berechtigung die Service-Worker-Benachrichtigung, auch auf unterstützten mobilen Browsern (Board muss geöffnet sein). Android plant Alarme per AlarmManager (mit Berechtigung exakt, auch im Standby), Windows zeigt bei laufender App ein Popup mit Ton. Aktive Erinnerungen lassen sich einsehen und löschen.
- Android: neue Berechtigung `SCHEDULE_EXACT_ALARM` für exakte Erinnerungen.
- Gelöschte letzte Favoriten bleiben nach einem Neustart auf Android und Windows gelöscht.
- Android stellt noch bevorstehende Alarme nach einem Neustart oder App-Update wieder her. Abfahrten bleiben wie auf Web und Windows bis zu einer Minute nach der angegebenen Zeit als „jetzt“ sichtbar.
- Wenn der Browser keine Systembenachrichtigung anzeigen kann, weist das Board sichtbar darauf hin. Ein geschlossener Tab kann ohne Push-Dienst keine Web-Erinnerung auslösen.

# Checkit 0.2.2 — Windows-Startkorrektur

- Behebt einen Startabsturz in Windows 0.2.1: Das weiße ICO war für Explorer lesbar, aber nicht für den WPF-Bilddecoder. Alle sechs Größen werden jetzt als kompatible 32-Bit-Bitmap gespeichert.
- Direkter Download von Checkit.exe; weiterhin portabel ohne Installation. ZIP bleibt als Alternative verfügbar.
- Android bleibt unverändert auf 0.2.1.
- WPF-Iconprüfung sowie tatsächlicher EXE-Start mit Live-Abfahrten erfolgreich geprüft.

# Checkit 0.2.1

- Android-Live-Modus: neue Abfahrten jede Minute bei eingeschaltetem Bildschirm, mit abschaltbarer dauerhafter Benachrichtigung.
- Minutenanzeige ohne negative Countdown-Werte; abgefahrene Verbindungen werden automatisch entfernt.
- Gemeinsame Aktualisierung aller Widgets und automatische Aktualisierung der geöffneten App.
- Einheitliches weißes Pfeil-Logo für Favicon, Web-App, Android und Windows.
- Weiße, lesbare Schrift in der dunklen Android-App.

APK über die bestehende Version installieren und Checkit einmal öffnen. Live-Modus ist standardmäßig aktiv. Bei Xiaomi gegebenenfalls Autostart und Akku → Keine Beschränkungen für Checkit erlauben. Android kann den Dienst durch Energiesparregeln oder manuelles Beenden anhalten.

Windows 0.2.1 enthält das neue weiße App-Symbol.

# Checkit 0.2.0

Erste öffentliche Bremen-Version.

- Website mit Live-Vorschau, Downloadbereich und installierbarem Web-Board.
- Signierte Android-APK mit Homescreen-Widget, Haltestellensuche und manuellen/periodischen Updates.
- Portables Windows-x64-Desktop-Widget mit gespeicherten Einstellungen.
- Echte Verkehrsdaten, Verspätungen, Ausfälle und Offline-/Cache-Zustände.

Android: APK installieren und Checkit öffnen. Alte Debug-Version bei Signaturkonflikt zunächst selbst deinstallieren. Automatische Updates ungefähr alle 15 Minuten, vom System abhängig.
Windows: ZIP entpacken, Checkit.exe starten. EXE ohne kommerzielles Code-Signing-Zertifikat.
iPhone: Website in Safari zum Home-Bildschirm hinzufügen. Kein natives iOS-Widget.

Daten: Transitous / MOTIS. Kein offizielles BSAG-/VBN-Produkt. Quellcode unter MIT; Verkehrsdaten unter eigenen Quelllizenzen.
