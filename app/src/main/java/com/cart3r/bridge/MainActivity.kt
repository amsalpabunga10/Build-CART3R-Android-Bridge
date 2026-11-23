
package com.cart3r.bridge

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Background = Color(0xFF080809)
private val Panel = Color(0xFF171013)
private val Crimson = Color(0xFFFF334D)
private val Muted = Color(0xFFB9A9AC)
private val Green = Color(0xFF55D695)
private val Amber = Color(0xFFFFC266)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Crimson,
                    secondary = Color(0xFFFF7182),
                    background = Background,
                    surface = Panel,
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    BridgeDashboard()
                }
            }
        }
    }
}

private data class RootCheck(
    val available: Boolean,
    val granted: Boolean,
    val detail: String
)

private data class DiagnosticReport(
    val success: Boolean = false,
    val capturedAt: String = "Belum diperiksa",
    val kernel: String = "—",
    val uptime: String = "—",
    val cpu: List<String> = emptyList(),
    val gpu: List<String> = emptyList(),
    val memory: List<String> = emptyList(),
    val thermal: List<String> = emptyList(),
    val modules: List<String> = emptyList(),
    val error: String = ""
)

private enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@Composable
private fun BridgeDashboard() {
    var rootCheck by remember {
        mutableStateOf(RootCheck(false, false, "Belum diperiksa"))
    }

    var report by remember {
        mutableStateOf(DiagnosticReport())
    }

    var busy by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }

    var endpoint by remember {
        mutableStateOf("")
    }

    var connectionState by remember {
        mutableStateOf(ConnectionState.DISCONNECTED)
    }

    var connectionMessage by remember {
        mutableStateOf("Backend belum dikonfigurasi")
    }

    var syncMessage by remember {
        mutableStateOf("Belum ada sinkronisasi")
    }

    var networkBusy by remember {
        mutableStateOf(false)
    }

    var audit by remember {
        mutableStateOf(listOf("NOVVA BRIDGE dimulai"))
    }

    val scope = rememberCoroutineScope()

    val model = Build.MODEL ?: "Unknown"
    val manufacturer = Build.MANUFACTURER ?: "Unknown"
    val device = Build.DEVICE ?: "Unknown"
    val product = Build.PRODUCT ?: "Unknown"
    val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
    val sdk = Build.VERSION.SDK_INT

    val expectedDevice =
        model.equals("RMX5303", true) ||
        product.contains("RMX5303", true) ||
        device.contains("RMX5303", true)

    fun addAudit(message: String) {
        val stamp = SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        audit = (listOf("$stamp · $message") + audit).take(15)
    }

    suspend fun refreshDiagnostics() {
        busy = true

        val result = withContext(Dispatchers.IO) {
            val root = checkKernelSuRoot()

            root to if (root.granted) {
                collectReadOnlyDiagnostics()
            } else {
                DiagnosticReport(
                    error = "Akses root belum tersedia"
                )
            }
        }

        rootCheck = result.first
        report = result.second

        addAudit(
            if (report.success) {
                "Pemeriksaan diagnostik berhasil"
            } else {
                "Pemeriksaan diagnostik gagal"
            }
        )

        busy = false
    }

    suspend fun connectBackend() {
        val base = endpoint.trim().trimEnd('/')

        if (!base.startsWith("https://")) {
            connectionState = ConnectionState.ERROR
            connectionMessage = "Gunakan alamat HTTPS yang valid"
            addAudit("Koneksi ditolak: endpoint tidak aman")
            return
        }

        if (base.contains("YOUR-WORKER") || base.isBlank()) {
            connectionState = ConnectionState.ERROR
            connectionMessage = "Masukkan URL Cloudflare Worker"
            return
        }

        networkBusy = true
        connectionState = ConnectionState.CONNECTING
        connectionMessage = "Menghubungkan ke backend..."

        val result = withContext(Dispatchers.IO) {
            backendRequest(
                base = base,
                path = "/health",
                method = "GET"
            )
        }

        networkBusy = false

        if (result.first) {
            connectionState = ConnectionState.CONNECTED
            connectionMessage = "Backend berhasil dijangkau"
            addAudit("Backend berhasil dihubungi")
        } else {
            connectionState = ConnectionState.ERROR
            connectionMessage = result.second
            addAudit("Koneksi backend gagal")
        }
    }

    suspend fun syncDiagnostics() {
        val base = endpoint.trim().trimEnd('/')

        if (!base.startsWith("https://") ||
            base.contains("YOUR-WORKER") ||
            base.isBlank()
        ) {
            syncMessage = "Konfigurasikan URL backend terlebih dahulu"
            return
        }

        if (!report.success) {
            syncMessage = "Jalankan diagnostik terlebih dahulu"
            return
        }

        networkBusy = true
        syncMessage = "Mengirim diagnostik..."

        val payload = JSONObject().apply {
            put("device", JSONObject().apply {
                put("manufacturer", manufacturer)
                put("model", model)
                put("device", device)
                put("product", product)
                put("android", androidVersion)
                put("sdk", sdk)
            })

            put("root", JSONObject().apply {
                put("available", rootCheck.available)
                put("granted", rootCheck.granted)
            })

            put("diagnostics", JSONObject().apply {
                put("capturedAt", report.capturedAt)
                put("kernel", report.kernel)
                put("uptime", report.uptime)
                put("cpu", JSONArray(report.cpu))
                put("gpu", JSONArray(report.gpu))
                put("memory", JSONArray(report.memory))
                put("thermal", JSONArray(report.thermal))
                put("modules", JSONArray(report.modules))
            })
        }

        val result = withContext(Dispatchers.IO) {
            backendRequest(
                base = base,
                path = "/api/diagnostics",
                method = "POST",
                body = payload.toString()
            )
        }

        networkBusy = false

        if (result.first) {
            syncMessage = "Diagnostik berhasil dikirim"
            addAudit("Diagnostik berhasil disinkronkan")
        } else {
            syncMessage = result.second
            addAudit("Sinkronisasi diagnostik gagal")
        }
    }

    LaunchedEffect(refreshToken) {
        refreshDiagnostics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // HEADER

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "NOVVA",
                    color = Crimson,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    "DEVELOPER · NOVVAAA",
                    color = Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "BRIDGE",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    "PHASE 4 · AI BRIDGE",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp
                )
            }

            StatusPill("PHASE 4")
        }

        // DEVICE

        PanelCard("DEVICE OVERVIEW") {
            Text(
                "$manufacturer $model",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (expectedDevice) {
                    "Target device identified"
                } else {
                    "Device detected · target not confirmed"
                },
                color = if (expectedDevice) Green else Amber,
                fontSize = 12.sp
            )

            InfoRow("Model", model)
            InfoRow("Device", device)
            InfoRow("Product", product)
            InfoRow("Android", "$androidVersion (API $sdk)")
            InfoRow("Build", Build.DISPLAY ?: "Unknown")
        }

        // ROOT

        PanelCard("KERNELSU / ROOT ENGINE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rootCheck.granted) {
                        "Root access verified"
                    } else {
                        "Root access unavailable"
                    },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )

                if (busy) {
                    CircularProgressIndicator(color = Crimson)
                } else {
                    StatusPill(
                        if (rootCheck.granted) "ROOT ACTIVE"
                        else "NOT READY"
                    )
                }
            }

            Text(
                rootCheck.detail,
                color = Muted,
                fontSize = 11.sp
            )
        }

        // AI CONNECTION

        PanelCard("AI BRIDGE · CONNECTION") {
            Text(
                "Backend endpoint",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = endpoint,
                onValueChange = {
                    endpoint = it
                    connectionState = ConnectionState.DISCONNECTED
                    connectionMessage = "Endpoint berubah"
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "https://your-worker.workers.dev",
                        color = Muted
                    )
                },
                singleLine = true,
                enabled = !networkBusy,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Crimson,
                    unfocusedBorderColor = Muted,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Crimson
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONNECTION STATUS",
                    modifier = Modifier.weight(1f),
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                StatusPill(
                    when (connectionState) {
                        ConnectionState.CONNECTED -> "CONNECTED"
                        ConnectionState.CONNECTING -> "CONNECTING"
                        ConnectionState.ERROR -> "ERROR"
                        ConnectionState.DISCONNECTED -> "OFFLINE"
                    }
                )
            }

            Text(
                connectionMessage,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> Green
                    ConnectionState.ERROR -> Amber
                    else -> Muted
                },
                fontSize = 11.sp
            )

            Button(
                onClick = {
                    scope.launch { connectBackend() }
                },
                enabled = !networkBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Crimson
                )
            ) {
                Text("TEST CONNECTION")
            }

            Text(
                "API key tidak disimpan di aplikasi. Koneksi memerlukan backend HTTPS yang telah dikonfigurasi.",
                color = Muted,
                fontSize = 10.sp
            )
        }

        // DIAGNOSTICS

        PanelCard("LIVE DIAGNOSTICS") {
            Text(
                if (report.success) {
                    "Last scan: ${report.capturedAt}"
                } else {
                    "Diagnostics not available"
                },
                color = if (report.success) Green else Amber,
                fontSize = 11.sp
            )

            if (report.error.isNotBlank()) {
                Text(report.error, color = Amber, fontSize = 11.sp)
            }

            DiagnosticGroup(
                "KERNEL & UPTIME",
                listOf(
                    "Kernel" to report.kernel,
                    "Uptime" to report.uptime
                )
            )

            DiagnosticGroup(
                "CPU",
                report.cpu.mapIndexed { i, s ->
                    "Policy $i" to s
                }
            )

            DiagnosticGroup(
                "GPU · MALI",
                report.gpu.mapIndexed { i, s ->
                    "GPU ${i + 1}" to s
                }
            )

            DiagnosticGroup(
                "MEMORY",
                report.memory.mapIndexed { i, s ->
                    "Memory ${i + 1}" to s
                }
            )

            DiagnosticGroup(
                "THERMAL ZONES",
                report.thermal.mapIndexed { i, value ->
                    "Zone ${i + 1}" to value
                }
            )
        }

        // MODULES

        PanelCard("KERNELSU MODULE INSPECTOR") {
            if (report.modules.isEmpty()) {
                Text(
                    "Belum ada data modul.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            report.modules.forEach {
                Text(it, color = Color.White, fontSize = 11.sp)
            }

            Text(
                "Mode pemeriksaan hanya membaca informasi modul.",
                color = Muted,
                fontSize = 10.sp
            )
        }

        // SYNC

        PanelCard("DEVICE SYNC") {
            Text(
                "Kirim hasil diagnostik perangkat ke backend NOVVA BRIDGE.",
                color = Muted,
                fontSize = 11.sp
            )

            Text(
                syncMessage,
                color = if (syncMessage.contains("berhasil")) Green else Amber,
                fontSize = 11.sp
            )

            Button(
                onClick = {
                    scope.launch { syncDiagnostics() }
                },
                enabled = !networkBusy &&
                    connectionState == ConnectionState.CONNECTED &&
                    report.success,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Crimson
                )
            ) {
                Text("SYNC DIAGNOSTICS")
            }
        }

        // CONTROLLED ACTIONS

        PanelCard("SYSTEM CONTROLLER · APPROVAL") {
            Text(
                "Developer: Novvaaa",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "AI dapat menganalisis data diagnostik yang dikirim. " +
                "Fitur perubahan sistem belum diaktifkan pada build ini.",
                color = Muted,
                fontSize = 11.sp
            )

            Text(
                "Setiap tindakan WRITE di masa depan wajib memiliki daftar izin, " +
                "target yang jelas, penjelasan dampak, persetujuan lokal, " +
                "dan mekanisme pemulihan.",
                color = Amber,
                fontSize = 10.sp
            )
        }

        // AUDIT

        PanelCard("AUDIT LOG") {
            audit.forEach {
                Text("• $it", color = Muted, fontSize = 11.sp)
            }
        }

        Button(
            onClick = { refreshToken++ },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Crimson
            )
        ) {
            Text(
                if (busy) "SCANNING..." else "REFRESH DIAGNOSTICS"
            )
        }

        Text(
            "NOVVA BRIDGE · DEVELOPER NOVVAAA · PHASE 4",
            color = Muted,
            fontSize = 9.sp
        )

        Spacer(Modifier.height(8.dp))
    }
}

// --------------------------------------------------
// BACKEND CONNECTION
// --------------------------------------------------

private fun backendRequest(
    base: String,
    path: String,
    method: String,
    body: String? = null
): Pair<Boolean, String> {
    var connection: HttpURLConnection? = null

    return try {
        val url = URL(base.trimEnd('/') + path)

        if (url.protocol != "https") {
            return false to "HTTPS wajib digunakan"
        }

        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty(
            "Accept",
            "application/json"
        )

        if (method == "POST" && body != null) {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }
        }

        val code = connection.responseCode

        val stream = if (code in 200..299) {
  
