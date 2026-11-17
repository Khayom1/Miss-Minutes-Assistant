package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.khayom1.missminutes.backup.BackupArchive
import com.khayom1.missminutes.memory.RestoreConflictPolicy
import com.khayom1.missminutes.plugins.PluginManifest

@Composable
internal fun BackupRestorePanel(
    language: AppLanguage,
    categories: Set<BackupArchive.Category>,
    onToggleCategory: (BackupArchive.Category) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    conflictPolicy: RestoreConflictPolicy,
    onConflictPolicyChange: (RestoreConflictPolicy) -> Unit,
    preview: BackupArchive.Preview?,
    status: String?,
    error: String?,
    busy: Boolean,
    onCreateBackup: () -> Unit,
    onChooseBackup: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissPreview: () -> Unit,
    pluginManifests: List<PluginManifest>,
    pluginConsentStates: Map<String, Boolean>,
    onPluginConsentChange: (String, Boolean) -> Unit,
    onExportPlugin: (String) -> Unit
) {
    val isTajik = language == AppLanguage.Tajik
    val isRussian = language == AppLanguage.Russian
    val title = if (isTajik) "Нусхаи эҳтиётӣ" else if (isRussian) "Резервная копия" else "Backup and restore"
    val categoryLabels = mapOf(
        BackupArchive.Category.CHAT_HISTORY to if (isTajik) "Гуфтугӯҳо ва хотира" else if (isRussian) "Чаты и память" else "Chats and memory",
        BackupArchive.Category.APP_SETTINGS to if (isTajik) "Танзимоти барнома" else if (isRussian) "Настройки приложения" else "App settings",
        BackupArchive.Category.PLUGINS to if (isTajik) "Метамаълумоти плагинҳо" else if (isRussian) "Метаданные плагинов" else "Plugin manifests"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (isTajik) "Нусха бо парол рамзгузорӣ мешавад. Калидҳои API, иҷозатҳои Android ва розигии плагинҳо дохил намешаванд."
            else if (isRussian) "Резервная копия шифруется паролем. API-ключи, разрешения Android и согласия плагинов не включаются."
            else "The backup is password-encrypted. API keys, Android permissions and plugin consent are never included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BackupArchive.Category.entries.forEach { category ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = category in categories,
                    onCheckedChange = { onToggleCategory(category) }
                )
                Text(categoryLabels.getValue(category), modifier = Modifier.weight(1f))
            }
        }
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (isTajik) "Пароли нусха (ҳадди ақал 10 аломат)" else if (isRussian) "Пароль копии (не менее 10 символов)" else "Backup password (at least 10 characters)")
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        if (pluginManifests.isNotEmpty()) {
            Text(
                if (isTajik) "Содироти алоҳидаи плагин"
                else if (isRussian) "Отдельный экспорт плагина"
                else "Export one plugin separately",
                style = MaterialTheme.typography.labelLarge
            )
            pluginManifests.forEach { manifest ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(manifest.displayName, modifier = Modifier.weight(1f))
                    Switch(
                        checked = pluginConsentStates[manifest.id] == true,
                        onCheckedChange = { onPluginConsentChange(manifest.id, it) }
                    )
                    TextButton(
                        enabled = !busy && password.length >= 10,
                        onClick = { onExportPlugin(manifest.id) }
                    ) {
                        Text(if (isTajik) "Содирот" else if (isRussian) "Экспорт" else "Export")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreateBackup, enabled = !busy && categories.isNotEmpty() && password.length >= 10) {
                Text(if (isTajik) "Содирот" else if (isRussian) "Экспорт" else "Export")
            }
            TextButton(onClick = onChooseBackup, enabled = !busy && password.length >= 10) {
                Text(if (isTajik) "Интихоби файл" else if (isRussian) "Выбрать файл" else "Choose file")
            }
        }
        Text(
            if (isTajik) "Ҳангоми барқарорсозӣ:"
            else if (isRussian) "При восстановлении:"
            else "Restore conflict policy:",
            style = MaterialTheme.typography.labelLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RestoreConflictPolicy.entries.forEach { policy ->
                val label = when (policy) {
                    RestoreConflictPolicy.MERGE -> if (isTajik) "Якҷоя" else if (isRussian) "Объединить" else "Merge"
                    RestoreConflictPolicy.KEEP_EXISTING -> if (isTajik) "Нигоҳ доштан" else if (isRussian) "Сохранить" else "Keep"
                    RestoreConflictPolicy.REPLACE -> if (isTajik) "Иваз кардан" else if (isRussian) "Заменить" else "Replace"
                }
                FilterChip(
                    selected = conflictPolicy == policy,
                    onClick = { onConflictPolicyChange(policy) },
                    label = { Text(label) }
                )
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (preview != null) {
        AlertDialog(
            onDismissRequest = onDismissPreview,
            title = { Text(if (isTajik) "Пешнамоиши нусха" else if (isRussian) "Предпросмотр копии" else "Backup preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isTajik) "Категорияҳо:" else if (isRussian) "Категории:" else "Categories:")
                    preview.categories.forEach { Text("• ${categoryLabels.getValue(it)}") }
                    Text(
                        if (isTajik) "Ҳаҷм: ${preview.uncompressedBytes} байт"
                        else if (isRussian) "Размер: ${preview.uncompressedBytes} байт"
                        else "Uncompressed size: ${preview.uncompressedBytes} bytes"
                    )
                    Text(
                        if (isTajik) "Плагинҳои воридшуда то иҷозати дастӣ хомӯш мемонанд."
                        else if (isRussian) "Импортированные плагины останутся выключенными до ручного разрешения."
                        else "Imported plugins remain disabled until explicitly approved."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text(if (isTajik) "Барқарор кардан" else if (isRussian) "Восстановить" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) {
                    Text(if (isTajik) "Бекор" else if (isRussian) "Отмена" else "Cancel")
                }
            }
        )
    }
}
