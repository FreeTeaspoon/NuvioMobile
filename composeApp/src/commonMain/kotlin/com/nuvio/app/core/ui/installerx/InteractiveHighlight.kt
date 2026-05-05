// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2025-2026 InstallerX Revived contributors
//
// Portions of this file are derived from weishu/KernelSU
// (https://github.com/tiann/KernelSU)
// Copyright (C) KernelSU contributors
// Licensed under GPL-3.0
package com.nuvio.app.core.ui.installerx

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope

expect class InteractiveHighlight(
    animationScope: CoroutineScope,
    highlightColor: Color,
    position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    val modifier: Modifier
    val gestureModifier: Modifier
}

expect fun isInteractiveHighlightSupported(): Boolean
