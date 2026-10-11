// SPDX-FileCopyrightText: 2026 Hirusha Adikari
// SPDX-License-Identifier: MIT

package dev.hirusha.lscontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var ble: BleController
    private var hasBluetoothPermission by mutableStateOf(false)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBluetoothPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ble = BleController(this)
        hasBluetoothPermission = ble.hasPermission

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerScreen(ble, hasBluetoothPermission) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            permLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::ble.isInitialized) hasBluetoothPermission = ble.hasPermission
    }

    override fun onDestroy() {
        ble.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(
    ble: BleController,
    hasBluetoothPermission: Boolean,
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.getString(R.string.idle)) }
    var activeCommand by remember { mutableStateOf<Protocol.Command?>(null) }
    var selectedPrefixIndex by remember { mutableIntStateOf(-1) }
    var prefixMenuExpanded by remember { mutableStateOf(false) }

    val prefixOptions = listOf(stringResource(R.string.all_prefixes)) +
        Protocol.PREFIXES.map { context.getString(R.string.prefix_option, it.name, it.deviceCount) }
    val selectedLabel = prefixOptions[selectedPrefixIndex + 1]
    val isActive = activeCommand != null

    LaunchedEffect(hasBluetoothPermission) {
        status = ble.unavailableReason() ?: context.getString(R.string.idle)
        if (!hasBluetoothPermission) {
            ble.stopAll()
            activeCommand = null
        }
    }

    fun send(cmd: Protocol.Command) {
        if (activeCommand == cmd) {
            ble.stopAll()
            activeCommand = null
            status = context.getString(R.string.stopped)
            return
        }
        activeCommand = cmd
        val updateStatus: (BroadcastStatus) -> Unit = { update ->
            status = update.text
            if (update.failed) activeCommand = null
        }
        if (selectedPrefixIndex == -1) {
            ble.startCycle(cmd.byte, onStatus = updateStatus)
        } else {
            val prefix = Protocol.PREFIXES[selectedPrefixIndex]
            ble.startSingle(prefix.bytes, cmd.byte, onStatus = updateStatus)
        }
    }

    fun stop() {
        ble.stopAll()
        activeCommand = null
        status = context.getString(R.string.stopped)
    }

    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val minimumHeight = 560.dp * fontScale + if (hasBluetoothPermission) 0.dp else 48.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxOf(maxHeight, minimumHeight))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(
                    label = if (isActive) activeCommand!!.label(context) else stringResource(R.string.idle),
                    active = isActive
                )
            }

            ExposedDropdownMenuBox(
                expanded = prefixMenuExpanded,
                onExpandedChange = { prefixMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.prefix)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(prefixMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = prefixMenuExpanded,
                    onDismissRequest = { prefixMenuExpanded = false }
                ) {
                    prefixOptions.forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedPrefixIndex = i - 1
                                prefixMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.vibration))
            Spacer(Modifier.height(6.dp))

            val vibModes = listOf(
                Protocol.Command.MODE1, Protocol.Command.MODE2, Protocol.Command.MODE3,
                Protocol.Command.MODE4, Protocol.Command.MODE5, Protocol.Command.MODE6,
                Protocol.Command.MODE7, Protocol.Command.MODE8, Protocol.Command.MODE9,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in vibModes.chunked(3)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cmd in row) {
                            ControlButton(
                                label = cmd.byte.toString(),
                                accessibilityLabel = cmd.label(context),
                                isActive = activeCommand == cmd,
                                onClick = { send(cmd) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SectionLabel(stringResource(R.string.heat))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControlButton(
                            stringResource(R.string.heat_on_label),
                            activeCommand == Protocol.Command.HEAT_ON,
                            { send(Protocol.Command.HEAT_ON) },
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                            accessibilityLabel = Protocol.Command.HEAT_ON.label(context)
                        )
                        ControlButton(
                            stringResource(R.string.heat_off_label),
                            activeCommand == Protocol.Command.HEAT_OFF,
                            { send(Protocol.Command.HEAT_OFF) },
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                            accessibilityLabel = Protocol.Command.HEAT_OFF.label(context)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1.4f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SectionLabel(stringResource(R.string.suction))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val suckCmds = listOf(
                            Protocol.Command.SUCK1, Protocol.Command.SUCK2,
                            Protocol.Command.SUCK3, Protocol.Command.SUCK4,
                            Protocol.Command.SUCK5,
                        )
                        for (cmd in suckCmds) {
                            ControlButton(
                                label = (cmd.byte - 0x50).toString(),
                                accessibilityLabel = cmd.label(context),
                                isActive = activeCommand == cmd,
                                onClick = { send(cmd) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!hasBluetoothPermission) {
                TextButton(onClick = requestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.allow_bluetooth))
                }
            }

            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { send(Protocol.Command.STOP) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.stop_device), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { stop() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        stringResource(R.string.stop_broadcast),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        tween(300), label = ""
    )
    val dot by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        tween(300), label = ""
    )

    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label
) {
    val containerColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        tween(200), label = ""
    )
    val contentColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(200), label = ""
    )

    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            selected = isActive
            contentDescription = accessibilityLabel
        },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
