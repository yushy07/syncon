package com.yu.syncon.ui.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.remote.ConnectedInstallation
import com.yu.syncon.ui.components.OutlinedPillButton
import com.yu.syncon.ui.components.PrimaryPillButton
import com.yu.syncon.ui.components.SecondaryPillButton
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentSage
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardShape
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun ConnectedDevicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val cloud = (context.applicationContext as SyncOnApp).cloudSyncRepository
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(cloud.isSignedIn()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<ConnectedInstallation>>(emptyList()) }
    var pendingRevoke by remember { mutableStateOf<ConnectedInstallation?>(null) }
    var pendingPairingCode by remember { mutableStateOf<String?>(null) }
    var pendingAccountDelete by remember { mutableStateOf(false) }
    var conflictCount by remember { mutableStateOf(0) }

    suspend fun refreshDevices() {
        if (!cloud.isSignedIn()) {
            devices = emptyList()
            return
        }
        devices = runCatching { cloud.connectedInstallations() }
            .onFailure { message = it.message }
            .getOrDefault(emptyList())
        conflictCount = cloud.unresolvedConflictCount()
    }

    LaunchedEffect(signedIn) { if (signedIn) refreshDevices() }

    fun scanQr() {
        if (activity == null) {
            message = "The scanner could not open from this screen."
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue ?: return@addOnSuccessListener
                pendingPairingCode = rawValue
            }
            .addOnFailureListener { message = it.message ?: "Could not scan this code" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Connected devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Chrome + Android", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Sign in once on this phone, then scan the one-time QR shown by the Chrome extension. Tracking and blocking stay local when either device is offline.",
                color = TextSecondary
            )

            if (!signedIn) {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your SyncOn account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Name (for new accounts)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryPillButton(
                            text = if (busy) "Signing in…" else "Sign in",
                            onClick = {
                                if (email.isBlank() || password.length < 6) {
                                    message = "Enter your email and a password of at least 6 characters."
                                } else scope.launch {
                                    busy = true
                                    runCatching { cloud.signIn(email, password) }
                                        .onSuccess { signedIn = true; message = "Signed in successfully." }
                                        .onFailure { message = it.message }
                                    busy = false
                                }
                            }
                        )
                        SecondaryPillButton(
                            text = "Create account",
                            onClick = {
                                if (displayName.isBlank() || email.isBlank() || password.length < 6) {
                                    message = "Add your name, email, and a password of at least 6 characters."
                                } else scope.launch {
                                    busy = true
                                    runCatching { cloud.signUp(email, password, displayName) }
                                        .onSuccess { result -> message = result; signedIn = cloud.isSignedIn() }
                                        .onFailure { message = it.message }
                                    busy = false
                                }
                            }
                        )
                        if (conflictCount > 0) {
                            Text(
                                "$conflictCount setting change${if (conflictCount == 1) "" else "s"} from another device need review. The server-approved version is active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentCoral
                            )
                        }
                    }
                }
            } else {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentSage)
                            Column {
                                Text("Account ready", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(lastSyncLabel(cloud.lastSyncAt()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        PrimaryPillButton(text = if (busy) "Connecting…" else "Scan Chrome QR", onClick = ::scanQr)
                        SecondaryPillButton(
                            text = "Sync now",
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { cloud.syncNow() }
                                        .onSuccess { message = "Everything is up to date."; refreshDevices() }
                                        .onFailure { message = it.message }
                                    busy = false
                                }
                            }
                        )
                    }
                }

                Text("Devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (devices.isEmpty()) {
                    Text("No connected Chrome browser yet. Install the extension and scan its QR code.", color = TextSecondary)
                }
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        onRevoke = if (device.platform == "CHROME") ({ pendingRevoke = device }) else null
                    )
                }

                OutlinedPillButton(
                    text = "Sign out on this phone",
                    onClick = {
                        scope.launch {
                            cloud.signOut()
                            signedIn = false
                            devices = emptyList()
                            message = "Signed out. Local tracking is unchanged."
                        }
                    }
                )
                TextButton(onClick = { pendingAccountDelete = true }) {
                    Text("Delete SyncOn account", color = AccentCoral)
                }
            }

            message?.let {
                Text(
                    it,
                    color = if (it.contains("fail", true) || it.contains("could not", true) || it.contains("invalid", true)) AccentCoral else PrimaryIndigo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text("Disconnect ${device.displayName}?") },
            text = { Text("That Chrome installation will lose future cloud access. Its local history will stay on the computer.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRevoke = null
                    scope.launch {
                        busy = true
                        runCatching { cloud.revokeInstallation(device.installationId) }
                            .onSuccess { message = "Chrome disconnected."; refreshDevices() }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }) { Text("Disconnect", color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { pendingRevoke = null }) { Text("Cancel") } }
        )
    }

    pendingPairingCode?.let { code ->
        AlertDialog(
            onDismissRequest = { pendingPairingCode = null },
            title = { Text("Connect this Chrome browser?") },
            text = { Text("Approve only if this QR is currently visible on a computer you recognize. The browser will be able to sync your SyncOn activity and settings.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPairingCode = null
                    scope.launch {
                        busy = true
                        message = runCatching { cloud.claimPairing(code) }
                            .onSuccess { refreshDevices() }
                            .fold(onSuccess = { it }, onFailure = { it.message ?: "Pairing failed" })
                        busy = false
                    }
                }) { Text("Connect", color = PrimaryIndigo) }
            },
            dismissButton = { TextButton(onClick = { pendingPairingCode = null }) { Text("Cancel") } }
        )
    }

    if (pendingAccountDelete) {
        AlertDialog(
            onDismissRequest = { pendingAccountDelete = false },
            title = { Text("Delete your SyncOn account?") },
            text = { Text("This permanently removes the cloud account, connected-device access, and synced cloud history. Local tracking data on this phone and browser installations is not silently erased.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingAccountDelete = false
                    scope.launch {
                        busy = true
                        runCatching { cloud.deleteAccount() }
                            .onSuccess {
                                signedIn = false
                                devices = emptyList()
                                message = "SyncOn cloud account deleted. Local phone history remains."
                            }
                            .onFailure { message = it.message ?: "Account deletion failed" }
                        busy = false
                    }
                }) { Text("Delete permanently", color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { pendingAccountDelete = false }) { Text("Keep account") } }
        )
    }
}

@Composable
private fun DeviceCard(device: ConnectedInstallation, onRevoke: (() -> Unit)?) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (device.platform == "CHROME") Icons.Default.Computer else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = PrimaryIndigo
                )
                Column {
                    Text(device.displayName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("${device.platform.lowercase().replaceFirstChar(Char::uppercase)} · ${formatLastSeen(device.lastSeenAt)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (onRevoke != null) TextButton(onClick = onRevoke) { Text("Disconnect", color = AccentCoral) }
        }
    }
}

private fun lastSyncLabel(value: Long): String = if (value <= 0L) "Not synced yet" else "Last synced ${java.text.DateFormat.getDateTimeInstance().format(value)}"

private fun formatLastSeen(value: String): String = runCatching {
    "seen ${DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.parse(value).atZone(java.time.ZoneId.systemDefault()))}"
}.getOrDefault("recently")
