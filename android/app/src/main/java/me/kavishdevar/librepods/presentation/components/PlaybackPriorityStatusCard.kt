package me.kavishdevar.librepods.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.ServiceManager

/** Small, copyable status instead of asking the user to collect system logs. */
@Composable
fun PlaybackPriorityStatusCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf("") }
    LaunchedEffect(context) {
        while (true) {
            fun permission(name: String) = context.getString(
                if (context.checkSelfPermission("android.permission.$name") == PackageManager.PERMISSION_GRANTED)
                    R.string.priority_granted else R.string.priority_missing
            )
            val systemApp = context.applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            report = listOf(
                BuildConfig.VERSION_NAME,
                context.getString(if (systemApp) R.string.priority_system_app else R.string.priority_user_app),
                "BLUETOOTH_PRIVILEGED: ${permission("BLUETOOTH_PRIVILEGED")}",
                "MODIFY_PHONE_STATE: ${permission("MODIFY_PHONE_STATE")}",
                ServiceManager.getService()?.priorityDiagnostics()
                    ?: context.getString(R.string.priority_service_missing)
            ).joinToString("\n")
            delay(1_000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
        .padding(16.dp)) {
        Text(stringResource(R.string.priority_status_title), style = MaterialTheme.typography.titleMedium)
        Text(report, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.priority_status_hint), modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("LibrePods playback protection", report))
        }) { Text(stringResource(R.string.priority_copy_status)) }
    }
}
