package com.example.pdftoolkit.ui.screens.extracttextscreen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.OutputActions
import com.example.pdftoolkit.ui.screens.components.ToolStateHost
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle

@Composable
fun ExtractTextScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ExtractTextViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val source by viewModel.source.collectAsState()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.setSource(uri)
        } else if (source == null) {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        if (source == null) picker.launch(arrayOf("application/pdf"))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = AppColors.TextPrimary)
                }
                Text(
                    text = stringResource(R.string.tool_extract_text),
                    style = AppTextStyle.Title18Medium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            source?.let { src ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = src.displayName, style = AppTextStyle.Body14SemiBold, color = AppColors.TextPrimary)
                        Text(text = "${src.pageCount} pages", style = AppTextStyle.Label12Regular, color = AppColors.TextSecondary)
                    }
                }

                Button(
                    onClick = { viewModel.extract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Icon(Icons.Outlined.TextFields, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Extract Text to TXT", style = AppTextStyle.Button14SemiBold, color = Color.White)
                }
            }
        }

        ToolStateHost(
            state = state,
            onCancel = viewModel::cancel,
            onDismiss = viewModel::dismissState,
            onOpen = { OutputActions.open(context, it) },
            onShare = { OutputActions.share(context, it) },
            onDone = {
                viewModel.dismissState()
                onBack()
            }
        )
    }
}
