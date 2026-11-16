package io.github.vigneshvicky2.timelog.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Update prompt + one-time "What's new" sheet, layered over the whole app. */
@Composable
fun UpdateHost(uvm: UpdateViewModel) {
    if (uvm.whatsNew.isNotEmpty()) WhatsNewDialog(uvm.currentVersion, uvm.whatsNew) { uvm.dismissWhatsNew() }
    else if (uvm.promptVisible) UpdateDialog(uvm)
}

@Composable
private fun UpdateDialog(uvm: UpdateViewModel) {
    val s = uvm.state
    AlertDialog(
        onDismissRequest = { uvm.closePrompt() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = {
            Text(
                when (s) {
                    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall ->
                        "Stint ${(s as? UpdateState.Available)?.release?.version ?: (s as? UpdateState.Downloading)?.release?.version ?: (s as UpdateState.ReadyToInstall).release.version} is here"
                    UpdateState.UpToDate -> "You're up to date"
                    is UpdateState.Failed -> "Update failed"
                    else -> "Checking for updates…"
                }
            )
        },
        text = {
            AnimatedContent(s, contentKey = { it::class }, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "upd") { st ->
                Column(Modifier.fillMaxWidth()) {
                    when (st) {
                        UpdateState.Checking, UpdateState.Idle -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                        UpdateState.UpToDate -> Text("Version ${uvm.currentVersion} is the latest.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is UpdateState.Available -> Notes(st.release, uvm.currentVersion)
                        is UpdateState.Downloading -> {
                            Notes(st.release, uvm.currentVersion)
                            Spacer(Modifier.height(16.dp))
                            val p by animateFloatAsState(st.progress, spring(stiffness = Spring.StiffnessLow), label = "p")
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text("Downloading… ${(st.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        is UpdateState.ReadyToInstall -> Text(
                            "Downloaded. Tap Install and confirm in the Android installer. If asked, allow Stint to install apps, then come back and tap Install again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is UpdateState.Failed -> Text(st.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.Available -> Button(onClick = { uvm.download() }) { Text("Update now") }
                is UpdateState.Downloading -> Button(onClick = {}, enabled = false) { Text("Downloading…") }
                is UpdateState.ReadyToInstall -> Button(onClick = { uvm.install() }) { Text("Install") }
                is UpdateState.Failed -> if (s.release != null) Button(onClick = { uvm.download() }) { Text("Retry") }
                    else Button(onClick = { uvm.check() }) { Text("Retry") }
                else -> TextButton(onClick = { uvm.closePrompt() }) { Text("OK") }
            }
        },
        dismissButton = {
            when (s) {
                is UpdateState.Available -> TextButton(onClick = { uvm.later() }) { Text("Later") }
                is UpdateState.Downloading -> {}
                is UpdateState.ReadyToInstall, is UpdateState.Failed -> TextButton(onClick = { uvm.closePrompt() }) { Text("Close") }
                else -> {}
            }
        },
    )
}

@Composable
private fun Notes(r: Release, current: String) {
    Column {
        Text("You have $current. ${if (r.size > 0) "Download is ${"%.1f".format(r.size / 1_048_576f)} MB." else ""}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val lines = r.notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("Install") }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.forEach { l ->
                    Row {
                        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                        Text(l.removePrefix("- ").removePrefix("* ").replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Shown once after an update: each change pops in one after another. */
@Composable
private fun WhatsNewDialog(version: String, entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            var on by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { on = true }
            val sc by animateFloatAsState(if (on) 1f else 0.3f, spring(0.4f, Spring.StiffnessLow), label = "ic")
            Box(
                Modifier.size(56.dp).graphicsLayer { scaleX = sc; scaleY = sc; rotationZ = (1f - sc) * -90f }
                    .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        },
        title = { Text("What's new in $version") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                var index = 0
                entries.forEach { e ->
                    Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    e.items.forEach { item ->
                        val i = index++
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(140L + i * 90L); shown = true }
                        val t by animateFloatAsState(if (shown) 1f else 0f, spring(0.6f, Spring.StiffnessMediumLow), label = "it")
                        Row(
                            Modifier.padding(bottom = 10.dp).graphicsLayer { alpha = t; translationY = (1f - t) * 28f },
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp).padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Let's go") } },
    )
}
