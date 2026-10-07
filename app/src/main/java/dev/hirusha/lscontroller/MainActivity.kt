package dev.hirusha.lscontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var ble: BleController

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = BleController(this)

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerScreen(ble)
                }
            }
        }
    }

    @Composable
    private fun dynamicColorScheme(): ColorScheme {
        return if (Build.VERSION.SDK_INT >= 31) {
            dynamicLightColorScheme(this)
        } else {
            lightColorScheme()
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
    var selectedPrefixIndex by remember { mutableIntStateOf(-1) } // -1 = cycle all
    var prefixMenuExpanded by remember { mutableStateOf(false) }

    val prefixOptions = listOf("All (cycle)") + Protocol.PREFIXES.map { "${it.name} (${it.deviceCount})" }
    val selectedLabel = prefixOptions[selectedPrefixIndex + 1]

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LS Controller") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!ble.isSupported) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "BLE advertising not supported on this device",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeCommand != null)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (activeCommand != null) activeCommand!!.label else "Idle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (activeCommand != null) {
                        FilledTonalButton(onClick = { stop() }) {
                            Text("Stop")
                        }
                    }
                }
            }

            // Prefix selector
            ExposedDropdownMenuBox(
                expanded = prefixMenuExpanded,
                onExpandedChange = { prefixMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Prefix") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prefixMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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

            // Vibration modes
            SectionHeader("Vibration")
            CommandGrid(
                commands = listOf(
                    Protocol.Command.MODE1, Protocol.Command.MODE2, Protocol.Command.MODE3,
                    Protocol.Command.MODE4, Protocol.Command.MODE5, Protocol.Command.MODE6,
                    Protocol.Command.MODE7, Protocol.Command.MODE8, Protocol.Command.MODE9,
                ),
                activeCommand = activeCommand,
                onSend = { send(it) }
            )

            // Heating
            SectionHeader("Heating")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton(Protocol.Command.HEAT_ON, activeCommand, Modifier.weight(1f)) { send(it) }
                CommandButton(Protocol.Command.HEAT_OFF, activeCommand, Modifier.weight(1f)) { send(it) }
            }

            // Suction
            SectionHeader("Suction")
            CommandGrid(
                commands = listOf(
                    Protocol.Command.SUCK1, Protocol.Command.SUCK2, Protocol.Command.SUCK3,
                    Protocol.Command.SUCK4, Protocol.Command.SUCK5,
                ),
                activeCommand = activeCommand,
                onSend = { send(it) }
            )

            // Stop device (broadcasts stop command 0x00)
            Button(
                onClick = { send(Protocol.Command.STOP) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("STOP DEVICE", style = MaterialTheme.typography.titleMedium)
            }

            // Stop broadcasting (just kills the radio, no command sent)
            OutlinedButton(
                onClick = { stop() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Broadcasting")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun CommandGrid(
    commands: List<Protocol.Command>,
    activeCommand: Protocol.Command?,
    onSend: (Protocol.Command) -> Unit
) {
    val columns = 3
    val rows = commands.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (cmd in row) {
                    CommandButton(cmd, activeCommand, Modifier.weight(1f), onSend)
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CommandButton(
    cmd: Protocol.Command,
    activeCommand: Protocol.Command?,
    modifier: Modifier = Modifier,
    onSend: (Protocol.Command) -> Unit
) {
    val isActive = activeCommand == cmd
    if (isActive) {
        Button(
            onClick = { onSend(cmd) },
            modifier = modifier.height(48.dp),
        ) {
            Text(cmd.label)
        }
    } else {
        OutlinedButton(
            onClick = { onSend(cmd) },
            modifier = modifier.height(48.dp),
        ) {
            Text(cmd.label)
        }
    }
}
