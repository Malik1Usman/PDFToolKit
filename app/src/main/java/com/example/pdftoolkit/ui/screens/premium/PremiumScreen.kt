package com.example.pdftoolkit.ui.screens.premium

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme

// ───────────────────────── Colors (App Palette) ─────────────────────────

private val PrimaryBrush = Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryButton))
private val PrimaryTextBrush = Brush.linearGradient(listOf(AppColors.Primary, AppColors.Teal))

// ───────────────────────── Models ─────────────────────────

data class PremiumFeature(val icon: ImageVector, val title: String, val subtitle: String)

data class PremiumPlan(
    val id: String,
    val title: String,
    val price: String,          // e.g. "$29.99"
    val period: String,         // e.g. "year"
    val subtitle: String? = null,   // e.g. "Just $2.49 / month"
    val badge: String? = null       // e.g. "BEST VALUE"
)

object PremiumDefaults {
    val features = listOf(
        PremiumFeature(Icons.Rounded.Block, "No ads", "Work without any interruptions"),
        PremiumFeature(Icons.Rounded.AllInclusive, "Unlimited tools", "Use every PDF tool without limits"),
        PremiumFeature(Icons.Rounded.Bolt, "Faster processing", "Priority speed for large files"),
        PremiumFeature(Icons.Rounded.SupportAgent, "Priority support", "Get help faster when you need it")
    )

    val plans = listOf(
        PremiumPlan("weekly", "Weekly", "$1.99", "week"),
        PremiumPlan("monthly", "Monthly", "$4.99", "month", subtitle = "Flexible, cancel anytime"),
        PremiumPlan("yearly", "Yearly", "$29.99", "year", subtitle = "Just $2.50 / month", badge = "SAVE 50%")
    )
}

// ───────────────────────── Screen ─────────────────────────

@Composable
fun PremiumScreen(
    onClose: () -> Unit,
    onContinue: (PremiumPlan) -> Unit,
    modifier: Modifier = Modifier,
    plans: List<PremiumPlan> = PremiumDefaults.plans,
    features: List<PremiumFeature> = PremiumDefaults.features,
    initialPlanId: String? = null,
    ctaText: String = "Continue",
    isLoading: Boolean = false,
    onRestore: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {}
) {
    var selectedId by rememberSaveable {
        mutableStateOf(
            initialPlanId ?: plans.firstOrNull { it.badge != null }?.id ?: plans.firstOrNull()?.id.orEmpty()
        )
    }
    val selected = plans.firstOrNull { it.id == selectedId } ?: plans.firstOrNull()

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Soft top glow using AppColors.Primary
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(AppColors.Primary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PremiumHero()
                Spacer(Modifier.height(8.dp))
                Text(
                    buildAnnotatedString {
                        append("Go ")
                        withStyle(SpanStyle(brush = PrimaryTextBrush)) { append("Premium") }
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Unlock every PDF tool and work without limits",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))
                FeaturesCard(features)

                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            selected = plan.id == selectedId,
                            onClick = { selectedId = plan.id }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            BottomBar(
                ctaText = ctaText,
                isLoading = isLoading,
                onContinue = { selected?.let(onContinue) },
                onRestore = onRestore,
                onTerms = onTerms,
                onPrivacy = onPrivacy
            )
        }
    }
}

// ───────────────────────── Hero ─────────────────────────

@Composable
private fun PremiumHero() {
    val transition = rememberInfiniteTransition(label = "heroGlow")
    val glow by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(listOf(AppColors.Primary.copy(alpha = glow), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(92.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = AppColors.Primary, spotColor = AppColors.Primary)
                .clip(RoundedCornerShape(28.dp))
                .background(PrimaryBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

// ───────────────────────── Features ─────────────────────────

@Composable
private fun FeaturesCard(features: List<PremiumFeature>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.PrimaryTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(f.icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            f.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── Plan card ─────────────────────────

@Composable
private fun PlanCard(plan: PremiumPlan, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(if (selected) AppColors.Primary else scheme.outlineVariant, label = "planBorder")
    val container by animateColorAsState(
        if (selected) AppColors.PrimaryTint else scheme.surface,
        label = "planContainer"
    )
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, label = "planScale")
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = if (plan.badge != null) 10.dp else 0.dp)
                .clip(shape)
                .background(container)
                .border2(selected, borderColor, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                plan.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(plan.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("per ${plan.period}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }

        plan.badge?.let {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PrimaryBrush)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            }
        }
    }
}

/** Border helper: thicker when selected. */
private fun Modifier.border2(selected: Boolean, color: Color, shape: RoundedCornerShape): Modifier =
    this.then(Modifier.border(if (selected) 2.dp else 1.dp, color, shape))

// ───────────────────────── Bottom bar ─────────────────────────

@Composable
private fun BottomBar(
    ctaText: String,
    isLoading: Boolean,
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 12.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShimmerPremiumButton(text = ctaText, isLoading = isLoading, onClick = onContinue)
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel anytime • Billed through Google Play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FooterLink("Restore", onRestore)
                FooterDot()
                FooterLink("Terms", onTerms)
                FooterDot()
                FooterLink("Privacy", onPrivacy)
            }
        }
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FooterDot() {
    Text("•", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun ShimmerPremiumButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = AppColors.Primary, spotColor = AppColors.Primary)
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBrush)
            .drawWithContent {
                drawContent()
                val x = size.width * (progress * 2f - 0.5f)
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(x - 140f, 0f),
                        end = Offset(x + 140f, size.height)
                    )
                )
            }
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
        } else {
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ───────────────────────── Previews ─────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PremiumScreenPreview() {
    PdfToolkitTheme {
        PremiumScreen(onClose = {}, onContinue = {})
    }
}
