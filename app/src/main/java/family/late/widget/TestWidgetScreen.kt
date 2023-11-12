package family.late.widget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.background
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.android.glance.appwidget.host.glance.GlanceAppWidgetHostPreview

@Preview
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun TestWidgetScreen() {
    // The size of the widget
    val displaySize = DpSize(200.dp, 200.dp)
    // Your GlanceAppWidget instance
    val instance = LateWidget()
    // Provide a state depending on the GlanceAppWidget state definition
//    val state = preferencesOf(CannoliWidget.countKey to 2)

    GlanceAppWidgetHostPreview(
        modifier = Modifier.fillMaxSize(),
        glanceAppWidget = instance,
        displaySize = displaySize,
    )
}