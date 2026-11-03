package com.drengr.launcher.feature.smarthome

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.runtime.key
import com.drengr.launcher.core.common.inOrder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.PageHeader
import com.drengr.launcher.core.designsystem.Pill
import com.drengr.launcher.integrations.govee.GoveeDevice
import com.drengr.launcher.integrations.govee.GoveeDeviceType
import kotlin.math.roundToInt

/** A colour shortcut: either a white temperature or an RGB colour. */
private data class Swatch(val label: String, val display: Color, val kelvin: Int? = null, val rgb: Int? = null)

private val WHITES = listOf(
    Swatch("Warm white", Color(0xFFFFC98A), kelvin = 2700),
    Swatch("Neutral white", Color(0xFFFFEBD1), kelvin = 4000),
    Swatch("Daylight", Color(0xFFE6EEFF), kelvin = 6500),
)
private val COLORS = listOf(
    Swatch("Red", Color(0xFFFF3B30), rgb = 0xFF3B30),
    Swatch("Orange", Color(0xFFFF9500), rgb = 0xFF9500),
    Swatch("Yellow", Color(0xFFFFD60A), rgb = 0xFFD60A),
    Swatch("Green", Color(0xFF34C759), rgb = 0x34C759),
    Swatch("Blue", Color(0xFF579BFF), rgb = 0x579BFF),
    Swatch("Purple", Color(0xFFAF52DE), rgb = 0xAF52DE),
    Swatch("Pink", Color(0xFFFF2D92), rgb = 0xFF2D92),
)

@Composable
fun SmartHomePage(viewModel: SmartHomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val savingKey by viewModel.savingKey.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val state = ui
    val ordered = (state as? SmartHomeUi.Ready)?.devices.orEmpty().inOrder(order) { it.device.id }
    val shown = ordered.filter { it.device.id !in hidden }
    val hiddenDevices = ordered.filter { it.device.id in hidden }
    val subtitle = when (state) {
        is SmartHomeUi.Ready -> "Govee · " + shown.size + (if (shown.size == 1) " device" else " devices") +
            if (hiddenDevices.isNotEmpty()) " · ${hiddenDevices.size} hidden" else ""
        else -> "Govee"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PageHeader("Smart home", subtitle = subtitle, modifier = Modifier.weight(1f))
            if (state is SmartHomeUi.Ready) {
                if (state.refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = DrengrTheme.colors.accent, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, "Refresh devices", tint = DrengrTheme.colors.textSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when (state) {
            SmartHomeUi.Loading -> Centered { CircularProgressIndicator(color = DrengrTheme.colors.accent) }
            SmartHomeUi.NeedsKey -> SetupCard(saving = savingKey, onSave = viewModel::saveKey)
            is SmartHomeUi.Error -> DrengrCard(Modifier.fillMaxWidth()) {
                Text("Couldn't load your devices", style = DrengrTheme.typography.title)
                Text(state.message, style = DrengrTheme.typography.caption, color = DrengrTheme.colors.textSecondary)
                TextButton(onClick = { viewModel.refresh() }) { Text("Try again", color = DrengrTheme.colors.accent) }
            }
            is SmartHomeUi.Ready -> {
                if (state.devices.isEmpty()) {
                    DrengrCard(Modifier.fillMaxWidth()) {
                        Text("No devices yet", style = DrengrTheme.typography.title)
                        Text(
                            "Add Wi-Fi devices in the Govee Home app, then refresh. Bluetooth-only devices can't be reached over the internet.",
                            style = DrengrTheme.typography.caption,
                            color = DrengrTheme.colors.textSecondary,
                        )
                    }
                } else {
                    if (shown.count { it.device.canPower } > 1) {
                        AllOffButton(onClick = viewModel::allOff)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (shown.isEmpty()) {
                        Text(
                            "All your devices are hidden. Show them again below.",
                            style = DrengrTheme.typography.caption,
                            color = DrengrTheme.colors.textSecondary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        shown.forEachIndexed { index, item ->
                            key(item.device.id) {
                                DeviceCard(
                                    item = item,
                                    viewModel = viewModel,
                                    position = index,
                                    lastPosition = shown.lastIndex,
                                    onMove = { viewModel.move(item.device, it) },
                                    onHide = { viewModel.setHidden(item.device, true) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (hiddenDevices.isEmpty()) {
                        Text(
                            "Long-press a device to move or hide it.",
                            style = DrengrTheme.typography.caption,
                            color = DrengrTheme.colors.textSecondary,
                        )
                    } else {
                        HiddenDevices(hiddenDevices, onShow = { viewModel.setHidden(it, false) })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AllOffButton(onClick: () -> Unit) {
    val colors = DrengrTheme.colors
    Row(
        Modifier
            .clip(DrengrTheme.shapes.pill)
            .background(colors.surfaceElevated)
            .clickable(onClickLabel = "Turn everything off", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PowerSettingsNew, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("All off", style = DrengrTheme.typography.label)
    }
}

@Composable
private fun DeviceCard(
    item: DeviceUi,
    viewModel: SmartHomeViewModel,
    position: Int,
    lastPosition: Int,
    onMove: (Int) -> Unit,
    onHide: () -> Unit,
) {
    val colors = DrengrTheme.colors
    val haptics = LocalHapticFeedback.current
    val device = item.device
    val state = item.state
    val offline = state.online == false
    val on = state.on == true
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        DrengrCard(
            Modifier.fillMaxWidth().alpha(if (offline) 0.55f else 1f),
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(device.type.icon())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = DrengrTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            offline -> "Offline"
                            state.on == null -> device.sku
                            on && state.brightness != null && device.brightness != null -> "On · ${state.brightness}%"
                            on -> "On"
                            else -> "Off"
                        },
                        style = DrengrTheme.typography.caption,
                        color = if (on && !offline) colors.accent else colors.textSecondary,
                    )
                }
                if (device.canPower) {
                    Switch(
                        checked = on,
                        onCheckedChange = { viewModel.setPower(device, it) },
                        enabled = !offline,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.surfaceElevated,
                            uncheckedBorderColor = colors.divider,
                        ),
                    )
                }
            }

            if (on && !offline) {
                device.brightness?.let { range ->
                    BrightnessSlider(device, range, state.brightness, onChange = { viewModel.setBrightness(device, it) })
                }
                val swatches = (if (device.colorTemperature != null) WHITES else emptyList()) +
                    (if (device.canColor) COLORS else emptyList())
                if (swatches.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        swatches.forEach { swatch ->
                            val selected = swatch.kelvin != null && swatch.kelvin == state.colorTemperatureK ||
                                swatch.rgb != null && swatch.rgb == state.color && state.colorTemperatureK == null
                            SwatchDot(swatch, selected) {
                                if (swatch.kelvin != null) {
                                    val range = device.colorTemperature ?: return@SwatchDot
                                    viewModel.setColorTemperature(device, swatch.kelvin.coerceIn(range))
                                } else if (swatch.rgb != null) {
                                    viewModel.setColor(device, swatch.rgb)
                                }
                            }
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                device.name,
                style = DrengrTheme.typography.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (position > 0) {
                DropdownMenuItem(
                    text = { Text("Move to top") },
                    leadingIcon = { Icon(Icons.Outlined.VerticalAlignTop, null) },
                    onClick = {
                        menuOpen = false
                        onMove(-position)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move up") },
                    leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) },
                    onClick = {
                        menuOpen = false
                        onMove(-1)
                    },
                )
            }
            if (position < lastPosition) {
                DropdownMenuItem(
                    text = { Text("Move down") },
                    leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) },
                    onClick = {
                        menuOpen = false
                        onMove(1)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Hide from this page") },
                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                onClick = {
                    menuOpen = false
                    onHide()
                },
            )
        }
    }
}

/** Hidden devices, collapsed by default, each with a Show button. */
@Composable
private fun HiddenDevices(devices: List<DeviceUi>, onShow: (GoveeDevice) -> Unit) {
    val colors = DrengrTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    DrengrCard(
        Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hidden devices (${devices.size})", style = DrengrTheme.typography.body)
                Text("Not shown here and skipped by All off", style = DrengrTheme.typography.caption, color = colors.textSecondary)
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                if (expanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            devices.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.device.type.icon(), null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.device.name,
                        style = DrengrTheme.typography.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShow(item.device) }) { Text("Show", color = colors.accent) }
                }
            }
        }
    }
}

/** Sends brightness when the drag ends, not on every step, to stay inside Govee's rate limits. */
@Composable
private fun BrightnessSlider(device: GoveeDevice, range: IntRange, current: Int?, onChange: (Int) -> Unit) {
    val colors = DrengrTheme.colors
    var dragging by remember(device.id) { mutableStateOf<Float?>(null) }
    val value = dragging ?: (current ?: range.last).toFloat()
    Spacer(Modifier.height(6.dp))
    Slider(
        value = value.coerceIn(range.first.toFloat(), range.last.toFloat()),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onChange(it.roundToInt()) }
            dragging = null
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.divider,
        ),
        modifier = Modifier.height(28.dp),
    )
}

@Composable
private fun SwatchDot(swatch: Swatch, selected: Boolean, onClick: () -> Unit) {
    val colors = DrengrTheme.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) colors.textPrimary else colors.divider, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(swatch.display)
            .clickable(onClickLabel = swatch.label, onClick = onClick),
    )
}

@Composable
private fun SetupCard(saving: Boolean, onSave: (String) -> Unit) {
    val colors = DrengrTheme.colors
    var key by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    DrengrCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Lightbulb)
            Spacer(Modifier.width(12.dp))
            Text("Connect Govee", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
            Pill("Cloud API")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Control your Govee lights and plugs from here. Drengr needs your personal Govee API key:",
            style = DrengrTheme.typography.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "Open the Govee Home app → Profile → Settings (gear).",
            "Tap “Apply for API Key”, fill in the short form.",
            "Govee emails you the key. Paste it below.",
        ).forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = DrengrTheme.typography.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            singleLine = true,
            label = { Text("Govee API key") },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank() && !saving) onSave(key) }),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (reveal) "Hide key" else "Show key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Stored only on this phone, never in backups. Device commands go through Govee's servers.",
                style = DrengrTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) {
                    Text("Connect", color = if (key.isNotBlank()) colors.accent else colors.textSecondary)
                }
            }
        }
    }
}

private fun GoveeDeviceType.icon(): ImageVector = when (this) {
    GoveeDeviceType.Light -> Icons.Outlined.Lightbulb
    GoveeDeviceType.Plug -> Icons.Outlined.Power
    GoveeDeviceType.Other -> Icons.Outlined.DevicesOther
}
