package family.late

import androidx.compose.runtime.compositionLocalOf

data class GoogleAPI(val accessToken: String? = null)

val LocalGoogleAPI = compositionLocalOf { GoogleAPI() }