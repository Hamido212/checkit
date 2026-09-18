package de.bremen.transit
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.bremen.transit.data.TransitDisplay
import de.bremen.transit.widget.BremenDepartureWidget
import de.bremen.transit.widget.BremenDepartureWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    @Test fun liveWidgetThreeSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = TransitDisplay.repository(context)
        val live = repository.refresh(repository.selectedStop())
        assertFalse("Live backend returned no departures", live.departures.isEmpty())
        val activity = instrumentation.startActivitySync(Intent(context, WidgetTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WidgetTestActivity
        instrumentation.runOnMainSync {
            val preview = android.view.LayoutInflater.from(context).inflate(R.layout.widget_picker_preview, null)
            val density = context.resources.displayMetrics.density
            val w = (300*density).toInt();val h = (180*density).toInt()
            preview.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            preview.layout(0,0,w,h)
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            preview.draw(android.graphics.Canvas(bitmap))
            File(context.getExternalFilesDir(null),"widget-picker.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 8127)
        val id = host.allocateAppWidgetId()
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, BremenDepartureWidgetReceiver::class.java)))
            instrumentation.runOnMainSync { host.startListening() }
            for ((width,height) in listOf(180 to 110,250 to 150,300 to 240)) {
                lateinit var view: android.appwidget.AppWidgetHostView
                instrumentation.runOnMainSync {
                    activity.root.removeAllViews()
                    view = host.createView(context,id,manager.getAppWidgetInfo(id))
                    val density = context.resources.displayMetrics.density
                    activity.root.addView(view,FrameLayout.LayoutParams((width*density).toInt(),(height*density).toInt()).apply { topMargin=(40*density).toInt();leftMargin=(15*density).toInt() })
                    manager.updateAppWidgetOptions(id,Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(width.toFloat(),height.toFloat())))
                    })
                }
                runBlocking { BremenDepartureWidget().updateAll(context) }
                Thread.sleep(2500)
                var texts = emptyList<TextView>()
                for(attempt in 0..25) {
                    Thread.sleep(200)
                    instrumentation.runOnMainSync { texts=collect(view) }
                    if(texts.any { it is android.widget.Chronometer }) break
                }
                instrumentation.runOnMainSync {
                    val times=texts.filterIsInstance<android.widget.Chronometer>()
                    assertTrue("No times at ${width}x${height}: ${texts.map{it.text}}", times.isNotEmpty())
                    assertTrue("Status missing", texts.any { it.text.contains("Stand") })
                    val location=IntArray(2);view.getLocationOnScreen(location)
                    for(t in times) {
                        val pos=IntArray(2);t.getLocationOnScreen(pos)
                        val bottomInset = (22 * context.resources.displayMetrics.density).toInt()
                        assertTrue("Time clipped by inner frame",pos[1]+t.height<=location[1]+view.height-bottomInset)
                        assertTrue("Time clipped horizontally",pos[0]+t.width<=location[0]+view.width)
                        assertTrue("Countdown must run backwards", t.isCountDown)
                        for(destination in texts.filter { label -> live.departures.any { it.destination == label.text.toString() } }) {
                            val target = IntArray(2);destination.getLocationOnScreen(target)
                            if(target[1] < pos[1]+t.height && target[1]+destination.height > pos[1])
                                assertTrue("Destination overlaps countdown", target[0]+destination.width<=pos[0])
                        }
                    }
                    val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    File(context.getExternalFilesDir(null),"widget-${width}x${height}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    android.util.Log.i("CheckitTest","${width}x${height}: ${times.size} aligned departure rows")
                }
                var before = ""
                instrumentation.runOnMainSync { before = collect(view).filterIsInstance<android.widget.Chronometer>().first().text.toString() }
                Thread.sleep(1200)
                instrumentation.runOnMainSync { assertNotEquals("Countdown froze between network updates", before, collect(view).filterIsInstance<android.widget.Chronometer>().first().text.toString()) }
            }
        } finally { instrumentation.runOnMainSync { host.stopListening();activity.finish() };host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun collect(view: View): List<TextView> = when(view) { is TextView -> listOf(view);is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) };else -> emptyList() }
}
