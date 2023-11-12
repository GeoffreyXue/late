package family.late.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.api.services.calendar.model.Event
import family.late.apis.GoogleCalendarService
import family.late.apis.GoogleGmailService.sendEmail
import family.late.ui.theme.LateWidgetGlanceColorScheme
import family.late.ui.theme.Red80
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class LateWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 100.dp)
        private val BIG_SQUARE = DpSize(250.dp, 250.dp)
    }


    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
            BIG_SQUARE
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val sharedPreferences: SharedPreferences = context.getSharedPreferences("late", Context.MODE_PRIVATE)
        val calendarId = sharedPreferences.getString("calendarId", null)
        val accessToken = sharedPreferences.getString("token", null)
        val email = sharedPreferences.getString("email", null)

        provideContent {
            GlanceTheme(colors = LateWidgetGlanceColorScheme.colors) {
                Content(email, calendarId, accessToken)
            }
        }
    }
}

@Composable
fun Content(sender: String?, calendarId: String?, accessToken: String?) {
    val coroutineScope = rememberCoroutineScope()

    var nextEvent: Event? by remember { mutableStateOf(null) }
    var text by remember { mutableStateOf("")}

    LaunchedEffect(Unit) {
        if (accessToken == null) return@LaunchedEffect
        if (calendarId == null) return@LaunchedEffect

        nextEvent = null

        val events = GoogleCalendarService.getEvents(accessToken, calendarId)

        if (events.isEmpty()) return@LaunchedEffect

        val currentTime = Instant.now()

        val test = events
            .filter { Instant.ofEpochMilli(it.start.dateTime.value).isAfter(currentTime) }
            .minByOrNull { it.start.dateTime.value }

        Log.d("TEST", test.toString())

        nextEvent = events
            .filter { Instant.ofEpochMilli(it.start.dateTime.value).isAfter(currentTime) }
            .minByOrNull { it.start.dateTime.value }
    }

    LaunchedEffect(sender, calendarId, accessToken, nextEvent) {
        if (sender == null || calendarId == null || accessToken == null) {
            text = "Calendar or token expired. Please reconfigure."

        }
        else if (nextEvent != null)
            text = "Meeting soon! Late ping?"
    }

    Box(
        modifier = GlanceModifier.background(ColorProvider(Red80))
            .clickable {
                coroutineScope.launch {
                    if (sender == null || accessToken == null || nextEvent == null || nextEvent?.attendees == null) return@launch
                    val subject = "Late to meeting - ${nextEvent!!.summary}"
                    val message = "Sorry, I will be a few minutes late to this meeting."
                    // List of recipients includes all but sender
                    val recipients = nextEvent!!.attendees.map { it.email }.filter { !it.equals(sender)}

                    text = if (sendEmail(accessToken, sender, subject, message, recipients))
                        "Successfully sent email!"
                    else
                        "Failed to send email."
                    delay(1.seconds)
                    text = "Late!"

                }
            }
            .fillMaxSize()
            .cornerRadius(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(MaterialTheme.colorScheme.onPrimary),
                fontSize = TextUnit(32F, TextUnitType.Sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}



class LateCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("TEST", "button clicked!")
    }
}
