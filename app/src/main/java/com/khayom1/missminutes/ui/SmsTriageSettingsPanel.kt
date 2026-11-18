package com.khayom1.missminutes.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.khayom1.missminutes.R
import com.khayom1.missminutes.assistant.AndroidAssistantRole
import com.khayom1.missminutes.communication.sms.SmsInboxScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SmsTriageSettingsPanel() {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    var assistantRoleHeld by remember(context) { mutableStateOf(AndroidAssistantRole.isHeld(context)) }
    var readSmsGranted by remember(context) { mutableStateOf(hasPermission(context, Manifest.permission.READ_SMS)) }
    var receiveSmsGranted by remember(context) { mutableStateOf(hasPermission(context, Manifest.permission.RECEIVE_SMS)) }
    var notificationsGranted by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val roleRequiredText = stringResource(R.string.sms_triage_role_required)
    val permissionDeniedText = stringResource(R.string.sms_triage_permission_denied)
    val activeText = stringResource(R.string.sms_triage_status_active)
    val notificationDisabledText = stringResource(R.string.sms_triage_notifications_disabled)
    val scanFailedText = stringResource(R.string.sms_triage_scan_failed)
    val scanResultFormat = stringResource(R.string.sms_triage_scan_result)

    fun refreshState() {
        assistantRoleHeld = AndroidAssistantRole.isHeld(context)
        readSmsGranted = hasPermission(context, Manifest.permission.READ_SMS)
        receiveSmsGranted = hasPermission(context, Manifest.permission.RECEIVE_SMS)
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshState()
        resultMessage = when {
            !readSmsGranted || !receiveSmsGranted -> permissionDeniedText
            !notificationsGranted -> notificationDisabledText
            else -> activeText
        }
    }

    val permissionsGranted = readSmsGranted && receiveSmsGranted
    val status = when {
        !assistantRoleHeld -> roleRequiredText
        !permissionsGranted -> stringResource(R.string.sms_triage_status_needs_permission)
        !notificationsGranted -> notificationDisabledText
        else -> activeText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.sms_triage_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.sms_triage_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                status,
                color = if (permissionsGranted && assistantRoleHeld) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            resultMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (!AndroidAssistantRole.isHeld(context)) {
                        assistantRoleHeld = false
                        resultMessage = roleRequiredText
                    } else {
                        assistantRoleHeld = true
                        val requested = buildList {
                            if (!hasPermission(context, Manifest.permission.READ_SMS)) add(Manifest.permission.READ_SMS)
                            if (!hasPermission(context, Manifest.permission.RECEIVE_SMS)) add(Manifest.permission.RECEIVE_SMS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            ) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (requested.isEmpty()) {
                            resultMessage = activeText
                        } else {
                            permissionLauncher.launch(requested.toTypedArray())
                        }
                    }
                },
                enabled = !permissionsGranted || !assistantRoleHeld || !notificationsGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sms_triage_enable))
            }
            Button(
                onClick = {
                    scanning = true
                    scope.launch {
                        try {
                            val scan = withContext(Dispatchers.IO) {
                                SmsInboxScanner.scanRecent(context.applicationContext, maximumMessages = 200)
                            }
                            resultMessage = scanResultFormat.format(
                                scan.scannedMessages,
                                scan.importantMessages,
                                scan.newlyClassified
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: SecurityException) {
                            resultMessage = permissionDeniedText
                        } catch (_: IllegalStateException) {
                            resultMessage = permissionDeniedText
                        } catch (_: RuntimeException) {
                            resultMessage = scanFailedText
                        } finally {
                            scanning = false
                        }
                    }
                },
                enabled = assistantRoleHeld && readSmsGranted && !scanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (scanning) stringResource(R.string.sms_triage_scanning)
                    else stringResource(R.string.sms_triage_scan_recent)
                )
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
