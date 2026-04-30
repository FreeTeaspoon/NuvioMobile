package com.nuvio.app.features.streams

import com.nuvio.app.features.details.MetaVideo
import kotlin.math.round

data class StreamEpisodeMeta(
    val released: String? = null,
    val runtimeMinutes: Int? = null,
    val rating: String? = null,
    val imdbId: String? = null,
)

private val episodeImdbIdRegex = Regex("^tt\\d+$", RegexOption.IGNORE_CASE)

internal fun buildEpisodeImdbUrl(imdbId: String?): String? {
    val normalized = sanitizeEpisodeImdbId(imdbId) ?: return null
    return "https://www.imdb.com/title/$normalized/"
}

internal fun sanitizeEpisodeImdbId(raw: String?): String? =
    raw
        ?.trim()
        ?.takeIf { episodeImdbIdRegex.matches(it) }
        ?.lowercase()

internal fun sanitizeEpisodeRating(raw: String?): String? {
    val rating = raw
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.toDoubleOrNull()
        ?: return null
    if (!rating.isFinite() || rating <= 0.0) return null
    return (round(rating * 10.0) / 10.0).toString()
}

internal fun MetaVideo.toStreamEpisodeMeta(): StreamEpisodeMeta =
    StreamEpisodeMeta(
        released = released,
        runtimeMinutes = runtime?.takeIf { it > 0 },
        rating = sanitizeEpisodeRating(rating),
        imdbId = sanitizeEpisodeImdbId(imdbId),
    )
