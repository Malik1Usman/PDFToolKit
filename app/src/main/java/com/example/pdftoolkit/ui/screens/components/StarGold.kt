package com.example.pdftoolkit.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme

private val StarGold = Color(0xFFFFB300)

private data class RatingMessage(val emoji: String, val title: String, val subtitle: String)

private val MESSAGES = listOf(
    RatingMessage("⭐", "Enjoying PDF Toolkit?", "Tap a star to rate your experience"),
    RatingMessage("😞", "We're sorry", "Tell us what went wrong and we'll fix it"),
    RatingMessage("😕", "Not great", "Help us improve with your feedback"),
    RatingMessage("🙂", "Thanks!", "Tell us how we can make it better"),
    RatingMessage("😃", "Glad you like it!", "A Play Store rating really helps us"),
    RatingMessage("🤩", "You're awesome!", "Would you share it on the Play Store?")
)

/**
 * Rate-us dialog (UI only).
 *
 * [onSubmit] receives the chosen stars (1..5). Decide what to do there, for example:
 *  - rating >= 4 → open the Play Store / in-app review
 *  - rating <= 3 → open a feedback form or email
 */
@Composable
fun RateUsDialog(
    onDismiss: () -> Unit,
    onSubmit: (rating: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialRating: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.85f)
        ) {
            RateUsContent(
                initialRating = initialRating,
                onDismiss = onDismiss,
                onSubmit = onSubmit,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun RateUsContent(
    initialRating: Int,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var rating by rememberSaveable { mutableIntStateOf(initialRating.coerceIn(0, 5)) }
    val message = MESSAGES[rating]

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Header(onDismiss)

            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Crossfade(targetState = rating, label = "ratingMessage") { r ->
                    val m = MESSAGES[r]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            m.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                StarRow(rating = rating, onRatingChange = { rating = it })
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { onSubmit(rating) },
                    enabled = rating > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        when {
                            rating >= 4 -> "Rate on Play Store"
                            rating in 1..3 -> "Send Feedback"
                            else -> "Submit"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Maybe later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ───────────────────────── Header ─────────────────────────

@Composable
private fun Header(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        // decorative circles
        Box(
            Modifier
                .size(130.dp)
                .offset(x = (-110).dp, y = (-30).dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            Modifier
                .size(90.dp)
                .offset(x = 120.dp, y = 36.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )

        Box(
            Modifier
                .size(72.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = StarGold,
                modifier = Modifier.size(44.dp)
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

// ───────────────────────── Stars ─────────────────────────

@Composable
private fun StarRow(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = i <= rating
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.15f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "star$i"
            )
            Icon(
                imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "$i star${if (i > 1) "s" else ""}",
                tint = if (filled) StarGold else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = { onRatingChange(i) })
            )
        }
    }
}

// ───────────────────────── Previews ─────────────────────────

@Preview(showBackground = true)
@Composable
private fun RateUsEmptyPreview() {
    PdfToolkitTheme {
        Box(Modifier.padding(24.dp)) { RateUsContent(initialRating = 0, onDismiss = {}, onSubmit = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RateUsFivePreview() {
    PdfToolkitTheme {
        Box(Modifier.padding(24.dp)) { RateUsContent(initialRating = 5, onDismiss = {}, onSubmit = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RateUsLowPreview() {
    PdfToolkitTheme {
        Box(Modifier.padding(24.dp)) { RateUsContent(initialRating = 2, onDismiss = {}, onSubmit = {}) }
    }
}