package com.nuvio.app.features.streams

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.features.details.formatRuntimeFromMinutes
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.rating_imdb
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun EpisodeMetadataRow(
    episodeMeta: StreamEpisodeMeta?,
    onOpenImdbUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val releaseDate = episodeMeta
        ?.released
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatReleaseDateForDisplay)
    val rating = episodeMeta?.rating?.takeIf { it.isNotBlank() }
    val runtime = episodeMeta
        ?.runtimeMinutes
        ?.takeIf { it > 0 }
        ?.let(::formatRuntimeFromMinutes)
        ?.takeIf { it.isNotBlank() }

    if (releaseDate == null && rating == null && runtime == null) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (releaseDate != null) {
            Text(
                text = releaseDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }

        if (rating != null) {
            val imdbUrl = remember(episodeMeta.imdbId) {
                buildEpisodeImdbUrl(episodeMeta.imdbId)
            }
            val ratingModifier = if (imdbUrl != null) {
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open IMDb",
                    ) {
                        onOpenImdbUrl(imdbUrl)
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            } else {
                Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            }

            Row(
                modifier = ratingModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(Res.drawable.rating_imdb),
                    contentDescription = "IMDb",
                    modifier = Modifier
                        .width(30.dp)
                        .height(16.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = rating,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF5C518),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (runtime != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = runtime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
