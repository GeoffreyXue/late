package family.late.apis

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GoogleCalendarService {

    private const val APPLICATION_NAME = "Late"
    private const val MAX_RESULTS = 5

    private fun getCalendarService(accessToken: String): Calendar {
        val transport = NetHttpTransport()
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()

        val credential = com.google.api.client.googleapis.auth.oauth2.GoogleCredential()
            .setAccessToken(accessToken)

        return Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    suspend fun listCalendars(accessToken: String): List<CalendarListEntry> {
        return withContext(Dispatchers.IO) {
            val service = getCalendarService(accessToken)
            service.calendarList().list().execute().items
        }
    }

    // This retrieves the next 250 events that end after the current time
    suspend fun getEvents(accessToken: String, calendarId: String): List<Event> {
        return withContext(Dispatchers.IO) {
            val service = getCalendarService(accessToken)
            val minDateTime = DateTime.parseRfc3339(instantToRFC3339(Instant.now()))
            service.events()
                .list(calendarId)
                .setSingleEvents(true)
                .setTimeMin(minDateTime)
                .setOrderBy("startTime")
                .setMaxResults(MAX_RESULTS)
                .execute().items
        }
    }

    private fun instantToRFC3339(instant: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        return formatter.format(instant.atOffset(ZoneOffset.UTC))
    }
}
