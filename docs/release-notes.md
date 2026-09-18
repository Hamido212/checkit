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
