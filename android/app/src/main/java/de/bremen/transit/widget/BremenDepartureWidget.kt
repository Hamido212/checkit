package de.bremen.transit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import de.bremen.transit.MainActivity
import de.bremen.transit.R
import android.widget.RemoteViews
import android.os.SystemClock
import java.time.Instant
import de.bremen.transit.data.*

private val graphite = ColorProvider(Color(0xFF111317))
private val white = ColorProvider(Color(0xFFF5F6F7))
private val muted = ColorProvider(Color(0xFFA8ADB5))
private val amber = ColorProvider(Color(0xFFFFB51B))

class BremenDepartureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = TransitDisplay.repository(context)
        val snapshot = repository.load()?.takeIf { it.station.id == repository.selectedStop().id }
        provideContent { Board(snapshot, repository.selectedStop()) }
    }
}

@Composable private fun Board(snapshot: DepartureSnapshot?, stop: Stop) {
    val size = LocalSize.current
    // Allow for launcher padding, the frame, header and font metrics before allocating rows.
    val rows = ((size.height.value - 90) / 28).toInt().coerceIn(1, 6)
    val departures = snapshot?.departures?.filter { TransitDisplay.upcoming(it) }.orEmpty()
    Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF434A4C))).cornerRadius(16.dp).padding(1.dp)) {
    Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF252829))).cornerRadius(15.dp).padding(5.dp)) {
    Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF0D0F10))).cornerRadius(10.dp).padding(8.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(stop.name.replace("Bremen Hauptbahnhof", "Bremen Hbf"),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text("↻", modifier = GlanceModifier.width(32.dp).clickable(actionRunCallback<RefreshAction>()),
                style = TextStyle(color = amber, fontSize = 20.sp, textAlign = TextAlign.End))
        }
        Text(TransitDisplay.status(snapshot), style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
        if (departures.isEmpty()) Text("Keine aktuellen Abfahrten. ↻", modifier = GlanceModifier.padding(top = 8.dp), style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 2)
        departures.take(rows).forEach { d ->
            Row(GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                val badge = runCatching { ColorProvider(Color(android.graphics.Color.parseColor(d.color ?: "#FFB51B"))) }.getOrDefault(amber)
                val ink = runCatching { ColorProvider(Color(android.graphics.Color.parseColor(d.textColor ?: "#111317"))) }.getOrDefault(graphite)
                Text(d.line, modifier = GlanceModifier.width(34.dp).background(badge).padding(3.dp), style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                Spacer(GlanceModifier.width(7.dp))
                Text(d.destination, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = white, fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
                if(d.cancelled) {
                    Text("AUS", modifier = GlanceModifier.width(56.dp), style = TextStyle(color = amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End), maxLines = 1)
                } else {
                    val context = LocalContext.current
                    val departureMillis = runCatching { Instant.parse(d.realtime ?: d.scheduled).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                    val remote = RemoteViews(context.packageName, R.layout.departure_countdown).apply {
                        setChronometerCountDown(R.id.countdown, true)
                        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + departureMillis - System.currentTimeMillis(), null, true)
                        setContentDescription(R.id.countdown, "Verbleibende Zeit bis ${TransitDisplay.time(d.realtime ?: d.scheduled)}")
                    }
                    AndroidRemoteViews(remote, modifier = GlanceModifier.width(56.dp).height(28.dp))
                }
            }
        }
    }
    }
    }
}
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) { RefreshWorker.refresh(context) }
}
class BremenDepartureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BremenDepartureWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); RefreshWorker.schedule(context); RefreshWorker.refresh(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); androidx.work.WorkManager.getInstance(context).cancelUniqueWork("checkit-periodic") }
}
