package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.khayom1.missminutes.ai.AiToolApprovalPrompt

/** Confirmation UI exposes action metadata and field names, never raw argument values. */
@Composable
internal fun AiToolApprovalDialog(
    approval: AiToolApprovalPrompt,
    language: AppLanguage,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                when (language) {
                    AppLanguage.Tajik -> "Тасдиқи амали плагин"
                    AppLanguage.Russian -> "Подтверждение действия плагина"
                    AppLanguage.English -> "Approve plugin action"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(approval.pluginName, fontWeight = FontWeight.SemiBold)
                Text(approval.toolName, style = MaterialTheme.typography.labelMedium)
                Text(approval.description)
                if (approval.argumentNames.isNotEmpty()) {
                    Text(
                        when (language) {
                            AppLanguage.Tajik -> "Майдонҳои дархост: " + approval.argumentNames.joinToString()
                            AppLanguage.Russian -> "Поля запроса: " + approval.argumentNames.joinToString()
                            AppLanguage.English -> "Requested fields: " + approval.argumentNames.joinToString()
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    when (language) {
                        AppLanguage.Tajik -> "Ин амал танҳо як бор, баъди иҷозати ту иҷро мешавад. Арзишҳои махфии параметрҳо нишон дода намешаванд."
                        AppLanguage.Russian -> "Действие будет выполнено один раз только после вашего разрешения. Секретные значения параметров не отображаются."
                        AppLanguage.English -> "This action runs once only after your approval. Sensitive argument values are not displayed."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove, modifier = Modifier.testTag("approve_plugin_tool")) {
                Text(
                    when (language) {
                        AppLanguage.Tajik -> "Иҷозат додан"
                        AppLanguage.Russian -> "Разрешить"
                        AppLanguage.English -> "Approve once"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReject, modifier = Modifier.testTag("reject_plugin_tool")) {
                Text(
                    when (language) {
                        AppLanguage.Tajik -> "Рад кардан"
                        AppLanguage.Russian -> "Отклонить"
                        AppLanguage.English -> "Reject"
                    }
                )
            }
        }
    )
}
