package de.bremen.transit

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import de.bremen.transit.data.*
import de.bremen.transit.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LiveUpdateTest {
    @Test fun appDarkThemePreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("MainActivity", android.content.Context.MODE_PRIVATE).edit().putBoolean("notification-asked", true).commit()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            Thread.sleep(4000)
            instrumentation.runOnMainSync {
                val view = activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                java.io.File(context.getExternalFilesDir(null), "app-dark-theme.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            context.stopService(Intent(context, LiveUpdateService::class.java))
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun countdownNeverNegativeAndExpiredDeparturesDisappear() {
        val now = Instant.parse("2026-09-18T10:00:00Z").toEpochMilli()
        assertEquals("jetzt", TransitDisplay.remaining("2026-09-18T09:59:13Z", now))
        assertEquals("1 min", TransitDisplay.remaining("2026-09-18T10:00:01Z", now))
        assertEquals("2 min", TransitDisplay.remaining("2026-09-18T10:01:01Z", now))
        val departure = Departure("test", "6", "Flughafen", null, "2026-09-18T10:00:00Z", null, 0, false, false)
        assertTrue(TransitDisplay.upcoming(departure, now))
        assertTrue(TransitDisplay.upcoming(departure, now + 59_999))
        assertFalse(TransitDisplay.upcoming(departure, now + 60_000))
        assertTrue(TransitDisplay.upcoming(departure, now - 1))
    }

    @Test fun deletingLastFavoriteSurvivesRepositoryReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("departure-cache", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getString("favorite-stops", null)
        try {
            preferences.edit().remove("favorite-stops").commit()
            val repository = TransitDisplay.repository(context)
            val initial = repository.getFavorites().single()
            repository.removeFavorite(initial.id)
            assertTrue(TransitDisplay.repository(context).getFavorites().isEmpty())
        } finally {
            val edit = preferences.edit()
            if (previous == null) edit.remove("favorite-stops") else edit.putString("favorite-stops", previous)
            edit.commit()
        }
    }

    @Test fun savedAlarmIsRestoredAfterBootBroadcast() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("departure-alarms", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getString("alarms", null)
        val departure = Departure("boot-test", "6", "Flughafen", null,
            Instant.ofEpochMilli(System.currentTimeMillis() + 7 * 60_000).toString(), null, 0, false, false)
        try {
            preferences.edit().remove("alarms").commit()
            assertTrue(DepartureAlarm.schedule(context, departure, Stop("boot-stop", "Test"), 5))
            AlarmRestoreReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
            assertTrue(DepartureAlarm.isSet(context, departure.id))
            assertEquals(1, DepartureAlarm.restore(context))
        } finally {
            DepartureAlarm.cancel(context, departure.id)
            val edit = preferences.edit()
            if (previous == null) edit.remove("alarms") else edit.putString("alarms", previous)
            edit.commit()
        }
    }

    @Test fun liveServiceFetchesAgainWithoutAppOrManualRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // A visible launcher-like host, not MainActivity: no application refresh loop is running.
        val host = instrumentation.startActivitySync(Intent(context, WidgetTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        LiveUpdateService.setEnabled(context, true)
        try {
            instrumentation.runOnMainSync { LiveUpdateService.start(host) }
            val repository = TransitDisplay.repository(context)
            Thread.sleep(20_000)
            val first = repository.load()
            assertNotNull("Initial service fetch missing", first)
            Thread.sleep(70_000)
            val second = repository.load()
            assertNotNull(second)
            assertFalse("Service returned stale data", second!!.stale)
            assertTrue("No automatic fetch after one minute", Instant.parse(second.generatedAt).isAfter(Instant.parse(first!!.generatedAt)))
        } finally {
            context.stopService(Intent(context, LiveUpdateService::class.java))
            instrumentation.runOnMainSync { host.finish() }
        }
    }
}
