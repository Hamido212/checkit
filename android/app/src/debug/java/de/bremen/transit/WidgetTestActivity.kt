package de.bremen.transit
import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
class WidgetTestActivity : Activity() {
    lateinit var root: FrameLayout
    override fun onCreate(state: Bundle?) { super.onCreate(state);root=FrameLayout(this);root.setBackgroundColor(android.graphics.Color.rgb(44,48,52));setContentView(root) }
}
