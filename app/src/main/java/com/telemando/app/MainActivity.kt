package com.telemando.app

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryzen.tvremote.protocol.AndroidTvRemoteV2Client
import com.ryzen.tvremote.protocol.ConnectionStatus
import com.ryzen.tvremote.protocol.DiscoveryManager
import com.ryzen.tvremote.protocol.DiscoveredDevice
import com.ryzen.tvremote.protocol.KeyManagerHelper
import com.ryzen.tvremote.protocol.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Original enchanted-library palette: parchment by day, moonlit ink by night.
private val Night = Color(0xFF1C2235)
private val Ivory = Color(0xFFFFFCF5)
private val Gold = Color(0xFFE9CE8A)
private data class MagicPalette(
    val ink: Color, val muted: Color, val background: Color, val accent: Color,
    val softAccent: Color, val surface: Color, val error: Color
)
private val DayPalette = MagicPalette(Night, Color(0xFF675F58), Color(0xFFF5EDDF),
    Color(0xFF82571F), Color(0xFFEBDFC9), Ivory, Color(0xFFFFECE9))
private val NightPalette = MagicPalette(Ivory, Color(0xFFD1C4AB), Color(0xFF141A28),
    Gold, Color(0xFF34354A), Color(0xFF262D3E), Color(0xFF493641))
private val LocalMagicPalette = staticCompositionLocalOf { DayPalette }
private val Ink: Color @Composable get() = LocalMagicPalette.current.ink
private val Muted: Color @Composable get() = LocalMagicPalette.current.muted
private val Canvas: Color @Composable get() = LocalMagicPalette.current.background
private val Teal: Color @Composable get() = LocalMagicPalette.current.accent
private val SoftTeal: Color @Composable get() = LocalMagicPalette.current.softAccent
private val White: Color @Composable get() = LocalMagicPalette.current.surface

internal enum class TextMode(val label: String, val detail: String) {
    FAST("Rápido · YouTube", "Enviar la frase de una vez"),
    PACED("Pausado · Netflix", "Enviar letra a letra sin perder caracteres")
}

internal fun parseTextMode(saved: String?): TextMode =
    TextMode.entries.firstOrNull { it.name == saved } ?: TextMode.FAST

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0xFFF5EDDF.toInt(), 0xFF141A28.toInt()),
            navigationBarStyle = SystemBarStyle.auto(0xFFF5EDDF.toInt(), 0xFF141A28.toInt())
        )
        setContent {
            val scope = rememberCoroutineScope()
            val discovery = remember { DiscoveryManager(applicationContext) }
            val remote = remember { AndroidTvRemoteV2Client(applicationContext, scope) }
            val prefs = remember { getSharedPreferences("telemando_device", Context.MODE_PRIVATE) }
            val devices by discovery.discoveredDevices.collectAsStateWithLifecycle()
            val scanning by discovery.isScanning.collectAsStateWithLifecycle()
            val status by remote.status.collectAsStateWithLifecycle()

            var selectedName by remember { mutableStateOf(prefs.getString("name", null) ?: "TCL 43C61B") }
            var lastHost by remember { mutableStateOf(prefs.getString("host", null) ?: "") }
            var autoReconnect by remember { mutableStateOf(lastHost.isNotEmpty()) }
            var retryCount by remember { mutableIntStateOf(0) }
            var tab by remember { mutableIntStateOf(0) }
            var searchText by remember { mutableStateOf("") }
            var sending by remember { mutableStateOf(false) }
            var textMode by remember {
                mutableStateOf(parseTextMode(prefs.getString("text_mode", null)))
            }
            // Each tab opens from the top, even if the previous tab was scrolled down.
            val scroll = remember(tab, status is ConnectionStatus.Connected) { ScrollState(0) }

            LaunchedEffect(Unit) {
                if (lastHost.isNotEmpty()) remote.connect(lastHost) else discovery.startDiscovery()
            }
            LaunchedEffect(status) {
                if (status is ConnectionStatus.Connected) {
                    lastHost = (status as ConnectionStatus.Connected).host
                    retryCount = 0
                    prefs.edit().putString("host", lastHost).putString("name", selectedName).apply()
                }
            }
            LaunchedEffect(status, autoReconnect, lastHost) {
                if (status is ConnectionStatus.Disconnected && autoReconnect &&
                    lastHost.isNotEmpty() && retryCount < 3) {
                    delay(listOf(1500L, 3000L, 6000L)[retryCount])
                    if (status is ConnectionStatus.Disconnected && autoReconnect) {
                        retryCount++
                        remote.connect(lastHost)
                    }
                }
            }
            var leftForegroundAt by remember { mutableLongStateOf(0L) }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                leftForegroundAt = SystemClock.elapsedRealtime()
                discovery.stopDiscovery() // Do not hold a Wi-Fi multicast lock in the background.
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (lastHost.isEmpty() && !discovery.isScanning.value) discovery.startDiscovery()
                val elapsed = leftForegroundAt.takeIf { it != 0L }
                    ?.let { SystemClock.elapsedRealtime() - it }
                if (shouldReconnectOnResume(remote.status.value, elapsed,
                        autoReconnect, lastHost.isNotEmpty())) {
                    retryCount = 0
                    val host = lastHost
                    scope.launch { remote.connect(host) }
                }
                leftForegroundAt = 0L
            }
            DisposableEffect(Unit) {
                onDispose { discovery.stopDiscovery(); remote.disconnect() }
            }

            fun connect(host: String, name: String) {
                selectedName = name
                lastHost = host
                autoReconnect = true
                retryCount = 0
                discovery.stopDiscovery()
                if (KeyManagerHelper.isHostPaired(applicationContext, host)) {
                    scope.launch { remote.connect(host) }
                } else remote.forcePair(host)
            }

            fun changeTv() {
                autoReconnect = false
                lastHost = ""
                prefs.edit().remove("host").remove("name").apply()
                remote.disconnect()
                discovery.startDiscovery()
            }

            val connected = status is ConnectionStatus.Connected
            val dark = isSystemInDarkTheme()
            CompositionLocalProvider(LocalMagicPalette provides if (dark) NightPalette else DayPalette) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Teal, onPrimary = Night,
                background = Canvas, surface = White, onSurface = Ink,
                secondary = Gold, surfaceVariant = SoftTeal)
            else lightColorScheme(primary = Teal, onPrimary = Ivory,
                background = Canvas, surface = White, onSurface = Ink,
                secondary = Night, surfaceVariant = SoftTeal)) {
                Scaffold(containerColor = Canvas, bottomBar = {
                    if (connected) NavigationBar(containerColor = Night) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Night, selectedTextColor = Gold,
                            indicatorColor = Gold, unselectedIconColor = Gold.copy(alpha = .75f),
                            unselectedTextColor = Gold.copy(alpha = .75f))
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = navColors,
                            icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                            label = { Text("Mando") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = navColors,
                            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                            label = { Text("Buscar") })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, colors = navColors,
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Conexión") })
                    }
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Teal, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("TELEMANDO", color = Teal, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        }
                        Text("Un poco de magia en tu salón", fontFamily = FontFamily.Serif,
                            color = Ink, fontSize = 24.sp, lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold)
                        StatusCard(selectedName, status,
                            retrying = autoReconnect && lastHost.isNotEmpty() && retryCount < 3,
                            hasSavedHost = lastHost.isNotEmpty())

                        if (!connected) {
                            if (status is ConnectionStatus.Disconnected && lastHost.isNotEmpty() &&
                                autoReconnect && retryCount >= 3) {
                                Card(colors = CardDefaults.cardColors(containerColor = SoftTeal),
                                    shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("Conexión interrumpida", fontWeight = FontWeight.SemiBold)
                                        Text("Comprueba que la TV está encendida y vuelve a intentarlo.",
                                            color = Muted, fontSize = 13.sp)
                                        TextButton(onClick = {
                                            retryCount = 0
                                            val host = lastHost
                                            scope.launch { remote.connect(host) }
                                        }) { Text("Reintentar conexión") }
                                        TextButton(onClick = ::changeTv) { Text("Buscar otra TV") }
                                    }
                                }
                            }
                            if (status is ConnectionStatus.Error) {
                                Card(colors = CardDefaults.cardColors(containerColor = LocalMagicPalette.current.error),
                                    shape = RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text((status as ConnectionStatus.Error).message, color = Ink, fontSize = 13.sp)
                                        if (lastHost.isNotEmpty()) {
                                            TextButton(onClick = { scope.launch { remote.connect(lastHost) } }) {
                                                Text("Reintentar conexión")
                                            }
                                            TextButton(onClick = { remote.forcePair(lastHost) }) {
                                                Text("Volver a emparejar")
                                            }
                                        }
                                        TextButton(onClick = ::changeTv) { Text("Buscar otra TV") }
                                    }
                                }
                            }
                            ConnectionSetup(devices, scanning,
                                onRescan = { discovery.stopDiscovery(); discovery.startDiscovery() },
                                onConnect = ::connect)
                            if (status is ConnectionStatus.NeedsPairing) {
                                PairingCard { pin -> scope.launch { remote.finishPairing(pin) } }
                            }
                        } else when (tab) {
                            0 -> RemotePane { remote.sendKey(it) }
                            1 -> SearchPane(searchText, { searchText = it }, textMode, { mode ->
                                textMode = mode
                                prefs.edit().putString("text_mode", mode.name).apply()
                            }, sending, onSend = {
                                sending = true
                                val query = searchText
                                val mode = textMode
                                scope.launch {
                                    try {
                                        when (mode) {
                                            TextMode.FAST -> remote.sendText(query)
                                            TextMode.PACED -> remote.sendTextPaced(query)
                                        }
                                    } finally { sending = false }
                                }
                            })
                            else -> ConnectionPane(selectedName, lastHost, onRetry = {
                                scope.launch { remote.connect(lastHost) }
                            }, onChange = ::changeTv)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatusCard(name: String, status: ConnectionStatus,
                       retrying: Boolean, hasSavedHost: Boolean) {
    val connected = status is ConnectionStatus.Connected
    val label = when (status) {
        is ConnectionStatus.Connected -> "Conectada · ${status.host}"
        is ConnectionStatus.Connecting -> "Conectando con la TV…"
        is ConnectionStatus.NeedsPairing -> "Introduce el código de la TV"
        is ConnectionStatus.Error -> "No se pudo conectar"
        is ConnectionStatus.Disconnected -> if (retrying) "Reconectando con la TV…"
            else if (hasSavedHost) "TV desconectada" else "Elige tu televisor para empezar"
    }
    Card(colors = CardDefaults.cardColors(containerColor = Night),
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .65f)),
        modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            EnchantedStars(Modifier.matchParentSize())
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFF343A50), CircleShape),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Tv, null, tint = Gold) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Ivory, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(label, color = if (connected) Gold else Color(0xFFE2D6BD),
                    fontSize = 12.sp)
            }
            Box(Modifier.size(9.dp).background(if (connected) Gold
                else Color(0xFFE4A977), CircleShape))
            }
        }
    }
}

@Composable
private fun EnchantedStars(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Tiny deterministic stars: original vector decoration, not franchise artwork.
        for ((x, y, radius) in listOf(Triple(.81f, .24f, 2.2f), Triple(.92f, .63f, 1.7f),
            Triple(.72f, .78f, 1.5f), Triple(.96f, .18f, 1.2f))) {
            drawCircle(Gold.copy(alpha = .5f), radius = radius.dp.toPx(),
                center = Offset(size.width * x, size.height * y))
        }
    }
}

@Composable
private fun ConnectionSetup(devices: List<DiscoveredDevice>, scanning: Boolean,
                            onRescan: () -> Unit, onConnect: (String, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    val compact = isCompactLayout()
    SectionTitle("Conectar TV", "CONFIGURACIÓN")
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compact) {
                Text("Dispositivos cercanos", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRescan) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text("Buscar")
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dispositivos cercanos", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRescan) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text("Buscar")
                }
            }
            if (devices.isEmpty()) Text(if (scanning) "Buscando televisores Google TV…"
                else "No hay televisores detectados. Prueba con la IP.", color = Muted, fontSize = 13.sp)
            devices.forEach { device ->
                Row(Modifier.fillMaxWidth().clickable { onConnect(device.host, device.name) }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tv, null, tint = Teal); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.Medium)
                        Text(device.host, color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
            HorizontalDivider(color = Canvas)
            OutlinedTextField(value = ip, onValueChange = { ip = it.take(15) },
                label = { Text("IP de la TV · p. ej. 192.168.1.42") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            val validIp = ip.split('.').let { parts -> parts.size == 4 &&
                parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) &&
                    (part.toIntOrNull() ?: 256) in 0..255 } }
            Button(onClick = { onConnect(ip, "TCL 43C61B") }, enabled = validIp,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Conectar por IP")
            }
        }
    }
}

@Composable
private fun PairingCard(onPair: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SoftTeal)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Emparejar con la TV", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Introduce el código que aparece en la TCL.", color = Muted, fontSize = 13.sp)
            OutlinedTextField(value = pin, onValueChange = { value ->
                pin = value.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.uppercase().take(6)
            }, label = { Text("Código de 6 caracteres") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Button(onClick = { onPair(pin) }, enabled = pin.length == 6,
                modifier = Modifier.fillMaxWidth()) { Text("Confirmar código") }
        }
    }
}

@Composable
private fun RemotePane(onKey: (RemoteKey) -> Unit) {
    val compact = isCompactLayout()
    val keySize = if (compact) 56.dp else 68.dp
    val okSize = if (compact) 64.dp else 76.dp
    SectionTitle("Mando", "NAVEGACIÓN")
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton("Arriba", Icons.Rounded.KeyboardArrowUp, keySize) { onKey(RemoteKey.DPAD_UP) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyButton("Izquierda", Icons.AutoMirrored.Rounded.KeyboardArrowLeft, keySize) { onKey(RemoteKey.DPAD_LEFT) }
                FilledIconButton(onClick = { onKey(RemoteKey.DPAD_CENTER) }, modifier = Modifier.size(okSize),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Teal)) {
                    Text("OK", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                KeyButton("Derecha", Icons.AutoMirrored.Rounded.KeyboardArrowRight, keySize) { onKey(RemoteKey.DPAD_RIGHT) }
            }
            KeyButton("Abajo", Icons.Rounded.KeyboardArrowDown, keySize) { onKey(RemoteKey.DPAD_DOWN) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onKey(RemoteKey.BACK) }, modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, if (compact) "Atrás" else null, Modifier.size(21.dp))
                    if (!compact) { Spacer(Modifier.width(5.dp)); Text("Atrás") }
                }
                OutlinedButton(onClick = { onKey(RemoteKey.HOME) }, modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(4.dp)) {
                    Icon(Icons.Rounded.Home, if (compact) "Inicio" else null, Modifier.size(21.dp))
                    if (!compact) { Spacer(Modifier.width(5.dp)); Text("Inicio") }
                }
            }
        }
    }
    SectionTitle("Sonido", "VOLUMEN")
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SoftTeal)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(RemoteKey.VOLUME_DOWN, Icons.AutoMirrored.Rounded.VolumeDown, "Bajar volumen"),
                Triple(RemoteKey.VOLUME_MUTE, Icons.AutoMirrored.Rounded.VolumeOff, "Silenciar"),
                Triple(RemoteKey.VOLUME_UP, Icons.AutoMirrored.Rounded.VolumeUp, "Subir volumen")).forEach { (key, icon, label) ->
                OutlinedButton(onClick = { onKey(key) }, modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(4.dp)) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchPane(text: String, onTextChange: (String) -> Unit, mode: TextMode,
                       onModeChange: (TextMode) -> Unit, sending: Boolean, onSend: () -> Unit) {
    SectionTitle("Escribir en la TV", "BÚSQUEDA")
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Abre la búsqueda en la TV con el mando; después escribe aquí.",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            OutlinedTextField(value = text, onValueChange = onTextChange,
                label = { Text("¿Qué quieres buscar?") }, singleLine = true,
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { onTextChange("") },
                    enabled = !sending) { Icon(Icons.Rounded.Close, "Borrar texto") } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Text("Método de escritura", color = Ink, fontWeight = FontWeight.SemiBold)
            TextMode.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !sending) { onModeChange(option) },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == option, onClick = { onModeChange(option) },
                        enabled = !sending)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(option.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(option.detail, fontSize = 12.sp, color = Muted)
                    }
                }
            }
            Button(onClick = onSend, enabled = text.isNotBlank() && !sending,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (sending) "Enviando…" else "Enviar a la TV")
            }
            Text("En Prime Video utiliza el mando para recorrer el teclado en pantalla. " +
                "Si reenvías una frase, borra antes el campo de la TV para no duplicarla.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ConnectionPane(name: String, host: String, onRetry: () -> Unit, onChange: () -> Unit) {
    SectionTitle("Conexión", "AJUSTES")
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Dirección local: $host", color = Muted, fontSize = 13.sp)
            Text("Telemando recordará esta TV la próxima vez. Cambiar de TV no elimina el emparejamiento.",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Reconectar") }
            TextButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) { Text("Cambiar de TV") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(title, color = Ink, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold, fontSize = 25.sp)
    }
}

@Composable
private fun isCompactLayout(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    return with(density) { widthPx.toDp() < 360.dp || fontScale >= 1.3f }
}

@Composable
private fun KeyButton(label: String, icon: ImageVector, size: androidx.compose.ui.unit.Dp,
                      action: () -> Unit) {
    OutlinedIconButton(onClick = action, modifier = Modifier.size(size),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Teal.copy(alpha = .45f))) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
    }
}
