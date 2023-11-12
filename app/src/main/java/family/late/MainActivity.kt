package family.late

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import family.late.apis.GoogleCalendarService
import family.late.ui.theme.LateTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

const val TAG = "LateTag"
const val NO_GOOGLE_ACCOUNTS = "No Google accounts found. Please set up a Google account on your device."
const val FAILED_LOG_IN = "Failed to log in. Please try again."
val REQUESTED_SCOPES: List<Scope> = listOf(
    Scope("https://www.googleapis.com/auth/gmail.send"), // Send emails to notify those on the event
    Scope("https://www.googleapis.com/auth/calendar"),   // View all calendars to find all relevant events
    Scope("https://www.googleapis.com/auth/calendar.events.readonly")   // View the events and those in the events
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContainerScreen()
                }
            }
        }
    }
}

@Composable
fun ContainerScreen() {
    val activity = LocalContext.current as Activity
    val webClientId = stringResource(R.string.web_client_id)

    val (displayText, setDisplayText) = remember { mutableStateOf("Signing in with Google...") }
    val (authorized, setAuthorized) = remember { mutableStateOf(false) }

    val (name, setName) = remember { mutableStateOf<String?>(null) }
    val (email, setEmail) = remember { mutableStateOf<String?>(null) }
    val (profileUri, setProfileUri) = remember { mutableStateOf<Uri?>(null) }

    val (googleAPI, setGoogleAPI) = remember { mutableStateOf<GoogleAPI?>(null) }

    LaunchedEffect(email) {
        if (email == null) return@LaunchedEffect
        val sharedPreferences: SharedPreferences = activity.getSharedPreferences("late", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("email", email)
        editor.apply()
    }

    LaunchedEffect(googleAPI) {
        if (googleAPI == null) return@LaunchedEffect
        val sharedPreferences: SharedPreferences = activity.getSharedPreferences("late", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("token", googleAPI.accessToken)
        editor.apply()
    }

    val authorizeScopesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        try {
            val authResult = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(
                    result.data
                )
            authResult.accessToken?.let {
                setGoogleAPI(GoogleAPI(authResult.accessToken))
                setAuthorized(true)
                Log.d(TAG, "AUTHORIZATION SUCCESSFUL")
            } ?: run {
                Log.e(TAG, "Authorization failed")
            }
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            e.localizedMessage?.let { Log.e(TAG, it) }
        }
    }

    val signOnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        try {
            val credential = Identity.getSignInClient(activity)
                .getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            if (idToken != null) {

                setName(credential.displayName)
                setEmail(credential.id)
                setProfileUri(credential.profilePictureUri)
                Log.d(TAG, "LOGGED IN")

                val authorizationRequest = AuthorizationRequest.Builder()
                    .setRequestedScopes(REQUESTED_SCOPES)
                    .build()

                Identity.getAuthorizationClient(activity)
                    .authorize(authorizationRequest)
                    .addOnSuccessListener { authorizationResult ->
                        if (authorizationResult.hasResolution()) {
                            try {
                                authorizeScopesLauncher.launch(
                                    IntentSenderRequest.Builder(authorizationResult.pendingIntent!!).build()
                                )
                            } catch (e: SendIntentException) {
                                Log.e(TAG, "Couldn't start Authorization UI: ${e.localizedMessage}")
                            }
                        } else {
                            setGoogleAPI(GoogleAPI(authorizationResult.accessToken))
                            setAuthorized(true)
                            Log.d(TAG, "AUTHORIZED ALREADY")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to authorize", e)
                    }
            }
            else {
                Log.e(TAG, "No ID token!")
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "One-tap dialog was closed.")
                    setDisplayText(FAILED_LOG_IN)
                }

                CommonStatusCodes.NETWORK_ERROR -> Log.d(
                    TAG,
                    "One-tap encountered a network error."
                )

                else -> Log.d(
                    TAG, "Couldn't get credential from result. " + e.localizedMessage
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(webClientId)
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(true)
                    .build())
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()

        val signUpRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(webClientId)
                    // Show all accounts on the device.
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            .build()

        // Attempt sign in
        Identity.getSignInClient(activity)
            .beginSignIn(signInRequest)
            .addOnSuccessListener(activity) { result ->
                try {
                    signOnLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
                } catch (e: SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(activity) {
                Log.d(TAG, "Failed to find existing, authorized account. Signing up")
                Identity.getSignInClient(activity).beginSignIn(signUpRequest)
                    .addOnSuccessListener(activity) { result ->
                        try {
                            signOnLauncher.launch(
                                IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
                        } catch (e: SendIntentException) {
                            Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                        }
                    }
                    .addOnFailureListener(activity) { e ->
                        // No Google Accounts found
                        e.localizedMessage?.let { it1 -> Log.e(TAG, it1) }
                        setDisplayText(NO_GOOGLE_ACCOUNTS)
                    }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (authorized && googleAPI != null) {
            CompositionLocalProvider(LocalGoogleAPI provides googleAPI) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Container()

                        ProfileCard(name ?: "Unknown", email ?: "Unknown", profileUri)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall
                )
            }
        }
    }
}


@OptIn(ExperimentalCoilApi::class)
@Composable
fun ProfileCard(name: String, email: String, photoUrl: Uri?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Display user photo on the top left
        if (photoUrl != null) {
            Image(
                painter = rememberImagePainter(data = photoUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Display user name and email to the right
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            // Display user name
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Display user email
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun Container(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Late",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "Let the next event know you're late",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        CalendarView()
    }
}

@Composable
fun CalendarView(modifier: Modifier = Modifier) {
    val accessToken = LocalGoogleAPI.current.accessToken
    val activity = LocalContext.current as Activity
    
    var calendarListEntries: List<CalendarListEntry> by remember { mutableStateOf(listOf()) }
    var selectedCalendar by remember { mutableStateOf(CalendarListEntry().setSummary("None")) }

    var nextEvent: Event? by remember { mutableStateOf(null)}

    LaunchedEffect(accessToken) {
        if (accessToken == null) return@LaunchedEffect
        calendarListEntries = GoogleCalendarService.listCalendars(accessToken)
    }

    LaunchedEffect(selectedCalendar) {
        if (accessToken == null) return@LaunchedEffect
        if (selectedCalendar.id == null) return@LaunchedEffect

        val sharedPreferences: SharedPreferences = activity.getSharedPreferences("late", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("calendarId", selectedCalendar.id)
        editor.apply()

        nextEvent = null

        val events = GoogleCalendarService.getEvents(accessToken, selectedCalendar.id)

        if (events.isEmpty()) return@LaunchedEffect

        val currentTime = Instant.now()

        nextEvent = events
            .filter { Instant.ofEpochMilli(it.start.dateTime.value).isAfter(currentTime) }
            .minByOrNull { it.start.dateTime.value }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Selected Calendar:",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        CalendarDropdown(calendarListEntries, selectedCalendar) { selectedCalendar = it}

        Spacer(modifier = Modifier.height(24.dp))

        NextEvent(nextEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDropdown(
    calendarListEntries: List<CalendarListEntry>,
    selectedCalendar: CalendarListEntry,
    onCalendarSelect: (CalendarListEntry) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(0.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = !expanded
            }
        ) {
            TextField(
                value = selectedCalendar.summary,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                calendarListEntries.forEach { calendar ->
                    DropdownMenuItem(
                        text = { Text(text = calendar.summary) },
                        onClick = {
                            expanded = !expanded
                            onCalendarSelect(calendar)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NextEvent(nextEvent: Event?,
              modifier: Modifier = Modifier) {

    val openCalendar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun formatDateTime(rfc3339String: String): String {
        try {
            // Parse the RFC3339 string to LocalDateTime
            val formatter = DateTimeFormatter.ISO_DATE_TIME
            val localDateTime = LocalDateTime.parse(rfc3339String, formatter)

            // Format the LocalDateTime to a pretty formatted date
            val prettyFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            return prettyFormatter.format(localDateTime)
        } catch (e: DateTimeParseException) {
            // Handle parsing exception
            e.printStackTrace()
            return "Invalid Date"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (nextEvent != null) {
            Text(
                text = "Next Event:",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (nextEvent.summary != null) {
                    Text(
                        text = nextEvent.summary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                Text(
                    modifier = modifier.padding(vertical=8.dp),
                    text = "Starts at " + formatDateTime(nextEvent.start.dateTime.toString()),
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (nextEvent.description != null) {
                    Text(
                        modifier = modifier.padding(vertical=8.dp),
                        text = nextEvent.description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }


                if (nextEvent.location != null) {
                    Text(
                        modifier = modifier.padding(vertical=8.dp),
                        text = nextEvent.location,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (nextEvent.htmlLink != null) {
                    Button(
                        onClick = {
                            // Create an intent to open the calendar link
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(nextEvent.htmlLink)
                            }

                            // Start the activity with the calendar intent
                            openCalendar.launch(intent)
                        },
                        modifier = Modifier
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Open Calendar",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun ContainerPreview() {
    Container()
}

@Composable
@Preview(showBackground = true)
fun CalendarListDropdownPreview() {
    CalendarDropdown(listOf(), CalendarListEntry().setSummary("None")) { }
}

@Composable
@Preview(showBackground = true)
fun MextEventPreview() {
    NextEvent(null)
}

@Composable
@Preview(showBackground = true)
fun ProfileCardPreview() {
    ProfileCard(
        name = "John Doe",
        email = "john.doe@example.com",
        photoUrl = Uri.parse("https://i.stack.imgur.com/34AD2.jpg")
    )
}