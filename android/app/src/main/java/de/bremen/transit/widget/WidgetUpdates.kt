package de.bremen.transit.widget

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import de.bremen.transit.data.TransitDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetUpdates {
    val revision = longPreferencesKey("render-time")
    private val lock = Mutex()
    private var lastAttempt = 0L
    private var lastStop = ""

    suspend fun refresh(context: Context, force: Boolean = false): Boolean = lock.withLock {
        val repository = TransitDisplay.repository(context)
        val stop = repository.selectedStop()
        var success = true
        if (force || stop.id != lastStop || SystemClock.elapsedRealtime() - lastAttempt >= 60_000) {
            lastAttempt = SystemClock.elapsedRealtime()
            lastStop = stop.id
            success = try { withContext(Dispatchers.IO) { repository.refresh(stop) }.stale.not() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { false }
        }
        render(context)
        success
    }

    suspend fun render(context: Context) {
        val widget = BremenDepartureWidget()
        GlanceAppWidgetManager(context).getGlanceIds(BremenDepartureWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { it[revision] = System.currentTimeMillis() }
            widget.update(context, id)
        }
    }
}
