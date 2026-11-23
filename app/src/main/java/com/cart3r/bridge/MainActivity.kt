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

private data class RootCheck(
    val available: Boolean,
    val granted: Boolean,
    val detail: String
)

@androidx.compose.runtime.Composable
private fun BridgeDashboard() {
    var rootCheck by remember {
        mutableStateOf(RootCheck(false, false, "Belum diperiksa"))
    }
    var checking by remember { mutableStateOf(true) }

    val model = Build.MODEL ?: "Unknown"
    val manufacturer = Build.MANUFACTURER ?: "Unknown"
    val device = Build.DEVICE ?: "Unknown"
    val product = Build.PRODUCT ?: "Unknown"
    val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
    val sdk = Build.VERSION.SDK_INT
    val expectedDevice = model.equals("RMX5303", ignoreCase = true) ||
        product.contains("RMX5303", ignoreCase = true) ||
        device.contains("RMX5303", ignoreCase = true)

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
                Text("CART3R", color = Crimson, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("ANDROID BRIDGE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("PHASE 1 · DEVICE FOUNDATION", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
            StatusPill("PHASE 1")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DEVICE OVERVIEW", color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$manufacturer $model", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (expectedDevice) "Target device identified" else "Device detected · target model not confirmed",
                    color = if (expectedDevice) Green else Amber,
                    fontSize = 12.sp
                )
                InfoRow("Model", model)
                InfoRow("Device", device)
                InfoRow("Product", product)
                InfoRow("Android", "$androidVersion (API $sdk)")
                InfoRow("Build fingerprint", Build.FINGERPRINT.takeLast(54))
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("KERNELSU / ROOT STATUS", color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            checking -> "Checking root permission…"
                            rootCheck.granted -> "Root command approved"
                            rootCheck.available -> "Root available, not granted"
                            else -> "Root unavailable / denied"
                        },
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (checking) CircularProgressIndicator(color = Crimson)
                    else StatusPill(if (rootCheck.granted) "GRANTED" else if (rootCheck.available) "AVAILABLE" else "NOT READY")
                }
                Text(rootCheck.detail, color = Muted, fontSize = 12.sp)
                Button(
                    onClick = {
                        checking = true
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson)
                ) {
                    Text("CHECK ROOT AGAIN")
                }
                LaunchedEffect(checking) {
                    if (checking) {
                        rootCheck = withContext(Dispatchers.IO) { checkKernelSuRoot() }
                        checking = false
                    }
                }
                Text(
                    "Root check runs only: su -c id. No system files or settings are changed.",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BRIDGE MODULES", color = Crimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                FeatureRow("Device identification", "READY")
                FeatureRow("KernelSU root detection", if (rootCheck.granted) "READY" else "CHECK")
                FeatureRow("Live diagnostics", "PHASE 2")
                FeatureRow("Approval controller", "PHASE 2")
                FeatureRow("System operations", "LOCKED")
            }
        }

        Text(
            "SECURITY: No remote command endpoint · No arbitrary shell · No system modifications in Phase 1",
            color = Muted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF351019), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFFFF8290), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.runtime.Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value.ifBlank { "Unknown" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@androidx.compose.runtime.Composable
private fun FeatureRow(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color.White, fontSize = 13.sp)
        Text(
            status,
            color = if (status == "READY") Green else Amber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun checkKernelSuRoot(): RootCheck {
    return try {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(8, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            RootCheck(true, false, "Root check timed out. Check the KernelSU superuser prompt.")
        } else {
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val granted = process.exitValue() == 0 && output.contains("uid=0")
            RootCheck(
                available = true,
                granted = granted,
                detail = if (granted) output else output.ifBlank { "Root request was denied or su returned no output." }
            )
        }
    } catch (e: Exception) {
        RootCheck(false, false, "Could not execute su: ${e.javaClass.simpleName}")
    }
}
