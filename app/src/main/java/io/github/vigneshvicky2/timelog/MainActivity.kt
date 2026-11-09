package io.github.vigneshvicky2.timelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vigneshvicky2.timelog.ui.EntranceScope
import io.github.vigneshvicky2.timelog.ui.HomeScreen
import io.github.vigneshvicky2.timelog.ui.SetupScreen
import io.github.vigneshvicky2.timelog.ui.TimelogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimelogTheme {
                val vm: TimelogViewModel = viewModel()
                Surface(color = MaterialTheme.colorScheme.surface) { AnimatedContent(
                    targetState = vm.configured,
                    transitionSpec = {
                        (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.05f))
                    },
                    label = "root",
                ) { ready -> EntranceScope { if (ready) HomeScreen(vm) else SetupScreen(vm) } } }
            }
        }
    }
}
