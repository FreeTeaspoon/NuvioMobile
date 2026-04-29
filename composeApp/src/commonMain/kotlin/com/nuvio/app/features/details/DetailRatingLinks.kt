package com.nuvio.app.features.details

import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import io.ktor.http.encodeURLParameter

private val imdbIdRegex = Regex("tt\\d+", RegexOption.IGNORE_CASE)
private val releaseYearRegex = Regex("^\\D*(\\d{4})")
private val tmdbPrefixedIdRegex = Regex(
    pattern = """(?:^|:)(?:tmdb|movie|series|tv|show):(\d+)(?:$|[:/?#-])""",
    option = RegexOption.IGNORE_CASE,
)
private val tmdbUrlRegex = Regex(
    pattern = """themoviedb\.org/(?:movie|tv)/(\d+)""",
    option = RegexOption.IGNORE_CASE,
)

internal fun buildRatingProviderUrl(meta: MetaDetails, source: String): String? {
    val normalizedSource = source.trim().lowercase()
    val searchQuery = meta.ratingSearchQuery()
    val encodedQuery = searchQuery.encodeURLParameter()
    val encodedPath = encodedQuery

    return when (normalizedSource) {
        PROVIDER_IMDB -> meta.extractImdbId()
            ?.let { imdbId -> "https://www.imdb.com/title/$imdbId/" }
            ?: "https://www.imdb.com/find/?q=$encodedQuery"

        PROVIDER_TMDB -> meta.extractTmdbId()
            ?.let { tmdbId ->
                val pathType = if (meta.isMovieType()) "movie" else "tv"
                "https://www.themoviedb.org/$pathType/$tmdbId"
            }
            ?: "https://www.themoviedb.org/search?query=$encodedQuery"

        PROVIDER_TOMATOES,
        PROVIDER_AUDIENCE -> "https://www.rottentomatoes.com/search?search=$encodedQuery"

        PROVIDER_METACRITIC -> "https://www.metacritic.com/search/$encodedPath/"
        PROVIDER_TRAKT -> "https://trakt.tv/search?query=$encodedQuery"
        PROVIDER_LETTERBOXD -> "https://letterboxd.com/search/full-text/$encodedPath/"
        else -> null
    }
}

private fun MetaDetails.ratingSearchQuery(): String {
    val title = name.trim()
    val year = releaseInfo
        ?.let(releaseYearRegex::find)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
    return listOfNotNull(title.takeIf { it.isNotBlank() }, year).joinToString(" ")
}

private fun MetaDetails.extractImdbId(): String? =
    sequenceOf(id)
        .plus(links.asSequence().map(MetaLink::url))
        .mapNotNull { value -> imdbIdRegex.find(value)?.value?.lowercase() }
        .firstOrNull()

private fun MetaDetails.extractTmdbId(): String? {
    links.asSequence()
        .mapNotNull { link -> tmdbUrlRegex.find(link.url)?.groupValues?.getOrNull(1) }
        .firstOrNull()
        ?.let { return it }

    val normalizedId = id.trim()
    if (normalizedId.isNotBlank() && normalizedId.all(Char::isDigit)) {
        return normalizedId
    }

    return tmdbPrefixedIdRegex.find(id)?.groupValues?.getOrNull(1)
}

private fun MetaDetails.isMovieType(): Boolean =
    type.trim().lowercase() in setOf("movie", "film")
