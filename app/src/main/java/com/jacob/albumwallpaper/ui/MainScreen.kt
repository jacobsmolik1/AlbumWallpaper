package com.jacob.albumwallpaper.ui

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jacob.albumwallpaper.AlbumArtService
import com.jacob.albumwallpaper.Settings
import com.jacob.albumwallpaper.ui.theme.AlbumWallpaperTheme
import java.io.File
import java.util.Locale

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatTime(minutesFromMidnight: Int): String {
    val h = minutesFromMidnight / 60
    val m = minutesFromMidnight % 60
    val amPm = if (h < 12) "AM" else "PM"
    val displayHour = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else   -> h
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, m, amPm)
}

fun reapply(ctx: Context) {
    LocalBroadcastManager.getInstance(ctx)
        .sendBroadcast(Intent(AlbumArtService.ACTION_REAPPLY))
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var trackTitle  by remember { mutableStateOf<String?>(null) }
    var trackArtist by remember { mutableStateOf<String?>(null) }
    var thumbnail   by remember { mutableStateOf<Bitmap?>(null) }
    var seedColor   by remember { mutableStateOf<Color?>(null) }

    // Register LocalBroadcast receiver for track changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trackTitle  = intent.getStringExtra(AlbumArtService.EXTRA_TITLE)
                trackArtist = intent.getStringExtra(AlbumArtService.EXTRA_ARTIST)
                val raw = intent.getIntExtra(AlbumArtService.EXTRA_SEED_COLOR, 0)
                if (raw != 0) {
                    seedColor = Color(raw)
                }
                // Reload thumbnail from disk
                val f = File(ctx.filesDir, "thumb.jpg")
                if (f.exists()) {
                    thumbnail = BitmapFactory.decodeFile(f.absolutePath)
                }
            }
        }
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, IntentFilter(AlbumArtService.ACTION_TRACK_CHANGED))
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    // Load thumbnail on first composition if it already exists
    LaunchedEffect(Unit) {
        val f = File(context.filesDir, "thumb.jpg")
        if (f.exists()) {
            thumbnail = BitmapFactory.decodeFile(f.absolutePath)
        }
    }

    AlbumWallpaperTheme(seedColor = seedColor) {
        MainScreenContent(
            trackTitle  = trackTitle,
            trackArtist = trackArtist,
            thumbnail   = thumbnail,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    trackTitle: String?,
    trackArtist: String?,
    thumbnail: Bitmap?,
) {
    val context = LocalContext.current
    val prefs   = remember { Settings.prefs(context) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Album Wallpaper",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text  = "Lockscreen & Home Wallpaper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor       = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            // ── Now Playing Card ──────────────────────────────────────────────
            item {
                NowPlayingCard(
                    trackTitle  = trackTitle,
                    trackArtist = trackArtist,
                    thumbnail   = thumbnail,
                )
            }

            // ── Status ────────────────────────────────────────────────────────
            item { SectionHeader("Status") }
            item {
                val isGranted = remember {
                    mutableStateOf(
                        NotificationManagerCompat.getEnabledListenerPackages(context)
                            .contains(context.packageName)
                    )
                }
                // Refresh on each composition
                LaunchedEffect(Unit) {
                    isGranted.value = NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                }
                SettingsCard {
                    ListItem(
                        headlineContent   = {
                            Text(if (isGranted.value) "Active — notification access granted"
                                 else "Notification access required")
                        },
                        supportingContent = {
                            Text(if (isGranted.value) "Watching for album art from enabled sources"
                                 else "Tap Enable to grant access in system settings")
                        },
                        leadingContent    = {
                            Icon(
                                imageVector = if (isGranted.value) Icons.Default.CheckCircle
                                              else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isGranted.value) Color(0xFF1DB954)
                                       else MaterialTheme.colorScheme.error,
                            )
                        },
                        trailingContent   = {
                            if (!isGranted.value) {
                                Button(onClick = {
                                    context.startActivity(
                                        Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                }) {
                                    Text("Enable")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── Sources ───────────────────────────────────────────────────────
            item { SectionHeader("Sources") }
            item {
                SettingsCard {
                    ToggleRow(
                        title    = "YouTube Music",
                        subtitle = "Streams music sessions",
                        prefKey  = Settings.SOURCE_YTM,
                        default  = true,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToggleRow(
                        title    = "Spotify",
                        subtitle = "Streams music sessions",
                        prefKey  = Settings.SOURCE_SPOTIFY,
                        default  = false,
                    )
                }
            }

            // ── Lockscreen ────────────────────────────────────────────────────
            item { SectionHeader("Lockscreen") }
            item {
                SettingsCard {
                    ListItem(
                        headlineContent   = { Text("Lockscreen") },
                        supportingContent = { Text("Always receives clean album art") },
                        leadingContent    = {
                            Icon(
                                imageVector        = Icons.Default.Lock,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(onClick = {}) {
                                Text(
                                    text  = "Active",
                                    color = Color(0xFF1DB954),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── Home Screen ───────────────────────────────────────────────────
            item { SectionHeader("Home Screen") }
            item {
                val homeOn = remember {
                    mutableStateOf(prefs.getBoolean(Settings.APPLY_HOME, false))
                }
                val blurOn = remember {
                    mutableStateOf(prefs.getBoolean(Settings.BLUR_ON, false))
                }
                val dimOn = remember {
                    mutableStateOf(prefs.getBoolean(Settings.DIM_ON, false))
                }

                SettingsCard {
                    ToggleRow(
                        title    = "Apply to home screen",
                        subtitle = "Also sets your launcher wallpaper",
                        prefKey  = Settings.APPLY_HOME,
                        default  = false,
                        onAfterChange = { homeOn.value = prefs.getBoolean(Settings.APPLY_HOME, false) },
                    )

                    AnimatedVisibility(
                        visible = homeOn.value,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            ToggleRow(
                                title    = "Blur",
                                subtitle = "Liquid glass blur",
                                prefKey  = Settings.BLUR_ON,
                                default  = false,
                                enabled  = homeOn.value,
                                onAfterChange = {
                                    blurOn.value = prefs.getBoolean(Settings.BLUR_ON, false)
                                    reapply(context)
                                },
                            )
                            AnimatedVisibility(
                                visible = blurOn.value && homeOn.value,
                                enter   = expandVertically() + fadeIn(),
                                exit    = shrinkVertically() + fadeOut(),
                            ) {
                                SliderRow(
                                    label    = "Intensity",
                                    prefKey  = Settings.BLUR_RADIUS,
                                    default  = 10f,
                                    range    = 1f..25f,
                                    steps    = 23,
                                    onAfterChange = { reapply(context) },
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            ToggleRow(
                                title    = "Dim",
                                subtitle = "Darken for readability",
                                prefKey  = Settings.DIM_ON,
                                default  = false,
                                enabled  = homeOn.value,
                                onAfterChange = {
                                    dimOn.value = prefs.getBoolean(Settings.DIM_ON, false)
                                    reapply(context)
                                },
                            )
                            AnimatedVisibility(
                                visible = dimOn.value && homeOn.value,
                                enter   = expandVertically() + fadeIn(),
                                exit    = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    SliderRow(
                                        label        = "Amount",
                                        prefKey      = Settings.DIM_AMOUNT,
                                        default      = 40f,
                                        range        = 0f..100f,
                                        steps        = 99,
                                        displayValue = { "${it.toInt()}%" },
                                        onAfterChange = { reapply(context) },
                                    )
                                    ToggleRow(
                                        title    = "Use album color",
                                        subtitle = "Dim with the art's dark tone instead of black",
                                        prefKey  = Settings.PALETTE_DIM,
                                        default  = false,
                                        enabled  = homeOn.value && dimOn.value,
                                        onAfterChange = { reapply(context) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Transitions ───────────────────────────────────────────────────
            item { SectionHeader("Transitions") }
            item {
                val crossfadeOn = remember {
                    mutableStateOf(prefs.getBoolean(Settings.CROSSFADE_ON, false))
                }
                SettingsCard {
                    ToggleRow(
                        title    = "Crossfade",
                        subtitle = "Smoothly blend between wallpapers",
                        prefKey  = Settings.CROSSFADE_ON,
                        default  = false,
                        onAfterChange = {
                            crossfadeOn.value = prefs.getBoolean(Settings.CROSSFADE_ON, false)
                        },
                    )
                    AnimatedVisibility(
                        visible = crossfadeOn.value,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        SliderRow(
                            label        = "Duration",
                            prefKey      = Settings.CROSSFADE_DURATION,
                            default      = 600f,
                            range        = 200f..2000f,
                            steps        = 17,
                            displayValue = { "${"%.1f".format(it / 1000f)}s" },
                            onAfterChange = { reapply(context) },
                        )
                    }
                }
            }

            // ── Behavior ──────────────────────────────────────────────────────
            item { SectionHeader("Behavior") }
            item {
                SettingsCard {
                    ToggleRow(
                        title    = "Restore on stop",
                        subtitle = "Returns your original wallpaper when music stops",
                        prefKey  = Settings.RESTORE_ON_STOP,
                        default  = false,
                    )
                }
            }

            // ── Schedule ──────────────────────────────────────────────────────
            item { SectionHeader("Schedule") }
            item {
                val scheduleOn = remember {
                    mutableStateOf(prefs.getBoolean(Settings.SCHEDULE_ON, false))
                }
                var schedStart by remember {
                    mutableIntStateOf(prefs.getInt(Settings.SCHEDULE_START, 22 * 60))
                }
                var schedEnd by remember {
                    mutableIntStateOf(prefs.getInt(Settings.SCHEDULE_END, 7 * 60))
                }

                SettingsCard {
                    ToggleRow(
                        title    = "Night dim",
                        subtitle = "Extra dim between set hours",
                        prefKey  = Settings.SCHEDULE_ON,
                        default  = false,
                        onAfterChange = {
                            scheduleOn.value = prefs.getBoolean(Settings.SCHEDULE_ON, false)
                            reapply(context)
                        },
                    )
                    AnimatedVisibility(
                        visible = scheduleOn.value,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            Row(
                                modifier            = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment   = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text     = "Active hours",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        val h = schedStart / 60
                                        val m = schedStart % 60
                                        TimePickerDialog(context, { _, hour, minute ->
                                            val newVal = hour * 60 + minute
                                            schedStart = newVal
                                            prefs.edit().putInt(Settings.SCHEDULE_START, newVal).apply()
                                            reapply(context)
                                        }, h, m, false).show()
                                    }
                                ) {
                                    Text(formatTime(schedStart))
                                }
                                Text(
                                    text  = "to",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        val h = schedEnd / 60
                                        val m = schedEnd % 60
                                        TimePickerDialog(context, { _, hour, minute ->
                                            val newVal = hour * 60 + minute
                                            schedEnd = newVal
                                            prefs.edit().putInt(Settings.SCHEDULE_END, newVal).apply()
                                            reapply(context)
                                        }, h, m, false).show()
                                    }
                                ) {
                                    Text(formatTime(schedEnd))
                                }
                            }

                            SliderRow(
                                label        = "Extra dim",
                                prefKey      = Settings.SCHEDULE_DIM,
                                default      = 30f,
                                range        = 0f..100f,
                                steps        = 19,
                                displayValue = { "${it.toInt()}%" },
                                onAfterChange = { reapply(context) },
                            )
                        }
                    }
                }
            }

            // ── Integrations ──────────────────────────────────────────────────
            item { SectionHeader("Integrations") }
            item {
                SettingsCard {
                    ToggleRow(
                        title    = "Kustom / KLWP",
                        subtitle = "Saves nowplaying.jpg to Download/Kustom/",
                        prefKey  = Settings.KUSTOM_ON,
                        default  = false,
                    )
                }
            }

            // ── Bottom spacer ─────────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

// ── Now Playing Card ──────────────────────────────────────────────────────────

@Composable
fun NowPlayingCard(
    trackTitle: String?,
    trackArtist: String?,
    thumbnail: Bitmap?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(bottom = 4.dp),
        shape  = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Background: blurred album art at low opacity
            if (thumbnail != null) {
                Image(
                    bitmap             = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop,
                    alpha              = 0.25f,
                )
            }

            // Gradient overlay: transparent top → opaque bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                            )
                        )
                    )
            )

            // Source chip in top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                val sourceLabel = when {
                    trackTitle != null -> "Now Playing"
                    else               -> "Waiting"
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ) {
                    Text(
                        text     = sourceLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (thumbnail != null) {
                // Bottom-left: thumbnail + track info
                Row(
                    modifier          = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        bitmap             = thumbnail.asImageBitmap(),
                        contentDescription = "Album art",
                        modifier           = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale       = ContentScale.Crop,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text       = trackTitle ?: "Unknown Track",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            maxLines   = 1,
                        )
                        if (!trackArtist.isNullOrEmpty()) {
                            Text(
                                text     = trackArtist,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                // No thumbnail: center music note icon
                Icon(
                    imageVector        = Icons.Default.MusicNote,
                    contentDescription = "No track playing",
                    modifier           = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
        letterSpacing = 1.sp,
    )
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    prefKey: String,
    default: Boolean,
    enabled: Boolean = true,
    onAfterChange: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs   = remember { Settings.prefs(context) }
    var checked by remember { mutableStateOf(prefs.getBoolean(prefKey, default)) }

    ListItem(
        headlineContent   = {
            Text(
                text  = title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        supportingContent = {
            Text(
                text  = subtitle,
                color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            )
        },
        trailingContent   = {
            Switch(
                checked         = checked,
                onCheckedChange = { newVal ->
                    checked = newVal
                    prefs.edit().putBoolean(prefKey, newVal).apply()
                    onAfterChange?.invoke()
                },
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SliderRow(
    label: String,
    prefKey: String,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: (Float) -> String = { it.toInt().toString() },
    onAfterChange: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs   = remember { Settings.prefs(context) }
    var value   by remember { mutableFloatStateOf(prefs.getFloat(prefKey, default)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = displayValue(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value         = value,
            onValueChange = { v ->
                value = v
                prefs.edit().putFloat(prefKey, v).apply()
            },
            onValueChangeFinished = { onAfterChange?.invoke() },
            valueRange    = range,
            steps         = steps,
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}
