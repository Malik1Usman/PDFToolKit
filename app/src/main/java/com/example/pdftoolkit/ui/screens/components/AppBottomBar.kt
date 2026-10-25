package com.example.pdftoolkit.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pdftoolkit.ui.screens.navigations.Screen
import com.example.pdftoolkit.ui.screens.navigations.bottomNavScreens
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle


@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, ambientColor = AppColors.ShadowNav, spotColor = AppColors.ShadowNav)
            .background(AppColors.BottomBarBg)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavScreens.forEach { screen ->
                val selected = currentRoute == screen.route
                if (screen == Screen.Scan) {
                    ScanNavItem(
                        screen = screen,
                        onClick = { onNavigate(screen) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    RegularNavItem(
                        screen = screen,
                        selected = selected,
                        onClick = { onNavigate(screen) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RegularNavItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(screen.titleRes)
    Column(
        modifier = modifier
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) AppColors.PrimaryTint else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = screen.icon,
                contentDescription = label,
                tint = if (selected) AppColors.Primary else AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = AppTextStyle.NavLabel11Medium,
            color = if (selected) AppColors.Primary else AppColors.TextSecondary,
            maxLines = 1
        )
    }
}

/** Center raised "Scan" button (Home design wala). */
@Composable
private fun ScanNavItem(
    screen: Screen,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(screen.titleRes)
    Column(
        modifier = modifier.clickable(
            onClick = onClick,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppColors.FabShadow,
                    spotColor = AppColors.FabShadow
                )
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(screen.icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = AppTextStyle.NavLabel11SemiBold,
            color = AppColors.Primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
