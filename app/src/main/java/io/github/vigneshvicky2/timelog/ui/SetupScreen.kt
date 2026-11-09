package io.github.vigneshvicky2.timelog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TimelogViewModel
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(vm: TimelogViewModel) {
    var token by rememberSaveable { mutableStateOf("") }
    var site by rememberSaveable { mutableStateOf(vm.site) }
    var email by rememberSaveable { mutableStateOf(vm.email) }
    var editAccount by remember { mutableStateOf(vm.site.isBlank() || vm.email.isBlank()) }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AnimatedBlobs()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo(Modifier.enter(0))
            Spacer(Modifier.height(28.dp))
            Text(
                "Timelog",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.enter(1),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Log Jira time for several days in one go, with your meetings ready as suggestions.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.enter(2),
            )
            Spacer(Modifier.height(36.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text("Jira API key") },
                leadingIcon = { Icon(Icons.Outlined.VpnKey, null) },
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show key")
                    }
                },
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .enter(3)
                    .graphicsLayer { translationX = shake.value },
            )

            AnimatedVisibility(
                visible = editAccount,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        site, { site = it; error = null }, label = { Text("Jira site") },
                        placeholder = { Text("your-team.atlassian.net") },
                        leadingIcon = { Icon(Icons.Outlined.Language, null) },
                        singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        email, { email = it; error = null }, label = { Text("Atlassian email") },
                        leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (site.isNotBlank() && email.isNotBlank()) TextButton(onClick = { editAccount = !editAccount }, modifier = Modifier.enter(4)) {
                Text(
                    if (editAccount) "Done" else "$site · $email",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            val connectPress = rememberPress(0.97f)
            Button(
                interactionSource = connectPress.source,
                onClick = {
                    scope.launch {
                        busy = true
                        val err = vm.connect(token, site, email)
                        busy = false
                        if (err != null) {
                            error = err
                            shake.animateTo(0f, keyframes {
                                durationMillis = 450
                                -22f at 50
                                22f at 110
                                -16f at 170
                                16f at 230
                                -8f at 290
                                0f at 450
                            })
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp).enter(5).then(connectPress.modifier),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Connect to Jira", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { uri.openUri("https://id.atlassian.com/manage-profile/security/api-tokens") },
                modifier = Modifier.enter(6),
            ) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Create an API key")
            }
        }
    }
}

@Composable
private fun Logo(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier
            .size(96.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(primary, tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AvTimer, null, Modifier.size(52.dp), tint = Color.White)
    }
}
