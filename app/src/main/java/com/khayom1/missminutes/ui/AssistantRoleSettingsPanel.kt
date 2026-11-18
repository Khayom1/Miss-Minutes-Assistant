package com.khayom1.missminutes.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khayom1.missminutes.R
import com.khayom1.missminutes.assistant.AndroidAssistantRole

@Composable
internal fun AssistantRoleSettingsPanel() {
    val context = LocalContext.current
    var available by remember(context) { mutableStateOf(AndroidAssistantRole.isAvailable(context)) }
    var held by remember(context) { mutableStateOf(AndroidAssistantRole.isHeld(context)) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val roleNotGrantedMessage = stringResource(R.string.miss_minutes_assistant_role_not_granted)

    fun refreshRoleState() {
        available = AndroidAssistantRole.isAvailable(context)
        held = AndroidAssistantRole.isHeld(context)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshRoleState()
        resultMessage = if (held) null else context.getString(
            roleNotGrantedMessage
        )
    }

    val status = when {
        held -> stringResource(R.string.miss_minutes_assistant_role_held)
        !available -> stringResource(R.string.miss_minutes_assistant_role_unavailable)
        else -> stringResource(R.string.miss_minutes_assistant_role_available)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.miss_minutes_assistant_role_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.miss_minutes_assistant_role_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                status,
                color = if (held) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            resultMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val requestIntent = AndroidAssistantRole.createRequestIntent(context)
                    if (requestIntent == null) {
                        refreshRoleState()
                        if (!held) resultMessage = context.getString(
                            roleNotGrantedMessage
                        )
                    } else {
                        roleLauncher.launch(requestIntent)
                    }
                },
                enabled = available && !held,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.miss_minutes_assistant_role_request))
            }
        }
    }
}
