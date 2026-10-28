 package com.example.mediaqueue

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mediaqueue.data.DownloadStatus
import com.example.mediaqueue.ui.MediaQueueViewModel
import com.example.mediaqueue.ui.theme.MediaQueueTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MediaQueueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedText(intent)

        setContent {
            MediaQueueTheme {
                Surface(Modifier.fillMaxSize()) {
                    QueueScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    private fun handleSharedText(intent: Intent?) {
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) {
            val url = Regex("https?://\\S+").find(sharedText)?.value
            if (url != null) viewModel.addUrl(url)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: MediaQueueViewModel) {
    val downloads by viewModel.items.collectAsState()
    val floatingEnabled by viewModel.floatingButtonEnabled.collectAsState()
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MediaQueue") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (!Settings.canDrawOverlays(context)) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable floating button permission")
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Paste direct file URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.addUrl(url.trim())
                    url = ""
                },
                enabled = url.trim().startsWith("https://") ||
                    url.trim().startsWith("http://"),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Add to queue")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Floating MQ button")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = floatingEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled || Settings.canDrawOverlays(context)) {
                            viewModel.setFloatingButtonEnabled(enabled)
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                )
            }

            Text(
                "Download queue",
                style = MaterialTheme.typography.titleMedium
            )

            if (downloads.isEmpty()) {
                Text(
                    "No downloads yet. Add a direct file link above.",
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(downloads, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title ?: item.url)
                            Text(
                                item.status.name +
                                    if (item.totalBytes > 0) {
                                        " • ${(item.progressPercent * 100).toInt()}%"
                                    } else "",
                                style = MaterialTheme.typography.bodySmall
                            )

                            LinearProgressIndicator(
                                progress = {
                                    item.progressPercent.coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )

                            Row {
                                when (item.status) {
                                    DownloadStatus.DOWNLOADING -> {
                                        TextButton(
                                            onClick = { viewModel.pause(item.id) }
                                        ) { Text("Pause") }
                                    }

                                    DownloadStatus.PAUSED -> {
                                        TextButton(
                                            onClick = { viewModel.resume(item.id) }
                                        ) { Text("Resume") }
                                    }

                                    DownloadStatus.FAILED,
                                    DownloadStatus.CANCELED -> {
                                        TextButton(
                                            onClick = { viewModel.retry(item.id) }
                                        ) { Text("Retry") }
                                    }

                                    else -> {}
                                }

                                if (item.status != DownloadStatus.COMPLETED &&
                                    item.status != DownloadStatus.CANCELED
                                ) {
                                    TextButton(
                                        onClick = { viewModel.cancel(item.id) }
                                    ) { Text("Cancel") }
                                }
                            }

                            if (item.status == DownloadStatus.FAILED &&
                                !item.errorMessage.isNullOrBlank()
                            ) {
                                Text(
                                    item.errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
