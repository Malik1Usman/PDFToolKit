package com.example.pdftoolkit.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle


/**
 * Shared top bar: logo + "PDF Toolkit" + current screen ka naam (subtitle).
 */
@Composable
fun AppTopBar(
    subtitle: String,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, ambientColor = AppColors.ShadowSoft, spotColor = AppColors.ShadowSoft)
            .background(AppColors.TopBarBg)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // TODO: apna exported logo laga do -> painterResource(R.drawable.pdf_toolkit_logo)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = stringResource(R.string.cd_logo),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppTextStyle.Title18Medium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.Search,
                    stringResource(R.string.cd_search),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    stringResource(R.string.cd_more_options),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    stringResource(R.string.cd_profile),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
