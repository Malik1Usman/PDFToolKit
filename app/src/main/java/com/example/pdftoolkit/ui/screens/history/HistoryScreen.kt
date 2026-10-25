package com.example.pdftoolkit.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme
import java.util.Locale

/* ----------------------------- Models ----------------------------- */

private enum class TaskStatus { COMPLETED, FAILED }

private enum class HistoryGroup(@StringRes val label: Int) {
    TODAY(R.string.history_group_today),
    YESTERDAY(R.string.history_group_yesterday),
    EARLIER(R.string.history_group_earlier)
}

private enum class HistoryFilter(@StringRes val label: Int, val dot: Color?) {
    ALL(R.string.history_filter_all, null),
    SUCCESSFUL(R.string.history_filter_successful, AppColors.Teal),
    FAILED(R.string.history_filter_failed, AppColors.Error),
    TODAY(R.string.history_filter_today, null),
    THIS_WEEK(R.string.history_filter_this_week, null)
}

private enum class RowAction(val icon: ImageVector, @StringRes val description: Int) {
    REPEAT(Icons.Outlined.Refresh, R.string.cd_repeat),
    SHARE(Icons.Outlined.Share, R.string.cd_share),
    MORE(Icons.Outlined.MoreVert, R.string.cd_more_options),
    DISMISS(Icons.Outlined.Close, R.string.cd_dismiss)
}

private data class TaskAccent(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val badgeBg: Color,
    val badgeFg: Color
)

private sealed interface TaskBody {
    data class Compression(
        val originalName: String,
        val originalSize: String,
        val outputName: String,
        val outputSize: String,
        val savedPercent: Int,
        val profile: String
    ) : TaskBody

    data class Output(val fileName: String, val meta: String, val icon: ImageVector) : TaskBody

    data class Error(val fileName: String, val message: String) : TaskBody
}

private data class TaskItem(
    val id: Int,
    val title: String,
    val time: String,
    val status: TaskStatus,
    val group: HistoryGroup,
    val accent: TaskAccent,
    val body: TaskBody,
    val actions: List<RowAction>
)

/* ----------------------------- Sample data ----------------------------- */

private val sampleTasks = listOf(
    TaskItem(
        id = 1,
        title = "Compressed PDF",
        time = "Today at 2:15 PM • 1.8s",
        status = TaskStatus.COMPLETED,
        group = HistoryGroup.TODAY,
        accent = TaskAccent(Icons.Outlined.Compress, AppColors.TealLight40, AppColors.Teal, AppColors.TealLight50, AppColors.TealOnLight),
        body = TaskBody.Compression("Marketing_Deck_Q3.pdf", "18.4 MB", "Marketing_Deck_Q3_compressed.pdf", "6.2 MB", 66, "High compression profile"),
        actions = listOf(RowAction.REPEAT, RowAction.SHARE)
    ),
    TaskItem(
        id = 2,
        title = "Merged 3 PDFs",
        time = "Today at 11:30 AM",
        status = TaskStatus.COMPLETED,
        group = HistoryGroup.TODAY,
        accent = TaskAccent(Icons.Outlined.MergeType, AppColors.IndigoSoft60, AppColors.Primary, AppColors.IndigoSoft, AppColors.OnIndigo),
        body = TaskBody.Output("Quarterly_Combined_Statements.pdf", "4.1 MB", Icons.Outlined.Description),
        actions = listOf(RowAction.SHARE, RowAction.MORE)
































    ),
    TaskItem(
        id = 3,
        title = "Images to PDF (8 photos)",
        time = "Yesterday at 4:50 PM",
        status = TaskStatus.COMPLETED,
        group = HistoryGroup.YESTERDAY,
        accent = TaskAccent(Icons.Outlined.PhotoLibrary, AppColors.Peach, AppColors.OnPeach, AppColors.TealLight50, AppColors.TealOnLight),
        body = TaskBody.Output("Site_Inspection_Photos.pdf", "5.4 MB", Icons.Outlined.Image),
        actions = listOf(RowAction.SHARE)
    ),
    TaskItem(
        id = 4,
        title = "Split PDF",
        time = "Nov 12 at 9:04 AM",
        status = TaskStatus.FAILED,
        group = HistoryGroup.EARLIER,
        accent = TaskAccent(Icons.Outlined.CallSplit, AppColors.ErrorSoft, AppColors.Error, AppColors.ErrorSoft, AppColors.OnErrorContainer),
        body = TaskBody.Error("Encrypted_Bank_Slip.pdf", "Password required or unsupported encryption"),
        actions = listOf(RowAction.DISMISS)
    )
)

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenFile: (String) -> Unit = {},
    onRepeatClick: (Int) -> Unit = {},
    onShareClick: (Int) -> Unit = {},
    onRetryWithPassword: (Int) -> Unit = {}
) {
    val tasks = remember { mutableStateListOf<TaskItem>().apply { addAll(sampleTasks) } }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val visible = when (filter) {
        HistoryFilter.ALL, HistoryFilter.THIS_WEEK -> tasks.toList()
        HistoryFilter.SUCCESSFUL -> tasks.filter { it.status == TaskStatus.COMPLETED }
        HistoryFilter.FAILED -> tasks.filter { it.status == TaskStatus.FAILED }
        HistoryFilter.TODAY -> tasks.filter { it.group == HistoryGroup.TODAY }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HistoryHeader(
                count = tasks.size,
                onClear = { tasks.clear() },
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
        }
        item {
            FilterChips(selected = filter, onSelected = { filter = it })
        }

        if (visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = AppTextStyle.Body14Regular,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 24.dp)
                )
            }
        }

        HistoryGroup.values().forEach { group ->
            val groupTasks = visible.filter { it.group == group }
            if (groupTasks.isNotEmpty()) {
                item(key = "header_${group.name}") {
                    GroupHeader(
                        label = stringResource(group.label),
                        modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp)
                    )
                }
                items(groupTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                        onOpenFile = onOpenFile,
                        onAction = { action ->
                            when (action) {
                                RowAction.REPEAT -> onRepeatClick(task.id)
                                RowAction.SHARE -> onShareClick(task.id)
                                RowAction.MORE -> { /* TODO: bottom sheet */ }
                                RowAction.DISMISS -> tasks.remove(task)
                            }
                        },
                        onRetry = { onRetryWithPassword(task.id) }
                    )
                }
            }
        }

        item {
            PrivacyFooter(modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp))
        }
    }
}

/* ----------------------------- Header ----------------------------- */

@Composable
private fun HistoryHeader(count: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.history_title),
                style = AppTextStyle.Title18Medium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.45).sp
                ),
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.history_subtitle, count),
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary
            )
        }

        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ClearAll, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.history_clear),
                style = AppTextStyle.Button14SemiBold,
                color = AppColors.Primary
            )
        }
    }
}

/* ----------------------------- Filter chips ----------------------------- */

@Composable
private fun FilterChips(selected: HistoryFilter, onSelected: (HistoryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(HistoryFilter.values().toList()) { filter ->
            val isSelected = filter == selected
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.Primary else AppColors.SurfaceSoft)
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(filter.label),
                    style = AppTextStyle.Label12Medium,
                    color = if (isSelected) Color.White else AppColors.TextSecondary,
                    maxLines = 1
                )
                if (filter.dot != null && !isSelected) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(filter.dot))
                }
            }
        }
    }
}

/* ----------------------------- Group header ----------------------------- */

@Composable
private fun GroupHeader(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = AppTextStyle.Caption11Medium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.55.sp
            ),
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColors.SurfaceSofter)
        )
    }
}

/* ----------------------------- Task card ----------------------------- */

@Composable
private fun TaskCard(
    task: TaskItem,
    onOpenFile: (String) -> Unit,
    onAction: (RowAction) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title row + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(task.accent.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(task.accent.icon, null, tint = task.accent.iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = AppTextStyle.Title16Medium,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.time,
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(task)
        }

        // Body
        when (val body = task.body) {
            is TaskBody.Compression -> CompressionBody(body)
            is TaskBody.Output -> OutputBody(body)
            is TaskBody.Error -> ErrorBody(body)
        }

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.status == TaskStatus.FAILED) {
                RetryButton(onClick = onRetry)
            } else {
                val fileName = when (val b = task.body) {
                    is TaskBody.Compression -> b.outputName
                    is TaskBody.Output -> b.fileName
                    is TaskBody.Error -> b.fileName
                }
                OpenFileButton(onClick = { onOpenFile(fileName) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                task.actions.forEach { action ->
                    IconButton(onClick = { onAction(action) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.description),
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(if (action == RowAction.MORE) 18.dp else 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(task: TaskItem) {
    val failed = task.status == TaskStatus.FAILED
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(task.accent.badgeBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = task.accent.badgeFg,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(if (failed) R.string.history_status_failed else R.string.history_status_completed),
            style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
            color = task.accent.badgeFg,
            maxLines = 1
        )
    }
}

/* ----------------------------- Card bodies ----------------------------- */

@Composable
private fun CompressionBody(body: TaskBody.Compression) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceTint)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = body.originalName,
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = body.originalSize,
                style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ArrowDownward, null, tint = AppColors.Teal, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = body.outputName,
                style = AppTextStyle.Label12Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = body.outputSize,
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.Teal,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.Teal)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_saved_percent, body.savedPercent),
                    style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = body.profile,
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun OutputBody(body: TaskBody.Output) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceTint)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceTrack),
            contentAlignment = Alignment.Center
        ) {
            Icon(body.icon, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = body.fileName,
                style = AppTextStyle.Label12Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body.meta,
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ErrorBody(body: TaskBody.Error) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.ErrorSoft20)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = AppColors.Error, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = body.fileName,
                style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = body.message,
            style = AppTextStyle.Label12Medium.copy(lineHeight = 19.5.sp),
            color = AppColors.Error
        )
    }
}

/* ----------------------------- Buttons ----------------------------- */

@Composable
private fun OpenFileButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Description, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.history_open_file),
            style = AppTextStyle.Label12Medium,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Key, null, tint = AppColors.Error, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.history_retry_password),
            style = AppTextStyle.Label12SemiBold,
            color = AppColors.Error
        )
    }
}

/* ----------------------------- Privacy footer ----------------------------- */

@Composable
private fun PrivacyFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(AppColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Shield, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.history_privacy_title),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.history_privacy_body),
                style = AppTextStyle.Label12Regular.copy(lineHeight = 19.5.sp),
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun HistoryScreenPreview() {
    PdfToolkitTheme { HistoryScreen(contentPadding = PaddingValues(0.dp)) }
}