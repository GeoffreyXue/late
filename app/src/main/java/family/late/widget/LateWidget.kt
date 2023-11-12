package family.late.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxHeight
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
import family.late.ui.theme.Green80
import family.late.ui.theme.LateWidgetGlanceColorScheme
import family.late.ui.theme.Red80
import family.late.ui.theme.Yellow80
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Timer
import kotlin.time.Duration.Companion.seconds


const val RECONFIGURE = "Calendar or token expired. Please reconfigure."

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
        provideContent {
            GlanceTheme(colors = LateWidgetGlanceColorScheme.colors) {
                ContentWrapper(context)
            }
        }
    }
}

@Composable
fun ContentWrapper(context: Context) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("late", Context.MODE_PRIVATE)
    var calendarId: String? by remember { mutableStateOf(sharedPreferences.getString("calendarId", null)) }
    var accessToken: String? by remember { mutableStateOf(sharedPreferences.getString("token", null)) }
    var email: String? by remember { mutableStateOf(sharedPreferences.getString("email", null)) }

    fun refresh() {
        calendarId = sharedPreferences.getString("calendarId", null)
        accessToken = sharedPreferences.getString("token", null)
        email = sharedPreferences.getString("email", null)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Content(email, calendarId, accessToken) { refresh() }
}

@SuppressLint("ResourceType")
@Composable
fun Content(sender: String?, calendarId: String?, accessToken: String?, refresh: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    var nextEvent: Event? by remember { mutableStateOf(null) }
    var text by remember { mutableStateOf("")}
    var backgroundColor by remember { mutableStateOf(Green80)}
    var sending by remember { mutableStateOf(false) }

    suspend fun fetchNextEvent() {
        if (accessToken == null) return
        if (calendarId == null) return

        nextEvent = null

        val events = GoogleCalendarService.getEvents(accessToken, calendarId)

        if (events.isEmpty()) return

        val currentTime = Instant.now()

        nextEvent = events
            .filter { Instant.ofEpochMilli(it.start.dateTime.value).isAfter(currentTime) }
            .minByOrNull { it.start.dateTime.value }
    }

    // Repeatedly refetch and update time every minute
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            fetchNextEvent()
            delay(60000)
        }
    }

    LaunchedEffect(sender, calendarId, accessToken, nextEvent) {
        if (sender == null || calendarId == null || accessToken == null) {
            text = RECONFIGURE
            backgroundColor = Yellow80
        }

        val event = nextEvent

        event?.let {
            text = "Next meeting: ${event.summary}"
            backgroundColor = Yellow80

            val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(event.start.dateTime.value))
            val thirtyMinutes = Duration.ofMinutes(30)

            if (duration <= thirtyMinutes) {
                text = "Meeting: ${event.summary} in ${duration.toMinutes()} minute(s)!"
                backgroundColor = Red80
            }
        }
    }

    Box(
        modifier = GlanceModifier.background(ColorProvider(backgroundColor))
            .fillMaxSize()
            .cornerRadius(20.dp),
        contentAlignment = Alignment.Center,
    ) {

        Column( modifier = GlanceModifier.fillMaxHeight() ) {
            Button(
                text = "Refresh",
                onClick = { refresh() },
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White)
                ),
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(textAlign = TextAlign.Right)
            )

            Box (
                modifier = GlanceModifier.clickable {
                    text = "Sending Email..."
                    sending = true
                    coroutineScope.launch {
                        if (sender == null || accessToken == null || nextEvent == null || nextEvent?.attendees == null) return@launch
                        val subject = "Running Late to meeting - ${nextEvent!!.summary}"
                        val message = "I hope this email finds you well. I wanted to inform you that I will be running a bit late for our upcoming meeting. I apologize for any inconvenience this may cause."
                        // List of recipients includes all but sender
                        val recipients = nextEvent!!.attendees
                            .filter { it.responseStatus != "declined"}
                            .map { it.email }
                            .filter { !it.equals(sender)}

                        if (sendEmail(accessToken, sender, subject, message, recipients)) {
                            text = "Successfully sent email!"
                            backgroundColor = Green80
                        }
                        else {
                            text = "Failed to send email."
                            backgroundColor = Red80
                        }
                        delay(2.seconds)
                        sending = false
                        fetchNextEvent()
                    }
                }
            ) {
                Column() {
                    if (text.isNotEmpty()) {
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

                        if (text != RECONFIGURE && !sending) {
                            Text(
                                text = "Send email!",
                                style = TextStyle(color = ColorProvider(MaterialTheme.colorScheme.onPrimary),
                                    fontSize = TextUnit(16F, TextUnitType.Sp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center),
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
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
