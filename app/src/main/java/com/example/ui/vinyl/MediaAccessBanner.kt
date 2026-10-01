package com.example.ui.vinyl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DeckSurfaceRaised
import com.example.ui.theme.DeckSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/**
 * MediaAccessBanner
 *
 * Minimalist, hardware-styled notification banner guiding the user to grant
 * Android Notification / Media Session access so Vinyl can read music metadata.
 *
 * Appears only when notification access is ungranted, and dismisses automatically
 * once enabled.
 *
 * @param isGranted True if notification listener permission is already active.
 * @param onGrantClick Callback opening Android's Notification Access settings.
 * @param modifier Composable layout modifier.
 */
@Composable
fun MediaAccessBanner(
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isGranted,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(width = 1.dp, color = Color(0x30FFFFFF), shape = RoundedCornerShape(10.dp))
                .background(DeckSurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.media_access_prompt_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = TextPrimary,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = stringResource(R.string.media_access_prompt_body),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            // Machined "Grant" pill button
            Row(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DeckSurfaceRaised)
                    .border(width = 0.8.dp, color = Color(0x40FFFFFF), shape = RoundedCornerShape(6.dp))
                    .clickable(onClick = onGrantClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("grant_media_access_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.media_access_prompt_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
