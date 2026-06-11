package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.installerx.FloatingBottomBar
import com.nuvio.app.core.ui.installerx.FloatingBottomBarItem
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

val NuvioNavigationBarScrollClearance = 104.dp

@Composable
fun NuvioNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    itemCount: Int? = null,
    backdrop: Any? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    content: @Composable NuvioNavigationBarScope.() -> Unit,
) {
    val count = itemCount?.takeIf { it > 0 } ?: 1
    val selected = selectedIndex?.coerceIn(0, count - 1) ?: 0
    val isInLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = if (isInLightTheme) {
        Color.White.copy(alpha = 0.36f)
    } else {
        Color.Black.copy(alpha = 0.36f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(nuvioBottomNavigationBarInsets().asPaddingValues())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = nuvioBottomNavigationExtraVerticalPadding + 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FloatingBottomBar(
            modifier = Modifier.width(288.dp),
            selectedIndex = { selected },
            onSelected = { index -> onSelectedIndexChange?.invoke(index) },
            backdrop = backdrop,
            tabsCount = count,
            isBlurEnabled = true,
            isInLightTheme = isInLightTheme,
            accentColor = Color.White,
            containerColor = containerColor,
            indicatorRestColor = Color.White.copy(alpha = 0.10f),
            indicatorPressedOverlayColor = Color.Black.copy(alpha = 0.03f),
            pressedIndicatorScrimColor = Color.Black.copy(alpha = 0.28f),
            highlightColor = accentColor.copy(alpha = 0.18f),
        ) {
            NuvioNavigationBarScopeImpl(this).content()
        }
    }
}

interface NuvioNavigationBarScope {
    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    )
}

private class NuvioNavigationBarScopeImpl(
    private val rowScope: RowScope,
) : NuvioNavigationBarScope {

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
    ) {
        with(rowScope) {
            FloatingBottomBarItem(
                onClick = onClick,
                modifier = modifier,
            ) {
                NavIcon(
                    selected = selected,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = it,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
    ) {
        with(rowScope) {
            FloatingBottomBarItem(
                onClick = onClick,
                modifier = modifier,
            ) {
                NavIcon(
                    selected = selected,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = contentDescription,
                        tint = it,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit,
    ) {
        with(rowScope) {
            FloatingBottomBarItem(
                onClick = onClick,
                modifier = modifier,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .alpha(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NavIcon(
    selected: Boolean,
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val baseAlpha = 1f
    val color = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .alpha(baseAlpha),
        contentAlignment = Alignment.Center,
    ) {
        icon(color)
    }
}
