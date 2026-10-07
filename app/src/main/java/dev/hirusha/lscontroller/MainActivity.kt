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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var ble: BleController

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ble = BleController(this)

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
                    ControllerScreen(ble)
                }
            }
        }
    }

    override fun onDestroy() {
        ble.stopAll()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(ble: BleController) {
    var status by remember { mutableStateOf("Idle") }
    var activeCommand by remember { mutableStateOf<Protocol.Command?>(null) }
    var selectedPrefixIndex by remember { mutableIntStateOf(-1) }
    var prefixMenuExpanded by remember { mutableStateOf(false) }

    val prefixOptions = listOf("All (cycle)") +
        Protocol.PREFIXES.map { "${it.name} (${it.deviceCount})" }
    val selectedLabel = prefixOptions[selectedPrefixIndex + 1]
    val isActive = activeCommand != null

    LaunchedEffect(Unit) {
        if (!ble.isSupported) status = "BLE advertising not supported"
    }

    fun send(cmd: Protocol.Command) {
        if (activeCommand == cmd) {
            ble.stopAll()
            activeCommand = null
            status = "Stopped"
            return
        }
        activeCommand = cmd
        if (selectedPrefixIndex == -1) {
            ble.startCycle(cmd.byte) { status = it }
        } else {
            val prefix = Protocol.PREFIXES[selectedPrefixIndex]
            ble.startSingle(prefix.bytes, cmd.byte) { status = it }
        }
    }

    fun stop() {
        ble.stopAll()
        activeCommand = null
        status = "Stopped"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
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
                "LS Controller",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            StatusChip(
                label = if (isActive) activeCommand!!.label else "Idle",
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
                label = { Text("Prefix") },
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
        SectionLabel("VIBRATION")
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
                            label = cmd.label.removePrefix("Mode "),
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
                SectionLabel("HEAT")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlButton(
                        "ON",
                        activeCommand == Protocol.Command.HEAT_ON,
                        { send(Protocol.Command.HEAT_ON) },
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                    ControlButton(
                        "OFF",
                        activeCommand == Protocol.Command.HEAT_OFF,
                        { send(Protocol.Command.HEAT_OFF) },
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1.4f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectionLabel("SUCTION")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val suckCmds = listOf(
                        Protocol.Command.SUCK1, Protocol.Command.SUCK2,
                        Protocol.Command.SUCK3, Protocol.Command.SUCK4,
                        Protocol.Command.SUCK5,
                    )
                    for (cmd in suckCmds) {
                        ControlButton(
                            label = cmd.label.removePrefix("Suck "),
                            isActive = activeCommand == cmd,
                            onClick = { send(cmd) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

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
                Text("STOP DEVICE", fontWeight = FontWeight.Bold)
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
                    "STOP BROADCAST",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
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
    modifier: Modifier = Modifier
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
        modifier = modifier,
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
