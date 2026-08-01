package com.nuvio.app.features.mdblist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpPostJson
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.RATING_PROVIDER_LINK_CATEGORY
import com.nuvio.app.features.details.normalizeRatingProviderUrl
import com.nuvio.app.features.details.ratingProvidersForDirectUrl
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MdbListMetadataService {
    const val PROVIDER_IMDB = "imdb"
    const val PROVIDER_TMDB = "tmdb"
    const val PROVIDER_TOMATOES = "tomatoes"
    const val PROVIDER_METACRITIC = "metacritic"
    const val PROVIDER_TRAKT = "trakt"
    const val PROVIDER_LETTERBOXD = "letterboxd"
    const val PROVIDER_AUDIENCE = "audience"
    const val PROVIDER_MAL = "mal"

    val PROVIDER_PRIORITY_ORDER = listOf(
        PROVIDER_IMDB,
        PROVIDER_TMDB,
        PROVIDER_TOMATOES,
        PROVIDER_METACRITIC,
        PROVIDER_TRAKT,
        PROVIDER_LETTERBOXD,
        PROVIDER_AUDIENCE,
        PROVIDER_MAL,
    )

    private val log = Logger.withTag("MdbListMetadata")
    private val json = Json { ignoreUnknownKeys = true }
    private val ratingsCache = mutableMapOf<String, List<MetaExternalRating>>()
    private val providerLinksCache = mutableMapOf<String, Map<String, String>>()
    private val imdbRegex = Regex("tt\\d+")
    private val tmdbPrefixedIdRegex = Regex(
        pattern = """(?:^|:)(?:tmdb|movie|series|tv|show):(\d+)(?:$|[:/?#-])""",
        option = RegexOption.IGNORE_CASE,
    )
    private val tmdbUrlRegex = Regex(
        pattern = """themoviedb\.org/(movie|tv)/(\d+)""",
        option = RegexOption.IGNORE_CASE,
    )
    private val anchorHrefRegex = Regex(
        pattern = """<a\b[^>]*\bhref\s*=\s*(['"])(.*?)\1""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    internal var ratingPayloadFetcher: suspend (url: String, body: String) -> String = ::httpPostJson
    internal var providerLinksHtmlFetcher: suspend (url: String) -> String = ::httpGetText
    internal var tmdbToImdbResolver: suspend (tmdbId: Int, mediaType: String) -> String? = TmdbService::tmdbToImdb

    private const val PROVIDER_RATING_TIMEOUT_MS = 2_500L

    fun shouldFetchForMeta(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: MdbListSettings,
    ): Boolean {
        if (!settings.enabled) return false
        if (settings.apiKey.trim().isBlank()) return false
        if (settings.enabledProvidersInPriorityOrder().isEmpty()) return false
        return true
    }

    suspend fun enrichMeta(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: MdbListSettings,
    ): MetaDetails {
        if (!shouldFetchForMeta(meta, fallbackItemId, settings)) {
            return meta.copy(externalRatings = emptyList())
        }
        val apiKey = settings.apiKey.trim()

        val imdbId = resolveImdbId(meta, fallbackItemId)
            ?: return meta.copy(externalRatings = emptyList())
        val mediaType = toMdbListMediaType(meta.type)
        val enabledProviders = settings.enabledProvidersInPriorityOrder()

        val (ratings, providerLinks) = coroutineScope {
            val ratingsDeferred = async {
                fetchRatings(
                    imdbId = imdbId,
                    mediaType = mediaType,
                    apiKey = apiKey,
                    providers = enabledProviders,
                )
            }
            val linksDeferred = async {
                fetchProviderLinks(
                    imdbId = imdbId,
                    mediaType = mediaType,
                )
            }
            ratingsDeferred.await() to linksDeferred.await()
        }

        return meta.copy(
            externalRatings = ratings,
            links = mergeRatingProviderLinks(meta.links, providerLinks),
        )
    }

    fun clearCache() {
        ratingsCache.clear()
        providerLinksCache.clear()
    }

    private suspend fun fetchRatings(
        imdbId: String,
        mediaType: String,
        apiKey: String,
        providers: List<String>,
    ): List<MetaExternalRating> = withContext(Dispatchers.Default) {
        val cacheKey = "$mediaType:$imdbId:$apiKey:${providers.joinToString(",")}"
        ratingsCache[cacheKey]?.let { return@withContext it }

        val ratings = coroutineScope {
            providers.map { providerId ->
                async {
                    withTimeoutOrNull(PROVIDER_RATING_TIMEOUT_MS) {
                        fetchProviderRating(
                            imdbId = imdbId,
                            mediaType = mediaType,
                            providerId = providerId,
                            apiKey = apiKey,
                        )
                    }.also { rating ->
                        if (rating == null) {
                            log.w { "MDBList request returned no rating for $providerId/$imdbId" }
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        ratingsCache[cacheKey] = ratings
        ratings
    }

    private suspend fun fetchProviderLinks(
        imdbId: String,
        mediaType: String,
    ): Map<String, String> = withContext(Dispatchers.Default) {
        val cacheKey = "$mediaType:$imdbId"
        providerLinksCache[cacheKey]?.let { return@withContext it }

        val links = runCatching {
            val pageUrl = "https://www.mdblist.com/$mediaType/$imdbId"
            extractRatingProviderLinksFromHtml(providerLinksHtmlFetcher(pageUrl))
        }.onFailure { error ->
            if (error is CancellationException) throw error
            log.w { "MDBList provider link request failed for $mediaType/$imdbId: ${error.message}" }
        }.getOrDefault(emptyMap())

        providerLinksCache[cacheKey] = links
        links
    }

    internal fun extractRatingProviderLinksFromHtml(html: String): Map<String, String> {
        val links = linkedMapOf<String, String>()
        anchorHrefRegex.findAll(html).forEach { match ->
            val url = normalizeRatingProviderUrl(match.groupValues.getOrNull(2).orEmpty())
                ?: return@forEach
            ratingProvidersForDirectUrl(url).forEach { provider ->
                links.putIfAbsent(provider, url)
            }
        }
        return links
    }

    internal fun mergeRatingProviderLinks(
        existingLinks: List<MetaLink>,
        providerLinks: Map<String, String>,
    ): List<MetaLink> {
        if (providerLinks.isEmpty()) {
            return existingLinks
        }

        val generatedLinks = providerLinks.map { (provider, url) ->
            MetaLink(
                name = provider,
                category = RATING_PROVIDER_LINK_CATEGORY,
                url = url,
            )
        }

        val replacementProviders = providerLinks.keys
        return existingLinks
            .filterNot { link ->
                link.category == RATING_PROVIDER_LINK_CATEGORY &&
                    link.name.trim().lowercase() in replacementProviders
            }
            .plus(generatedLinks)
    }

    private suspend fun fetchProviderRating(
        imdbId: String,
        mediaType: String,
        providerId: String,
        apiKey: String,
    ): MetaExternalRating? {
        val url = "https://api.mdblist.com/rating/$mediaType/$providerId?apikey=$apiKey"
        val requestBody = json.encodeToString(
            RatingRequest(
                ids = listOf(imdbId),
                provider = PROVIDER_IMDB,
            ),
        )

        return runCatching {
            val payload = ratingPayloadFetcher(url, requestBody)
            val parsed = json.decodeFromString<RatingResponse>(payload)
            val rating = parsed.ratings.firstOrNull()?.rating ?: return@runCatching null
            MetaExternalRating(source = providerId, value = rating)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            log.w { "MDBList request failed for $providerId/$imdbId: ${error.message}" }
        }.getOrNull()
    }

    private fun extractImdbId(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return imdbRegex.find(value)?.value
    }

    private suspend fun resolveImdbId(meta: MetaDetails, fallbackItemId: String): String? {
        sequenceOf(meta.id, fallbackItemId)
            .plus(meta.links.asSequence().map(MetaLink::url))
            .mapNotNull(::extractImdbId)
            .firstOrNull()
            ?.let { return it }

        val metaType = normalizeTmdbMediaType(meta.type)
        val candidates = sequenceOf(
            extractTmdbIdCandidate(meta.id, metaType),
            extractTmdbIdCandidate(fallbackItemId, metaType),
        )
            .filterNotNull()
            .plus(meta.links.asSequence().mapNotNull { link -> extractTmdbIdCandidate(link.url, metaType) })
            .distinct()
            .toList()

        for (candidate in candidates) {
            val imdbId = runCatching {
                tmdbToImdbResolver(candidate.tmdbId, candidate.mediaType)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                log.w { "TMDB to IMDb lookup failed for ${candidate.mediaType}/${candidate.tmdbId}: ${error.message}" }
            }.getOrNull()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::extractImdbId)

            if (imdbId != null) {
                return imdbId
            }
        }

        return null
    }

    private fun extractTmdbIdCandidate(value: String?, fallbackMediaType: String): TmdbIdCandidate? {
        if (value.isNullOrBlank()) return null
        tmdbUrlRegex.find(value)?.let { match ->
            val mediaType = if (match.groupValues.getOrNull(1).equals("tv", ignoreCase = true)) {
                "tv"
            } else {
                "movie"
            }
            val tmdbId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
            return TmdbIdCandidate(tmdbId = tmdbId, mediaType = mediaType)
        }

        val trimmed = value.trim()
        val prefixedId = tmdbPrefixedIdRegex.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (prefixedId != null) {
            return TmdbIdCandidate(tmdbId = prefixedId, mediaType = fallbackMediaType)
        }

        val numericId = trimmed.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
        return numericId?.let { TmdbIdCandidate(tmdbId = it, mediaType = fallbackMediaType) }
    }

    private fun normalizeTmdbMediaType(metaType: String): String {
        val normalized = metaType.trim().lowercase()
        return if (normalized == "movie" || normalized == "film") "movie" else "tv"
    }

    private fun toMdbListMediaType(metaType: String): String {
        val normalized = metaType.trim().lowercase()
        return if (normalized == "movie") "movie" else "show"
    }
}

private data class TmdbIdCandidate(
    val tmdbId: Int,
    val mediaType: String,
)

@Serializable
private data class RatingRequest(
    val ids: List<String>,
    val provider: String,
)

@Serializable
private data class RatingResponse(
    val ratings: List<RatingItem> = emptyList(),
)

@Serializable
private data class RatingItem(
    val rating: Double? = null,
)
