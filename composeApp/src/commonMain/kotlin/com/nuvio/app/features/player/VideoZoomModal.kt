package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_pan_and_zoom
import nuvio.composeapp.generated.resources.compose_player_reset
import nuvio.composeapp.generated.resources.compose_player_set_as_default
import nuvio.composeapp.generated.resources.compose_player_video_zoom
import nuvio.composeapp.generated.resources.compose_player_zoom_decrease
import nuvio.composeapp.generated.resources.compose_player_zoom_increase
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VideoZoomModal(
    visible: Boolean,
    state: PlayerVideoZoomState,
    onStateChanged: (PlayerVideoZoomState) -> Unit,
    onSetDefault: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val normalizedState = state.normalized()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                )
                .background(colorScheme.scrim.copy(alpha = 0.52f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(250)) { it / 3 } + fadeOut(tween(250)),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(0.86f)
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(colorScheme.surface)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f), RoundedCornerShape(28.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZoomStepButton(
                            icon = Icons.Rounded.Remove,
                            contentDescription = stringResource(Res.string.compose_player_zoom_decrease),
                            onClick = {
                                onStateChanged(
                                    normalizedState.copy(
                                        zoom = stepPlayerVideoZoom(normalizedState.zoom, -1),
                                    ),
                                )
                            },
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(
                            modifier = Modifier.widthIn(min = 128.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomIn,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(22.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.compose_player_video_zoom),
                                    color = colorScheme.onSurface,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = formatPlayerVideoZoomLabel(normalizedState.zoom),
                                color = colorScheme.onSurface,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Slider(
                            value = normalizedState.zoom,
                            onValueChange = { zoom ->
                                onStateChanged(normalizedState.copy(zoom = clampPlayerVideoZoom(zoom)))
                            },
                            valueRange = PlayerVideoZoomMin..PlayerVideoZoomMax,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                activeTrackColor = colorScheme.primary,
                                inactiveTrackColor = colorScheme.primary.copy(alpha = 0.16f),
                                thumbColor = colorScheme.primary,
                            ),
                        )
                        Spacer(Modifier.width(18.dp))
                        ZoomStepButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = stringResource(Res.string.compose_player_zoom_increase),
                            onClick = {
                                onStateChanged(
                                    normalizedState.copy(
                                        zoom = stepPlayerVideoZoom(normalizedState.zoom, 1),
                                    ),
                                )
                            },
                        )
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = normalizedState.panAndZoomEnabled,
                            onCheckedChange = { enabled ->
                                onStateChanged(normalizedState.copy(panAndZoomEnabled = enabled))
                            },
                        )
                        Text(
                            text = stringResource(Res.string.compose_player_pan_and_zoom),
                            color = colorScheme.onSurface,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSetDefault,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text(text = stringResource(Res.string.compose_player_set_as_default))
                        }
                        Button(
                            onClick = onReset,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(text = stringResource(Res.string.compose_player_reset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(colorScheme.primaryContainer),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}
