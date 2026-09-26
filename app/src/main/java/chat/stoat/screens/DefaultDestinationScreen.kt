package chat.stoat.screens

import android.app.Activity
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.internals.getComponentActivity
import chat.stoat.composables.screens.splash.DisconnectedScreen

// Smooth sine-like easing: eases in AND out so the pulse feels natural, not mechanical
private val SineInOut = Easing { fraction ->
    (-(kotlin.math.cos(Math.PI * fraction) - 1) / 2).toFloat()
}

@Composable
fun DefaultDestinationScreen(
    navController: NavController,
    nextDestination: String? = null,
    isConnected: Boolean = false,
    onRetryConnection: () -> Unit = {}
) {
    val context = LocalContext.current

    if (!isConnected) {
        DisconnectedScreen(onRetry = { onRetryConnection() })
        return
    }

    LaunchedEffect(nextDestination) {
        nextDestination?.let {
            val activity = context.getComponentActivity() as Activity
            activity.window.statusBarColor = Color.Transparent.toArgb()
            navController.popBackStack(navController.graph.startDestinationRoute!!, true)
            navController.navigate(it)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // Logo gently breathes — smooth sine easing, not jerky linear
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = SineInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )

    // "Connecting…" fades smoothly in/out
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "text_fade"
    )

    // Pure black background — hardcoded so it's instant, no theme load needed
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon — animated via graphicsLayer (GPU only, no recomposition)
            Image(
                painter = painterResource(id = R.drawable.dismod_logo),
                contentDescription = "Dismod",
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bold "DISMOD" wordmark — uppercase, wide spacing, heavy weight
            Text(
                text = "DISMOD",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connecting label — GPU alpha animation, no recomposition
            Text(
                text = "Connecting\u2026",
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
                color = Color.White
            )
        }
    }
}