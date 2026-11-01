package com.example.pdftoolkit.ui.screens.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle

/**
 * Tool screen ke upar overlay: progress dialog, success (Open / Share / Done) aur error dialog.
 * Har tool ke nav composable mein screen ke saath Box mein rakho.
 */
@Composable
fun ToolStateHost(
    state: ToolState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (OutputRecord) -> Unit,
    onShare: (OutputRecord) -> Unit,
    onDone: () -> Unit
) {
    when (state) {
        ToolState.Idle -> Unit

        is ToolState.Working -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = AppColors.Surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.progress == null) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.Primary,
                            trackColor = AppColors.SurfaceSoft
                        )
                    }
                    Text(
                        text = state.message,
                        style = AppTextStyle.Body14Medium,
                        color = AppColors.TextPrimary
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.tool_cancel), color = AppColors.TextSecondary)
                    }
                }
            }
        }

        is ToolState.Success -> {
            val context = LocalContext.current
            val size = Formatter.formatShortFileSize(context, state.record.sizeBytes)
            AlertDialog(
                onDismissRequest = onDone,
                containerColor = AppColors.Surface,
                title = {
                    Text(
                        text = stringResource(R.string.tool_done_title),
                        style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.tool_saved_as, state.record.name, size),
                            style = AppTextStyle.Body14Regular,
                            color = AppColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onShare(state.record) }) {
                                Text(stringResource(R.string.tool_share), color = AppColors.Primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onOpen(state.record) }) {
                        Text(stringResource(R.string.tool_open), color = AppColors.Primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.tool_close), color = AppColors.TextSecondary)
                    }
                }
            )
        }

        is ToolState.Failure -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = AppColors.Surface,
            title = {
                Text(
                    text = stringResource(R.string.tool_failed_title),
                    style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Error
                )
            },
            text = {
                Text(
                    text = state.message,
                    style = AppTextStyle.Body14Regular,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tool_ok), color = AppColors.Primary)
                }
            }
        )
    }
}