package com.nuvio.app.features.details

import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import io.ktor.http.encodeURLParameter

internal const val RATING_PROVIDER_LINK_CATEGORY = "rating-provider"

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
private val imdbDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?imdb\.com/title/tt\d+/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val tmdbDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?themoviedb\.org/(?:movie|tv)/\d+(?:-[^/?#]+)?/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val traktDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?trakt\.tv/(?:movies|shows)/[^/?#]+/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val rottenTomatoesDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?rottentomatoes\.com/(?:m|tv)/[^?#]+/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val metacriticDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?metacritic\.com/(?:movie|tv)/[^?#]+/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val letterboxdDirectUrlRegex = Regex(
    pattern = """^https?://(?:www\.)?letterboxd\.com/(?:film/[^/?#]+|imdb/tt\d+)/?(?:[?#].*)?$""",
    option = RegexOption.IGNORE_CASE,
)
private val knownRatingProviders = setOf(
    PROVIDER_IMDB,
    PROVIDER_TMDB,
    PROVIDER_TOMATOES,
    PROVIDER_METACRITIC,
    PROVIDER_TRAKT,
    PROVIDER_LETTERBOXD,
    PROVIDER_AUDIENCE,
)

internal fun buildRatingProviderUrl(meta: MetaDetails, source: String): String? {
    val normalizedSource = source.trim().lowercase()
    if (normalizedSource !in knownRatingProviders) return null

    meta.findDirectRatingProviderUrl(normalizedSource)?.let { return it }

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

internal fun normalizeRatingProviderUrl(rawUrl: String): String? {
    val trimmed = rawUrl
        .trim()
        .replace("&amp;", "&")
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.drop("http://".length)
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> null
    }
}

internal fun ratingProvidersForDirectUrl(rawUrl: String): List<String> {
    val url = normalizeRatingProviderUrl(rawUrl) ?: return emptyList()
    return when {
        imdbDirectUrlRegex.matches(url) -> listOf(PROVIDER_IMDB)
        tmdbDirectUrlRegex.matches(url) -> listOf(PROVIDER_TMDB)
        traktDirectUrlRegex.matches(url) -> listOf(PROVIDER_TRAKT)
        rottenTomatoesDirectUrlRegex.matches(url) -> listOf(PROVIDER_TOMATOES, PROVIDER_AUDIENCE)
        metacriticDirectUrlRegex.matches(url) -> listOf(PROVIDER_METACRITIC)
        letterboxdDirectUrlRegex.matches(url) -> listOf(PROVIDER_LETTERBOXD)
        else -> emptyList()
    }
}

internal fun isDirectRatingProviderUrlForSource(rawUrl: String, source: String): Boolean {
    val normalizedSource = source.trim().lowercase()
    return normalizedSource in ratingProvidersForDirectUrl(rawUrl)
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

private fun MetaDetails.findDirectRatingProviderUrl(source: String): String? {
    val generatedProviderNames = if (source == PROVIDER_AUDIENCE) {
        setOf(PROVIDER_AUDIENCE, PROVIDER_TOMATOES)
    } else {
        setOf(source)
    }

    links.asSequence()
        .filter { link ->
            link.category == RATING_PROVIDER_LINK_CATEGORY &&
                link.name.trim().lowercase() in generatedProviderNames
        }
        .mapNotNull { link -> normalizeRatingProviderUrl(link.url) }
        .firstOrNull { url -> isDirectRatingProviderUrlForSource(url, source) }
        ?.let { return it }

    return links.asSequence()
        .mapNotNull { link -> normalizeRatingProviderUrl(link.url) }
        .firstOrNull { url -> isDirectRatingProviderUrlForSource(url, source) }
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
