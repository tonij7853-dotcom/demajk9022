package chat.stoat.screens

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

@Composable
fun DefaultDestinationScreen(
    navController: NavController,
    nextDestination: String? = null,
    isConnected: Boolean = false,
    onRetryConnection: () -> Unit = {}
) {
    val context = LocalContext.current

    if (!isConnected) {
        DisconnectedScreen(
            onRetry = {
                onRetryConnection()
            }
        )
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

    // Pulsing animation — logo breathes in and out
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing logo
            Image(
                painter = painterResource(id = R.drawable.dismod_logo),
                contentDescription = "Dismod",
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulse)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // "Dismod" text wordmark
            Text(
                text = "Dismod",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated loading dots
            Text(
                text = "Connecting\u2026",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = dotAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}