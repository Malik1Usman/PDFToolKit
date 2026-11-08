package com.example.pdftoolkit.ui.screens.cropscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.scan.NPoint
import com.example.pdftoolkit.core.scan.Quad
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/**
 * "Adjust Edges": scanned photo ke upar 4 corner handles drag karke document ka area chuno.
 * Apply dabane par quad wapas milta hai (perspective crop ViewModel karta hai).
 */
@Composable
fun CropEditorScreen(
    contentPadding: PaddingValues,
    image: ImageBitmap?,
    initialQuad: Quad,
    modifier: Modifier = Modifier,
    onAutoDetect: suspend () -> Quad? = { null },
    onBack: () -> Unit = {},
    onApply: (Quad) -> Unit = {},
    onApplyAndEnhance: (Quad) -> Unit = {}
) {
    var quad by remember(initialQuad) { mutableStateOf(initialQuad) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var detecting by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Image ka asli displayed rectangle (ContentScale.Fit)
    val fit: Rect? = if (image == null || boxSize.width == 0 || boxSize.height == 0) null else {
        val s = min(boxSize.width / image.width.toFloat(), boxSize.height / image.height.toFloat())
        val dw = image.width * s
        val dh = image.height * s
        val left = (boxSize.width - dw) / 2f
        val top = (boxSize.height - dh) / 2f
        Rect(left, top, left + dw, top + dh)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.crop_title),
                    style = AppTextStyle.Title18Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(if (notFound) R.string.crop_not_found else R.string.crop_hint),
                    style = AppTextStyle.Label12Regular,
                    color = if (notFound) AppColors.Error else AppColors.TextSecondary
                )
            }
        }

        // Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.ViewfinderBg)
                .onSizeChanged { boxSize = it }
                .pointerInput(fit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = fit
                            activeHandle = if (r == null) -1 else {
                                val pts = quad.points()
                                var best = -1
                                var bestDist = 64.dp.toPx()
                                pts.forEachIndexed { i, p ->
                                    val d = hypot(r.left + p.x * r.width - pos.x, r.top + p.y * r.height - pos.y)
                                    if (d < bestDist) {
                                        bestDist = d
                                        best = i
                                    }
                                }
                                best
                            }
                        },
                        onDragEnd = { activeHandle = -1 },
                        onDragCancel = { activeHandle = -1 },
                        onDrag = { change, drag ->
                            val r = fit
                            if (r != null && activeHandle >= 0) {
                                change.consume()
                                val p = quad.points()[activeHandle]
                                val nx = ((p.x * r.width + drag.x) / r.width).coerceIn(0f, 1f)
                                val ny = ((p.y * r.height + drag.y) / r.height).coerceIn(0f, 1f)
                                quad = quad.with(activeHandle, NPoint(nx, ny))
                            }
                        }
                    )
                }
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = fit ?: return@Canvas
                val pts = quad.points().map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(Color(0x99000000))
                }
                drawPath(path, AppColors.ScanCyan, style = Stroke(width = 3.dp.toPx()))
                pts.forEachIndexed { i, p ->
                    val big = if (i == activeHandle) 16.dp.toPx() else 12.dp.toPx()
                    drawCircle(Color.White, big, p)
                    drawCircle(AppColors.PrimaryButton, big / 2f, p)
                }
            }
        }

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickAction(Icons.Outlined.RestartAlt, R.string.crop_reset) {
                quad = initialQuad
                notFound = false
            }
            QuickAction(Icons.Outlined.AutoFixHigh, R.string.crop_auto) {
                if (!detecting) {
                    detecting = true
                    scope.launch {
                        val q = onAutoDetect()
                        if (q != null) {
                            quad = q
                            notFound = false
                        } else {
                            notFound = true
                        }
                        detecting = false
                    }
                }
            }
            QuickAction(Icons.Outlined.CropFree, R.string.crop_full) {
                quad = Quad.fullFrame(0f)
                notFound = false
            }
        }

        // Primary actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SurfaceSofter)
                    .clickable { onApplyAndEnhance(quad) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AutoFixHigh, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.crop_enhance),
                    style = AppTextStyle.Button14SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Primary)
                    .clickable { onApply(quad) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.crop_apply),
                    style = AppTextStyle.Button14SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RowScope.QuickAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(label),
            style = AppTextStyle.Label12Medium,
            color = AppColors.TextPrimary,
            maxLines = 1
        )
    }
}