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
    var report by remember { mutableStateOf(DiagnosticReport()) }
    var busy by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }
    var endpoint by remember { mutableStateOf("") }
    var connectionState by remember {
        mutableStateOf(ConnectionState.DISCONNECTED)
    }
    var connectionMessage by remember {
        mutableStateOf("Backend belum dikonfigurasi")
    }
    var syncMessage by remember {
        mutableStateOf("Belum ada sinkronisasi")
    }
    var networkBusy by remember { mutableStateOf(false) }
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
                DiagnosticReport(error = "Akses root belum tersedia")
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
            backendRequest(base, "/health", "GET")
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

        PanelCard("KERNELSU / ROOT ENGINE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rootCheck.granted) "Root access verified"
                    else "Root access unavailable",
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
            Text(rootCheck.detail, color = Muted, fontSize = 11.sp)
        }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                onClick = { scope.launch { connectBackend() } },
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

        PanelCard("LIVE DIAGNOSTICS") {
            Text(
                if (report.success) "Last scan: ${report.capturedAt}"
                else "Diagnostics not available",
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
                report.cpu.mapIndexed { i, s -> "Policy $i" to s }
            )
            DiagnosticGroup(
                "GPU · MALI",
                report.gpu.mapIndexed { i, s -> "GPU ${i + 1}" to s }
            )
            DiagnosticGroup(
                "MEMORY",
                report.memory.mapIndexed { i, s -> "Memory ${i + 1}" to s }
            )
            DiagnosticGroup(
                "THERMAL ZONES",
                report.thermal.mapIndexed { i, value ->
                    "Zone ${i + 1}" to value
                }
            )
        }

        PanelCard("KERNELSU MODULE INSPECTOR") {
            if (report.modules.isEmpty()) {
                Text("Belum ada data modul.", color = Muted, fontSize = 11.sp)
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
                onClick = { scope.launch { syncDiagnostics() } },
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
            Text(if (busy) "SCANNING..." else "REFRESH DIAGNOSTICS")
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
        connection.setRequestProperty("Accept", "application/json")

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
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).use {
                it.readText()
            }
        }.orEmpty()

        if (code in 200..299) {
            true to if (response.isBlank()) "HTTP $code" else response.take(500)
        } else {
            false to "HTTP $code: " +
                response.ifBlank { "Permintaan ditolak" }.take(350)
        }
    } catch (e: Exception) {
        false to (e.message ?: "Koneksi backend gagal")
    } finally {
        connection?.disconnect()
    }
}

// --------------------------------------------------
// ROOT & READ-ONLY DIAGNOSTICS
// --------------------------------------------------

private fun runRootRead(command: String): String {
    return try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "Timeout"
        } else if (process.exitValue() == 0) {
            output.trim()
        } else {
            output.trim().ifBlank {
                "Perintah gagal (${process.exitValue()})"
            }
        }
    } catch (e: Exception) {
        "Error: ${e.message ?: "root command gagal"}"
    }
}

private fun checkKernelSuRoot(): RootCheck {
    return try {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            RootCheck(true, false, "Pemeriksaan root timeout")
        } else {
            val granted = process.exitValue() == 0 &&
                output.contains("uid=0")
            RootCheck(
                available = true,
                granted = granted,
                detail = if (granted) {
                    "KernelSU memberikan akses root"
                } else {
                    output.trim().ifBlank {
                        "Akses root belum diberikan"
                    }
                }
            )
        }
    } catch (e: Exception) {
        RootCheck(
            available = false,
            granted = false,
            detail = e.message ?: "Binary su tidak dapat dijalankan"
        )
    }
}

private fun collectReadOnlyDiagnostics(): DiagnosticReport {
    val kernel = runRootRead("uname -r")
    val uptimeRaw = runRootRead("cat /proc/uptime").substringBefore(" ")
    val uptime = uptimeRaw.toDoubleOrNull()?.toLong()?.let {
        val days = it / 86400
        val hours = (it % 86400) / 3600
        val minutes = (it % 3600) / 60
        "${days}d ${hours}h ${minutes}m"
    } ?: "Tidak tersedia"

    val cpu = runRootRead(
        """for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            echo "$(basename "$p"): min=$(cat "$p/scaling_min_freq" 2>/dev/null) max=$(cat "$p/scaling_max_freq" 2>/dev/null) cur=$(cat "$p/scaling_cur_freq" 2>/dev/null)"
        done"""
    )
    val gpu = runRootRead(
        """p=/sys/class/devfreq/23100000.gpu
        if [ -d "$p" ]; then
            echo "governor=$(cat "$p/governor" 2>/dev/null)"
            echo "min=$(cat "$p/min_freq" 2>/dev/null)"
            echo "max=$(cat "$p/max_freq" 2>/dev/null)"
            echo "current=$(cat "$p/cur_freq" 2>/dev/null)"
        else
            echo "GPU sysfs tidak ditemukan"
        fi"""
    )
    val memory = runRootRead(
        "grep -E '^(MemTotal|MemAvailable|MemFree|SwapTotal|SwapFree):' /proc/meminfo"
    )
    val thermal = runRootRead(
        """for z in /sys/class/thermal/thermal_zone*; do
            [ -d "$z" ] || continue
            n=$(cat "$z/type" 2>/dev/null)
            t=$(cat "$z/temp" 2>/dev/null)
            [ -n "$n" ] && echo "$n: $t"
        done | head -30"""
    )
    val modules = runRootRead(
        """for m in /data/adb/modules/*; do
            [ -d "$m" ] || continue
            if [ -f "$m/module.prop" ]; then
                n=$(grep -m1 '^name=' "$m/module.prop" | cut -d= -f2-)
                echo "$n [$(basename "$m")]"
            else
                basename "$m"
            fi
        done | head -50"""
    )

    fun lines(value: String): List<String> {
        return value.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    val valid = kernel.isNotBlank() &&
        !kernel.startsWith("Error:") &&
        !kernel.startsWith("Perintah gagal")

    return DiagnosticReport(
        success = valid,
        capturedAt = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date()),
        kernel = kernel.ifBlank { "Tidak tersedia" },
        uptime = uptime,
        cpu = lines(cpu),
        gpu = lines(gpu),
        memory = lines(memory),
        thermal = lines(thermal),
        modules = lines(modules),
        error = if (valid) "" else "Data diagnostik tidak dapat dibaca"
    )
}

// --------------------------------------------------
// REUSABLE UI COMPONENTS
// --------------------------------------------------

@Composable
private fun StatusPill(label: String) {
    val tint = when {
        label.contains("ACTIVE") ||
            label.contains("CONNECTED") ||
            label.contains("PHASE") -> Green
        label.contains("ERROR") ||
            label.contains("NOT READY") -> Amber
        else -> Muted
    }

    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PanelCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                color = Crimson,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DiagnosticGroup(
    title: String,
    values: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            title,
            color = Crimson,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        if (values.isEmpty()) {
            Text("Data tidak tersedia", color = Muted, fontSize = 10.sp)
        } else {
            values.forEach { (label, value) ->
                InfoRow(label, value.ifBlank { "—" })
            }
        }
    }
}
