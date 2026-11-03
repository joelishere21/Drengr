package com.quadro.launcher.feature.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quadro.launcher.BuildConfig
import com.quadro.launcher.core.designsystem.AccentColor
import com.quadro.launcher.core.designsystem.Pill
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.designsystem.SectionHeader
import com.quadro.launcher.core.domain.AppLayout
import com.quadro.launcher.core.domain.PageId
import com.quadro.launcher.core.domain.UserSettings
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, settings: UserSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = QuadroTheme.colors
    val isDefaultHome by viewModel.isDefaultHome.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDefaultHome()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultHome()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::import)
    }

    fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Settings", style = QuadroTheme.typography.headline)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                QuadroCard(Modifier.fillMaxWidth(), onClick = ::requestDefaultHome) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Default Home app", style = QuadroTheme.typography.title)
                            Text(
                                if (isDefaultHome) "Quadro is your Home app" else "Tap to make Quadro your Home app",
                                style = QuadroTheme.typography.caption,
                                color = colors.textSecondary,
                            )
                        }
                        Pill(if (isDefaultHome) "Active" else "Not set", highlighted = isDefaultHome)
                    }
                }
            }

            item { SectionHeader("Appearance", Modifier.padding(top = 8.dp)) }
            item {
                QuadroCard(Modifier.fillMaxWidth()) {
                    Text("Accent color", style = QuadroTheme.typography.body)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentColor.entries.forEach { accent ->
                            val selected = accent == settings.accent
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(accent.accent)
                                    .border(2.dp, if (selected) colors.textPrimary else colors.divider, CircleShape)
                                    .clickable(onClickLabel = accent.displayName) { viewModel.setAccent(accent) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Outlined.Check, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingRow("App drawer layout") {
                        Segmented(
                            options = AppLayout.entries.map { it.name },
                            selected = settings.appLayout.name,
                            onSelect = { name -> viewModel.setLayout(AppLayout.valueOf(name)) },
                        )
                    }
                    if (settings.appLayout == AppLayout.Grid) {
                        SettingRow("Grid columns") {
                            Segmented(
                                options = listOf("3", "4", "5", "6"),
                                selected = settings.gridColumns.toString(),
                                onSelect = { viewModel.setColumns(it.toInt()) },
                            )
                        }
                    }
                    SettingRow("Clock format") {
                        Segmented(
                            options = listOf("System", "12h", "24h"),
                            selected = when (settings.use24Hour) {
                                null -> "System"
                                true -> "24h"
                                false -> "12h"
                            },
                            onSelect = {
                                viewModel.setUse24Hour(
                                    when (it) {
                                        "24h" -> true
                                        "12h" -> false
                                        else -> null
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                WallpaperCard(
                    dim = settings.wallpaperDim,
                    onDimChange = viewModel::setWallpaperDim,
                    onChangeWallpaper = viewModel::changeWallpaper,
                )
            }

            item { SectionHeader("Pages", Modifier.padding(top = 8.dp)) }
            item {
                QuadroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp)) {
                    settings.pageOrder.forEachIndexed { index, page ->
                        PageRow(
                            page = page,
                            visible = page == PageId.Home || page !in settings.hiddenPages,
                            canMoveUp = index > 0,
                            canMoveDown = index < settings.pageOrder.lastIndex,
                            onMove = { viewModel.movePage(page, it) },
                            onVisibleChange = { viewModel.setPageHidden(page, !it) },
                        )
                    }
                }
            }

            item { SectionHeader("Gestures", Modifier.padding(top = 8.dp)) }
            item {
                QuadroCard(Modifier.fillMaxWidth()) {
                    SwitchRow(
                        title = "Swipe down for Command Center",
                        subtitle = "On the Home page. Swipe up always opens Applications.",
                        checked = settings.swipeDownForCommandCenter,
                        onChange = viewModel::setSwipeDown,
                    )
                }
            }

            item { SectionHeader("Backup & restore", Modifier.padding(top = 8.dp)) }
            item {
                QuadroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)) {
                    ClickRow("Export settings", "Save a JSON file with your layout and preferences") {
                        exportLauncher.launch("quadro-settings-${LocalDate.now()}.json")
                    }
                    ClickRow("Import settings", "Restore from a Quadro JSON file") {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }
            }

            item { SectionHeader("About", Modifier.padding(top = 8.dp)) }
            item {
                QuadroCard(Modifier.fillMaxWidth()) {
                    Text("QUADRO", style = QuadroTheme.typography.title, color = colors.accent)
                    Text("A smarter home for Android.", style = QuadroTheme.typography.body)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = QuadroTheme.typography.mono,
                        color = colors.textSecondary,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun WallpaperCard(dim: Int, onDimChange: (Int) -> Unit, onChangeWallpaper: () -> Unit) {
    val colors = QuadroTheme.colors
    // Track the drag locally and save once the finger lifts, instead of on every frame.
    var dragValue by remember(dim) { mutableFloatStateOf(dim.toFloat()) }
    QuadroCard(Modifier.fillMaxWidth()) {
        ClickRow("Change wallpaper", "Opens the system wallpaper picker", onChangeWallpaper)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wallpaper dimming", style = QuadroTheme.typography.body, modifier = Modifier.weight(1f))
            Text("${dragValue.roundToInt()}%", style = QuadroTheme.typography.mono, color = colors.textSecondary)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onDimChange(dragValue.roundToInt()) },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surfaceElevated,
                activeTickColor = colors.onAccent,
                inactiveTickColor = colors.divider,
            ),
        )
        Text(
            "Darkens the wallpaper so text stays readable. 100% hides it.",
            style = QuadroTheme.typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SettingRow(title: String, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = QuadroTheme.typography.body, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = QuadroTheme.colors
    val shape = QuadroTheme.shapes.small
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, colors.divider, shape),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                option,
                style = QuadroTheme.typography.caption,
                color = if (isSelected) colors.onAccent else colors.textSecondary,
                modifier = Modifier
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun PageRow(
    page: PageId,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(page.title, style = QuadroTheme.typography.body)
            if (page == PageId.Home) {
                Text("Always shown", style = QuadroTheme.typography.caption, color = QuadroTheme.colors.textSecondary)
            }
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, "Move ${page.title} up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, "Move ${page.title} down")
        }
        Switch(
            checked = visible,
            onCheckedChange = onVisibleChange,
            enabled = page != PageId.Home,
            colors = quadroSwitchColors(),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QuadroTheme.typography.body)
            Text(subtitle, style = QuadroTheme.typography.caption, color = QuadroTheme.colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = quadroSwitchColors())
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = QuadroTheme.typography.body)
        Text(subtitle, style = QuadroTheme.typography.caption, color = QuadroTheme.colors.textSecondary)
    }
}

@Composable
private fun quadroSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = QuadroTheme.colors.onAccent,
    checkedTrackColor = QuadroTheme.colors.accent,
    uncheckedThumbColor = QuadroTheme.colors.textSecondary,
    uncheckedTrackColor = QuadroTheme.colors.surfaceElevated,
    uncheckedBorderColor = QuadroTheme.colors.divider,
)
