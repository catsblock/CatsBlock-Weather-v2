package com.catsblock.weather.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.Condition
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.kit.BackgroundKit
import com.catsblock.weather.kit.IconKit
import com.catsblock.weather.kit.Slot
import com.catsblock.weather.kit.backgroundSlots
import com.catsblock.weather.kit.iconSlots
import java.io.File

private fun Slot.title(): String {
    val base = kind.label(day)
    if (!paired || kind.label(true) != kind.label(false)) return base
    return base + " · " + tr(if (day) "Day" else "Night")
}

private fun soundTitle(kind: Condition): String = kind.label(kind != Condition.Clear)

private fun rejected(context: Context) {
    Toast.makeText(context, tr("Could not use that file."), Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitScaffold(
    title: String,
    onBack: () -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
                },
                actions = {
                    if (canReset) {
                        IconButton(onClick = onReset) { Icon(Icons.Rounded.RestartAlt, tr("Reset all")) }
                    }
                }
            )
        },
        content = content
    )
}

@Composable
private fun ConfirmReset(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Reset all")) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Reset")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun SectionHeader(title: String?, hint: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KitTile(
    title: String,
    custom: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            preview()
            if (custom) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    title: String,
    status: String,
    hint: String,
    pickLabel: String,
    pickIcon: ImageVector,
    canReset: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
    preview: @Composable () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            preview()
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            extra()
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(pickIcon, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(pickLabel)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset, enabled = canReset, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Reset to default"))
            }
        }
    }
}

@Composable
private fun SoundPreview(file: File?) {
    var playing by remember { mutableStateOf(false) }
    val holder = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stop() {
        holder[0]?.let { runCatching { it.release() } }
        holder[0] = null
        playing = false
    }

    DisposableEffect(file?.path) { onDispose { stop() } }

    if (file != null) {
        FilledTonalButton(
            onClick = {
                if (playing) {
                    stop()
                } else {
                    val mp = MediaPlayer()
                    runCatching {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        mp.setDataSource(file.absolutePath)
                        mp.setOnPreparedListener { it.start() }
                        mp.setOnCompletionListener { stop() }
                        mp.prepareAsync()
                        holder[0] = mp
                        playing = true
                    }.onFailure { runCatching { mp.release() } }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(tr(if (playing) "Stop" else "Preview"))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IconEditor(vm: WeatherViewModel, slot: Slot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by IconKit.version.collectAsState()
    val custom = remember(version) { IconKit.hasCustom(context, slot) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importIcon(slot, uri) { ok -> if (!ok) rejected(context) }
    }
    EditorSheet(
        title = slot.title(),
        status = tr(if (custom) "Custom" else "Default"),
        hint = tr("Pick an image to replace this icon in the app and the widgets."),
        pickLabel = tr("Choose image"),
        pickIcon = Icons.Rounded.Image,
        canReset = custom,
        onPick = { picker.launch(arrayOf("image/*")) },
        onReset = { vm.resetIcon(slot) },
        onDismiss = onDismiss
    ) {
        WeatherIcon(slot.kind, slot.day, Modifier.size(104.dp))
    }
}

@Composable
private fun BackgroundEditor(vm: WeatherViewModel, slot: Slot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by BackgroundKit.version.collectAsState()
    val custom = remember(version) { BackgroundKit.hasVisual(context, slot) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackground(slot, uri) { ok -> if (!ok) rejected(context) }
    }
    EditorSheet(
        title = slot.title(),
        status = tr(if (custom) "Custom" else "Default"),
        hint = tr("Pick an image or GIF to use as this background."),
        pickLabel = tr("Choose image or GIF"),
        pickIcon = Icons.Rounded.Image,
        canReset = custom,
        onPick = { picker.launch(arrayOf("image/*")) },
        onReset = { vm.resetBackground(slot) },
        onDismiss = onDismiss
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(24.dp))) {
            WeatherBackground(slot.kind, slot.day, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SoundEditor(vm: WeatherViewModel, kind: Condition, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by BackgroundKit.version.collectAsState()
    val file = remember(version) { BackgroundKit.sound(context, kind) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSound(kind, uri) { ok -> if (!ok) rejected(context) }
    }
    EditorSheet(
        title = soundTitle(kind),
        status = tr(if (file != null) "Custom" else "Default"),
        hint = tr("Pick an MP3 to play for this weather."),
        pickLabel = tr("Choose MP3"),
        pickIcon = Icons.Rounded.MusicNote,
        canReset = file != null,
        onPick = { picker.launch(arrayOf("audio/*")) },
        onReset = { vm.resetSound(kind) },
        onDismiss = onDismiss,
        extra = { SoundPreview(file) }
    ) {
        WeatherIcon(kind, true, Modifier.size(104.dp))
    }
}

@Composable
fun IconKitScreen(vm: WeatherViewModel) {
    val context = LocalContext.current
    val version by IconKit.version.collectAsState()
    var editing by remember { mutableStateOf<Slot?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val anyCustom = remember(version) { iconSlots.any { IconKit.hasCustom(context, it) } }

    KitScaffold(tr("Weather Icon Kit"), vm::back, anyCustom, { confirm = true }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(null, tr("Tap an icon to replace it. Changes apply to the app and the widgets."))
            }
            items(iconSlots, key = { it.key }) { slot ->
                val custom = remember(version) { IconKit.hasCustom(context, slot) }
                KitTile(slot.title(), custom, { editing = slot }) {
                    WeatherIcon(slot.kind, slot.day, Modifier.fillMaxSize().padding(10.dp))
                }
            }
        }
    }

    editing?.let { slot -> key(slot.key) { IconEditor(vm, slot) { editing = null } } }

    if (confirm) {
        ConfirmReset(
            tr("All custom icons will be removed."),
            { confirm = false; vm.resetAllIcons() },
            { confirm = false }
        )
    }
}

@Composable
fun BackgroundKitScreen(vm: WeatherViewModel) {
    val context = LocalContext.current
    val version by BackgroundKit.version.collectAsState()
    var editingBackground by remember { mutableStateOf<Slot?>(null) }
    var editingSound by remember { mutableStateOf<Condition?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val anyCustom = remember(version) {
        backgroundSlots.any { BackgroundKit.hasVisual(context, it) } ||
            Condition.entries.any { BackgroundKit.sound(context, it) != null }
    }

    KitScaffold(tr("Weather Background Kit"), vm::back, anyCustom, { confirm = true }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(tr("Backgrounds"), tr("Pick an image or an animated GIF for each weather."))
            }
            items(backgroundSlots, key = { "bg_" + it.key }) { slot ->
                val custom = remember(version) { BackgroundKit.hasVisual(context, slot) }
                KitTile(slot.title(), custom, { editingBackground = slot }) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                        WeatherBackground(slot.kind, slot.day, Modifier.fillMaxSize())
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(tr("Sounds"), tr("Pick an MP3 to play as the ambient sound for each weather."))
            }
            items(Condition.entries, key = { "snd_" + it.name }) { kind ->
                val custom = remember(version) { BackgroundKit.sound(context, kind) != null }
                KitTile(soundTitle(kind), custom, { editingSound = kind }) {
                    WeatherIcon(kind, true, Modifier.fillMaxSize().padding(10.dp))
                }
            }
        }
    }

    editingBackground?.let { slot ->
        key("bg_" + slot.key) { BackgroundEditor(vm, slot) { editingBackground = null } }
    }
    editingSound?.let { kind ->
        key("snd_" + kind.name) { SoundEditor(vm, kind) { editingSound = null } }
    }

    if (confirm) {
        ConfirmReset(
            tr("All custom backgrounds and sounds will be removed."),
            { confirm = false; vm.resetAllBackgrounds() },
            { confirm = false }
        )
    }
}
