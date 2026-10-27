package com.catsblock.weather.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.kit.AppIconKit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconScreen(vm: WeatherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by AppIconKit.version.collectAsState()
    val file = remember(version) { AppIconKit.custom(context) }
    val image = remember(version) { file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }
    val supported = remember { AppIconKit.supported(context) }
    var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                if (!AppIconKit.import(context, uri)) {
                    Toast.makeText(context, tr("Could not use that file."), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("App icon changer")) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(
                        Icons.Rounded.AppShortcut,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                tr(if (file != null) "Custom" else "Default"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Pick an image to use as the app icon. It is added to the home screen as a shortcut, and the original icon stays in the app drawer."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(tr("Choose image"))
            }
            if (file != null) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (!AppIconKit.apply(context)) {
                            Toast.makeText(
                                context,
                                tr("Could not add the icon to the home screen."),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = supported,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AppShortcut, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(tr("Add to home screen"))
                }
            }
            if (!supported) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Your launcher does not support adding icons to the home screen."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { confirm = true },
                enabled = file != null || AppIconKit.hasShortcut(context),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Reset to default"))
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Reset all")) },
            text = { Text(tr("The custom app icon will be removed.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { AppIconKit.reset(context) }
                }) { Text(tr("Reset")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel")) } }
        )
    }
}
