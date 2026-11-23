package com.cart3r.bridge

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
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
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Crimson,
                secondary = Color(0xFFFF7182),
                background = Background,
                surface = Panel,
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    BridgeDashboard()
                }
            }
        }
    }
}

private data class RootCheck(val available: Boolean, val granted: Boolean, val detail: String)
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

@androidx.compose.runtime.Composable
private fun BridgeDashboard() {
    var rootCheck by remember { mutableStateOf(RootCheck(false, false, "Belum diperiksa")) }
    var report by remember { mutableStateOf(DiagnosticReport()) }
    var busy by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }
    var audit by remember { mutableStateOf(listOf("Aplikasi dimulai")) }

    val model = Build.MODEL ?: "Unknown"
    val manufacturer = Build.MANUFACTURER ?: "Unknown"
    val device = Build.DEVICE ?: "Unknown"
    val product = Build.PRODUCT ?: "Unknown"
    val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
    val sdk = Build.VERSION.SDK_INT
    val expectedDevice = model.equals("RMX5303", true) || product.contains("RMX5303", true) || device.contains("RMX5303", true)

    suspend fun refreshDiagnostics() {
        busy = true
        val result = withContext(Dispatchers.IO) {
            val root = checkKernelSuRoot()
            root to if (root.granted) collectReadOnlyDiagnostics() else DiagnosticReport(error = "Akses root belum tersedia")
        }
        rootCheck = result.first
        report = result.second
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        audit = (listOf("$stamp · Pemeriksaan diagnostik ${if (report.success) "berhasil" else "gagal"}") + audit).take(12)
        busy = false
    }

    LaunchedEffect(refreshToken) { refreshDiagnostics() }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("CART3R", color = Crimson, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("ANDROID BRIDGE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("PHASE 2 · ROOT ENGINE", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
            StatusPill("PHASE 2")
        }

        PanelCard("DEVICE OVERVIEW") {
            Text("$manufacturer $model", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(if (expectedDevice) "Target device identified" else "Device detected · target model not confirmed", color = if (expectedDevice) Green else Amber, fontSize = 12.sp)
            InfoRow("Model", model)
            InfoRow("Device", device)
            InfoRow("Product", product)
            InfoRow("Android", "$androidVersion (API $sdk)")
            InfoRow("Build", Build.DISPLAY ?: "Unknown")
        }

        PanelCard("KERNELSU / ROOT ENGINE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (rootCheck.granted) "Root access verified" else "Root access unavailable", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (busy) CircularProgressIndicator(color = Crimson) else StatusPill(if (rootCheck.granted) "ROOT ACTIVE" else "NOT READY")
            }
            Text(rootCheck.detail, color = Muted, fontSize = 11.sp)
            Text("Root command terbatas pada pemeriksaan identitas dan pembacaan data diagnostik.", color = Muted, fontSize = 10.sp)
        }

        PanelCard("LIVE DIAGNOSTICS") {
            Text(if (report.success) "Last scan: ${report.capturedAt}" else "Diagnostics not available", color = if (report.success) Green else Amber, fontSize = 11.sp)
            if (report.error.isNotBlank()) Text(report.error, color = Amber, fontSize = 11.sp)
            DiagnosticGroup("KERNEL & UPTIME", listOf("Kernel" to report.kernel, "Uptime" to report.uptime))
            DiagnosticGroup("CPU", report.cpu.mapIndexed { i, s -> "Policy ${i}" to s })
            DiagnosticGroup("GPU · MALI", report.gpu.mapIndexed { i, s -> "GPU ${i + 1}" to s })
            DiagnosticGroup("MEMORY", report.memory.mapIndexed { i, s -> "Memory ${i + 1}" to s })
            DiagnosticGroup("THERMAL ZONES", report.thermal.mapIndexed { i, value -> "Zone ${i + 1}" to value })
        }

        PanelCard("KERNELSU MODULE INSPECTOR") {
            if (report.modules.isEmpty()) Text("Belum ada data modul. Refresh diagnostik saat root aktif.", color = Muted, fontSize = 11.sp)
            report.modules.forEach { Text(it, color = Color.White, fontSize = 11.sp) }
            Text("Inspector hanya membaca /data/adb/modules dan module.prop. Tidak mengaktifkan, menonaktifkan, atau menghapus modul.", color = Muted, fontSize = 10.sp)
        }

        PanelCard("AUDIT LOG") {
            audit.forEach { Text("• $it", color = Muted, fontSize = 11.sp) }
        }

        Button(
            onClick = { refreshToken++ },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Crimson)
        ) { Text(if (busy) "SCANNING…" else "REFRESH DIAGNOSTICS") }
        Text("SECURITY: fixed read-only commands only · no arbitrary shell input · no network endpoint · no system writes", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@androidx.compose.runtime.Composable
private fun PanelCard(title: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(label: String) {
    Box(Modifier.background(Color(0xFF351019), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp)) {
        Text(label, color = Color(0xFFFF8290), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.runtime.Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value.ifBlank { "Unknown" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@androidx.compose.runtime.Composable
private fun DiagnosticGroup(title: String, values: List<Pair<String, String>>) {
    if (values.isNotEmpty()) {
        Text(title, color = Color(0xFFFF8290), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        values.forEach { (label, value) -> InfoRow(label, value) }
    }
}

private fun checkKernelSuRoot(): RootCheck = runFixedRootCommand("id") { output, code ->
    val granted = code == 0 && output.contains("uid=0")
    RootCheck(true, granted, output.ifBlank { "su tidak mengembalikan output" })
} ?: RootCheck(false, false, "Tidak dapat menjalankan pemeriksaan root")

private fun collectReadOnlyDiagnostics(): DiagnosticReport {
    val script = """
        echo 'KERNEL:'@(uname -r)
        echo 'UPTIME:'@(cut -d' ' -f1 /proc/uptime 2>/dev/null)
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
          [ -d "@p" ] || continue
          n=@{p##*policy}
          g=@(cat "@p/scaling_governor" 2>/dev/null)
          c=@(cat "@p/scaling_cur_freq" 2>/dev/null)
          lo=@(cat "@p/scaling_min_freq" 2>/dev/null)
          hi=@(cat "@p/scaling_max_freq" 2>/dev/null)
          echo "CPU:@n|gov=@g cur=@c min=@lo max=@hi"
        done
        gp=/sys/class/devfreq/23100000.gpu
        if [ -d "@gp" ]; then
          echo "GPU:governor=@(cat "@gp/governor" 2>/dev/null) cur=@(cat "@gp/cur_freq" 2>/dev/null) min=@(cat "@gp/min_freq" 2>/dev/null) max=@(cat "@gp/max_freq" 2>/dev/null)"
        else echo 'GPU:unavailable'; fi
        grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo 2>/dev/null | while read k v unit; do echo "MEM:@k|@v @unit"; done
        for z in /sys/class/thermal/thermal_zone*; do
          [ -r "@z/temp" ] || continue
          t=@(cat "@z/type" 2>/dev/null); v=@(cat "@z/temp" 2>/dev/null)
          echo "THERMAL:@{z##*thermal_zone}|@t|@v"
        done
        if [ -d /data/adb/modules ]; then
          for m in /data/adb/modules/*; do
            [ -d "@m" ] || continue
            id=@{m##*/}; name=@(sed -n 's/^name=//p' "@m/module.prop" 2>/dev/null | head -n 1)
            ver=@(sed -n 's/^version=//p' "@m/module.prop" 2>/dev/null | head -n 1)
            [ -n "@name" ] || name=@id
            state=enabled; [ -e "@m/disable" ] && state=disabled; [ -e "@m/remove" ] && state=pending-remove
            echo "MODULE:@name|@ver|@state|@id"
          done
        fi
    """.trimIndent().replace("@", "\$")

    val output = runFixedRootCommand(script) { text, code -> if (code == 0) text else "" } ?: return DiagnosticReport(error = "Root command gagal atau timeout")
    val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
    fun values(prefix: String) = lines.filter { it.startsWith(prefix) }.map { it.substringAfter(prefix) }
    val kernel = lines.firstOrNull { it.startsWith("KERNEL:") }?.substringAfter("KERNEL:") ?: "Unknown"
    val uptimeRaw = lines.firstOrNull { it.startsWith("UPTIME:") }?.substringAfter("UPTIME:") ?: "—"
    val uptimeSeconds = uptimeRaw.toDoubleOrNull()?.toLong()
    val uptime = uptimeSeconds?.let { "${it / 3600}h ${(it % 3600) / 60}m" } ?: uptimeRaw
    val thermal = values("THERMAL:").map { raw ->
        val p = raw.split('|')
        val milli = p.getOrNull(2)?.toDoubleOrNull()
        val temp = milli?.let { if (it > 1000) "%.1f°C".format(Locale.US, it / 1000.0) else "${it}°C" } ?: (p.getOrNull(2) ?: "?")
        "${p.getOrNull(1) ?: "zone"} · $temp"
    }.take(12)
    return DiagnosticReport(
        success = true,
        capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        kernel = kernel,
        uptime = uptime,
        cpu = values("CPU:"),
        gpu = values("GPU:"),
        memory = values("MEM:").map { it.replace('|', ' ') },
        thermal = thermal,
        modules = values("MODULE:").map { raw ->
            val p = raw.split('|'); "${p.getOrNull(0) ?: "Module"} · ${p.getOrNull(1)?.ifBlank { "version unknown" } ?: "version unknown"} · ${p.getOrNull(2) ?: "unknown"} (${p.getOrNull(3) ?: "?"})"
        }
    )
}

private fun <T> runFixedRootCommand(command: String, transform: (String, Int) -> T): T? {
    return try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = process.waitFor(12, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            null
        } else {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().take(24000) }
            transform(output.trim(), process.exitValue())
        }
    } catch (_: Exception) { null }
}
