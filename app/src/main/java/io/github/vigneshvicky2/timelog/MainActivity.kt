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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vigneshvicky2.timelog.ui.EntranceScope
import io.github.vigneshvicky2.timelog.ui.HomeScreen
import io.github.vigneshvicky2.timelog.ui.SetupScreen
import io.github.vigneshvicky2.timelog.ui.TimelogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vmMain = androidx.lifecycle.ViewModelProvider(this)[TimelogViewModel::class.java]
        val quickLaunch = intent?.action == io.github.vigneshvicky2.timelog.reminder.Reminders.ACTION_LOG
        if (quickLaunch && savedInstanceState == null) handleLog(vmMain)
        setContent {
            TimelogTheme {
                val vm: TimelogViewModel = viewModel()
                Surface(color = MaterialTheme.colorScheme.surface) { androidx.compose.foundation.layout.Box { AnimatedContent(
                    targetState = vm.configured,
                    transitionSpec = {
                        (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.92f)) togetherWith
                            (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.05f))
                    },
                    label = "root",
                ) { ready -> EntranceScope { if (ready) HomeScreen(vm) else SetupScreen(vm) } }
                    io.github.vigneshvicky2.timelog.ui.IssuePeekHost(vm)
                    var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(savedInstanceState == null && !quickLaunch) }
                    // Hold update prompts and "What's new" until the splash has finished.
                    if (splash) io.github.vigneshvicky2.timelog.ui.SplashOverlay(onDone = { splash = false })
                    else io.github.vigneshvicky2.timelog.update.UpdateHost(viewModel())
                } }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == io.github.vigneshvicky2.timelog.reminder.Reminders.ACTION_LOG) {
            handleLog(androidx.lifecycle.ViewModelProvider(this)[TimelogViewModel::class.java])
        }
    }

    /** Opened from the "Log time" shortcut, widget or reminder: go straight to Add time. */
    private fun handleLog(vm: TimelogViewModel) {
        io.github.vigneshvicky2.timelog.reminder.Reminders.dismiss(this)
        vm.requestQuickLog()
    }
}
