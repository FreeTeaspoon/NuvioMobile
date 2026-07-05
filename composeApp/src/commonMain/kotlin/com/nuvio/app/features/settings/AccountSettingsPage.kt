package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.backup.BackupFilePlatform
import com.nuvio.app.features.backup.BackupFileReadResult
import com.nuvio.app.features.backup.BackupFileResult
import com.nuvio.app.features.backup.AppRestartPlatform
import com.nuvio.app.features.backup.BackupImportMode
import com.nuvio.app.features.backup.BackupImportResult
import com.nuvio.app.features.backup.BackupImportSummary
import com.nuvio.app.features.backup.BackupRepository
import com.nuvio.app.features.backup.BackupExportResult
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_import
import nuvio.composeapp.generated.resources.action_ok
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.backup_confirm_passphrase
import nuvio.composeapp.generated.resources.backup_error_export_failed
import nuvio.composeapp.generated.resources.backup_error_import_failed
import nuvio.composeapp.generated.resources.backup_export
import nuvio.composeapp.generated.resources.backup_export_success
import nuvio.composeapp.generated.resources.backup_import
import nuvio.composeapp.generated.resources.backup_import_replace_confirm
import nuvio.composeapp.generated.resources.backup_import_success
import nuvio.composeapp.generated.resources.backup_import_summary_title
import nuvio.composeapp.generated.resources.backup_passphrase
import nuvio.composeapp.generated.resources.backup_passphrase_mismatch
import nuvio.composeapp.generated.resources.backup_sensitive_warning
import nuvio.composeapp.generated.resources.backup_title
import nuvio.composeapp.generated.resources.auth_account_deletion_failed
import nuvio.composeapp.generated.resources.settings_account_delete_account
import nuvio.composeapp.generated.resources.settings_account_delete_account_description
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_message
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_title
import nuvio.composeapp.generated.resources.settings_account_email
import nuvio.composeapp.generated.resources.settings_account_not_signed_in
import nuvio.composeapp.generated.resources.settings_account_sign_out
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_message
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_title
import nuvio.composeapp.generated.resources.settings_account_status
import nuvio.composeapp.generated.resources.settings_account_status_anonymous
import nuvio.composeapp.generated.resources.settings_account_status_signed_in
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
) {
    item {
        AccountSettingsBody(isTablet = isTablet)
    }
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && authState is AuthState.Authenticated

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_status),
                        value = if (state.isAnonymous) {
                            stringResource(Res.string.settings_account_status_anonymous)
                        } else {
                            stringResource(Res.string.settings_account_status_signed_in)
                        },
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    state.email?.takeUnless { state.isAnonymous }?.let { email ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_email),
                            value = email,
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.settings_account_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        NuvioPrimaryButton(
            text = stringResource(Res.string.settings_account_sign_out),
            onClick = { showSignOutConfirm = true },
        )

        BackupSettingsCard()

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showSignOutConfirm = false
            scope.launch { AuthRepository.signOut() }
        },
        onDismiss = { showSignOutConfirm = false },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class BackupPassphraseMode {
    Export,
    Import,
}

@Composable
private fun BackupSettingsCard() {
    val scope = rememberCoroutineScope()
    var passphraseMode by remember { mutableStateOf<BackupPassphraseMode?>(null) }
    var statusTitle by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showRestartPrompt by remember { mutableStateOf(false) }

    fun showStatus(title: String, message: String) {
        statusTitle = title
        statusMessage = message
    }

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.backup_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.backup_sensitive_warning),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NuvioPrimaryButton(
                text = stringResource(Res.string.backup_export),
                modifier = Modifier.weight(1f),
                onClick = { passphraseMode = BackupPassphraseMode.Export },
            )
            NuvioPrimaryButton(
                text = stringResource(Res.string.backup_import),
                modifier = Modifier.weight(1f),
                onClick = { passphraseMode = BackupPassphraseMode.Import },
            )
        }
    }

    val exportFailed = stringResource(Res.string.backup_error_export_failed)
    val importFailed = stringResource(Res.string.backup_error_import_failed)
    val exportSuccess = stringResource(Res.string.backup_export_success)
    val importSuccess = stringResource(Res.string.backup_import_success)
    val importTitle = stringResource(Res.string.backup_import_summary_title)
    val mismatchMessage = stringResource(Res.string.backup_passphrase_mismatch)

    passphraseMode?.let { mode ->
        BackupPassphraseDialog(
            mode = mode,
            onDismiss = { passphraseMode = null },
            onConfirm = { passphrase ->
                passphraseMode = null
                scope.launch {
                    when (mode) {
                        BackupPassphraseMode.Export -> {
                            when (val export = BackupRepository.exportEncrypted(passphrase)) {
                                is BackupExportResult.Error -> showStatus(exportFailed, export.message)
                                is BackupExportResult.Success -> {
                                    when (val fileResult = BackupFilePlatform.exportBackup(export.defaultFileName, export.bytes)) {
                                        BackupFileResult.Cancelled -> Unit
                                        is BackupFileResult.Error -> showStatus(exportFailed, fileResult.message)
                                        BackupFileResult.Success -> showStatus(exportSuccess, exportSuccess)
                                    }
                                }
                            }
                        }
                        BackupPassphraseMode.Import -> {
                            when (val read = BackupFilePlatform.importBackup()) {
                                BackupFileReadResult.Cancelled -> Unit
                                is BackupFileReadResult.Error -> showStatus(importFailed, read.message)
                                is BackupFileReadResult.Success -> {
                                    when (val preview = BackupRepository.previewImport(read.bytes, passphrase)) {
                                        is BackupImportResult.Error -> showStatus(importFailed, preview.message)
                                        is BackupImportResult.NeedsConfirmation -> {
                                            pendingImport = PendingImport(
                                                bytes = preview.bytes,
                                                passphrase = preview.passphrase,
                                                summary = preview.preview,
                                            )
                                        }
                                        is BackupImportResult.Success -> showStatus(importSuccess, importSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            mismatchMessage = mismatchMessage,
        )
    }

    pendingImport?.let { pending ->
        BackupImportSummaryDialog(
            summary = pending.summary,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    when (
                        val result = BackupRepository.importEncrypted(
                            bytes = pending.bytes,
                            passphrase = pending.passphrase,
                            mode = BackupImportMode.Replace,
                        )
                    ) {
                        is BackupImportResult.Error -> showStatus(importFailed, result.message)
                        is BackupImportResult.NeedsConfirmation -> Unit
                        is BackupImportResult.Success -> showRestartPrompt = true
                    }
                }
            },
        )
    }

    NuvioStatusModal(
        title = statusTitle.orEmpty(),
        message = statusMessage.orEmpty(),
        isVisible = statusTitle != null,
        confirmText = stringResource(Res.string.action_ok),
        onConfirm = {
            statusTitle = null
            statusMessage = null
        },
        onDismiss = {
            statusTitle = null
            statusMessage = null
        },
    )

    NuvioStatusModal(
        title = "Restart required",
        message = "Backup import completed. Restart the app now to reload the restored profile data.",
        isVisible = showRestartPrompt,
        confirmText = "Restart app",
        onConfirm = {
            showRestartPrompt = false
            AppRestartPlatform.restartApp()
        },
        onDismiss = {
            showRestartPrompt = false
            AppRestartPlatform.restartApp()
        },
    )
}

private data class PendingImport(
    val bytes: ByteArray,
    val passphrase: String,
    val summary: BackupImportSummary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPassphraseDialog(
    mode: BackupPassphraseMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    mismatchMessage: String,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(
                        if (mode == BackupPassphraseMode.Export) Res.string.backup_export else Res.string.backup_import,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.backup_sensitive_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                BackupPasswordField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    label = stringResource(Res.string.backup_passphrase),
                )
                if (mode == BackupPassphraseMode.Export) {
                    Spacer(modifier = Modifier.height(10.dp))
                    BackupPasswordField(
                        value = confirmPassphrase,
                        onValueChange = {
                            confirmPassphrase = it
                            errorMessage = null
                        },
                        label = stringResource(Res.string.backup_confirm_passphrase),
                    )
                }
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                DialogButtons(
                    confirmText = stringResource(
                        if (mode == BackupPassphraseMode.Export) Res.string.backup_export else Res.string.backup_import,
                    ),
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (mode == BackupPassphraseMode.Export && passphrase != confirmPassphrase) {
                            errorMessage = mismatchMessage
                            return@DialogButtons
                        }
                        onConfirm(passphrase)
                    },
                    confirmEnabled = passphrase.isNotBlank() &&
                        (mode == BackupPassphraseMode.Import || confirmPassphrase.isNotBlank()),
                )
            }
        }
    }
}

@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupImportSummaryDialog(
    summary: BackupImportSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(Res.string.backup_import_summary_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Current profile backup\nAddons: ${summary.addonCount}\nSaved items: ${summary.libraryItemCount}\nWatch progress entries: ${summary.watchProgressItemCount}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.backup_import_replace_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(18.dp))
                DialogButtons(
                    confirmText = stringResource(Res.string.action_import),
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                    confirmEnabled = true,
                )
            }
        }
    }
}

@Composable
private fun DialogButtons(
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(stringResource(Res.string.action_cancel))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(confirmText)
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
