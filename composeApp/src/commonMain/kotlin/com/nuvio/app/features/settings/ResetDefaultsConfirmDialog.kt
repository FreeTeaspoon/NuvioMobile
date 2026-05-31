package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.NuvioStatusModal
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_reset
import nuvio.composeapp.generated.resources.settings_reset_confirm_message
import nuvio.composeapp.generated.resources.settings_reset_confirm_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ResetDefaultsConfirmDialog(
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NuvioStatusModal(
        title = stringResource(Res.string.settings_reset_confirm_title),
        message = stringResource(Res.string.settings_reset_confirm_message),
        isVisible = isVisible,
        confirmText = stringResource(Res.string.action_reset),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
