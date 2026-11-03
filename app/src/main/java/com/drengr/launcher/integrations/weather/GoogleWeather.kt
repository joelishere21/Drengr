package com.drengr.launcher.integrations.weather

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.drengr.launcher.core.domain.Place

/**
 * Opens Google's weather for the place Home shows. Google has no public weather intent; a
 * "weather <place>" search in the Google app opens its full-screen weather view (the Pixel
 * Weather app on Pixels). Falls back to the same search in the browser.
 */
object GoogleWeather {
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

    /**
     * The search to run: the city picked in Settings → Weather, else the name Home shows for
     * the device location, else plain "weather" (Google then uses the phone's location).
     */
    fun query(place: Place?, shownName: String?): String {
        val name = place?.name?.takeIf { it.isNotBlank() } ?: shownName?.takeIf { it.isNotBlank() }
        return if (name == null) "weather" else "weather $name"
    }

    fun open(context: Context, query: String): Boolean {
        val google = Intent(Intent.ACTION_WEB_SEARCH)
            .setPackage(GOOGLE_APP)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return listOf(google, browser).any { runCatching { context.startActivity(it) }.isSuccess }
    }
}
