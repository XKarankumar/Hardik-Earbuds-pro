package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppConstants
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlatinumSilver
import kotlinx.coroutines.delay

@Composable
fun SplashOverlay(
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var animateIn by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.88f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        animateIn = true
        delay(1050L)
        visible = false
        delay(220L)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF10131C), ObsidianVoid, Color(0xFF050608))
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    visible = false
                    onFinished()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
                    .padding(32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_buds_pro_foreground),
                    contentDescription = stringResource(R.string.cd_buds_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = AppConstants.APP_NAME,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = PlatinumSilver
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AppConstants.APP_VERSION,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7DD3FC)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = AppConstants.COPYRIGHT_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B758A)
                )
            }
        }
    }
}
