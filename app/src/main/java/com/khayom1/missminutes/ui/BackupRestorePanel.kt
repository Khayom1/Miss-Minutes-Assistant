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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.khayom1.missminutes.backup.BackupArchive
import com.khayom1.missminutes.memory.RestoreConflictPolicy
import com.khayom1.missminutes.plugins.PluginCapability
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
    onExportPlugin: (String) -> Unit,
    onImportPluginPackage: () -> Unit,
    onExportPluginPackage: (String) -> Unit,
    onUninstallPlugin: (String) -> Unit,
    pendingPluginImport: List<PluginManifest>?,
    onConfirmPluginImport: () -> Unit,
    onDismissPluginImport: () -> Unit
) {
    val isTajik = language == AppLanguage.Tajik
    val isRussian = language == AppLanguage.Russian
    var uninstallCandidate by remember { mutableStateOf<PluginManifest?>(null) }
    val title = if (isTajik) "Нусхаи эҳтиётӣ" else if (isRussian) "Резервная копия" else "Backup and restore"
    fun capabilityLabel(capability: PluginCapability): String {
        val label = when (capability) {
            PluginCapability.DEVICE_INFORMATION -> if (isTajik) "Маълумоти дастгоҳ" else if (isRussian) "Информация об устройстве" else "Device information"
            PluginCapability.BATTERY_STATUS -> if (isTajik) "Ҳолати батарея" else if (isRussian) "Состояние батареи" else "Battery status"
            PluginCapability.CALENDAR_READ -> if (isTajik) "Хондани тақвим" else if (isRussian) "Чтение календаря" else "Read calendar"
            PluginCapability.CALENDAR_WRITE -> if (isTajik) "Тағйири тақвим" else if (isRussian) "Изменение календаря" else "Modify calendar"
            PluginCapability.CONTACTS_READ -> if (isTajik) "Хондани тамосҳо" else if (isRussian) "Чтение контактов" else "Read contacts"
            PluginCapability.LOCATION_APPROXIMATE -> if (isTajik) "Ҷойгиршавии тахминӣ" else if (isRussian) "Приблизительное местоположение" else "Approximate location"
            PluginCapability.LOCATION_PRECISE -> if (isTajik) "Ҷойгиршавии дақиқ" else if (isRussian) "Точное местоположение" else "Precise location"
            PluginCapability.NOTIFICATION_READ -> if (isTajik) "Хондани огоҳиномаҳо" else if (isRussian) "Чтение уведомлений" else "Read notifications"
            PluginCapability.GITHUB_REPOSITORY_READ -> if (isTajik) "Хондани GitHub" else if (isRussian) "Чтение репозитория GitHub" else "Read GitHub repository"
            PluginCapability.GITHUB_REPOSITORY_WRITE -> if (isTajik) "Тағйири GitHub" else if (isRussian) "Изменение репозитория GitHub" else "Modify GitHub repository"
        }
        val requiresConfirmation = capability == PluginCapability.CALENDAR_WRITE ||
            capability == PluginCapability.GITHUB_REPOSITORY_WRITE
        return if (!requiresConfirmation) label else label + when {
            isTajik -> " (тасдиқ ҳатмист)"
            isRussian -> " (требуется подтверждение)"
            else -> " (confirmation required)"
        }
    }
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
        TextButton(
            onClick = onImportPluginPackage,
            enabled = !busy,
            modifier = Modifier.testTag("plugin_package_import")
        ) {
            Text(
                if (isTajik) "Ворид кардани плагин аз файл"
                else if (isRussian) "Импортировать плагин из файла"
                else "Import plugin package"
            )
        }
        Text(
            if (isTajik) "Бастаи плагин танҳо метамаълумот дорад. Код, калидҳо, иҷозатҳои Android ва розигӣ ворид намешаванд."
            else if (isRussian) "Пакет содержит только метаданные. Код, ключи, разрешения Android и согласие не импортируются."
            else "Plugin packages contain metadata only. No code, credentials, Android permissions or consent are imported.",
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
                if (isTajik) "Плагинҳои маҳаллӣ"
                else if (isRussian) "Локальные плагины"
                else "Local plugins",
                style = MaterialTheme.typography.labelLarge
            )
            pluginManifests.forEach { manifest ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(manifest.displayName, modifier = Modifier.weight(1f))
                        Switch(
                            modifier = Modifier.testTag("plugin_consent_${manifest.id}"),
                            checked = pluginConsentStates[manifest.id] == true,
                            onCheckedChange = { onPluginConsentChange(manifest.id, it) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !busy,
                            onClick = { onExportPluginPackage(manifest.id) },
                            modifier = Modifier.testTag("plugin_package_export_${manifest.id}")
                        ) {
                            Text(if (isTajik) "Бастаи JSON" else if (isRussian) "Пакет JSON" else "JSON package")
                        }
                        TextButton(
                            enabled = !busy && password.length >= 10,
                            onClick = { onExportPlugin(manifest.id) }
                        ) {
                            Text(if (isTajik) "Нусхаи рамзшуда" else if (isRussian) "Зашифрованная копия" else "Encrypted backup")
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = { uninstallCandidate = manifest },
                            modifier = Modifier.testTag("plugin_uninstall_${manifest.id}")
                        ) {
                            Text(if (isTajik) "Нест кардан" else if (isRussian) "Удалить" else "Uninstall")
                        }
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

    if (pendingPluginImport != null) {
        AlertDialog(
            onDismissRequest = onDismissPluginImport,
            title = {
                Text(
                    if (isTajik) "Пешнамоиши плагин"
                    else if (isRussian) "Предпросмотр плагина"
                    else "Review plugin package"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendingPluginImport.forEach { manifest ->
                        Text("${manifest.displayName} (${manifest.id})")
                        Text(
                            if (isTajik) "Амалҳо: ${manifest.actions.size}"
                            else if (isRussian) "Действий: ${manifest.actions.size}"
                            else "Actions: ${manifest.actions.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            (if (isTajik) "Имкониятҳо: " else if (isRussian) "Возможности: " else "Capabilities: ") +
                                manifest.capabilities.sortedBy { it.name }.joinToString { capabilityLabel(it) },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        if (isTajik) "Пас аз воридкунӣ плагинҳо хомӯш мемонанд. Барои фаъол кардан розигии ҷудогона лозим аст."
                        else if (isRussian) "После импорта плагины останутся отключёнными. Для подключения требуется отдельное согласие."
                        else "Imported plugins stay disconnected. A separate one-time approval is required to connect them.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmPluginImport, enabled = !busy) {
                    Text(if (isTajik) "Ворид кардан" else if (isRussian) "Импортировать" else "Install")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPluginImport) {
                    Text(if (isTajik) "Бекор" else if (isRussian) "Отмена" else "Cancel")
                }
            }
        )
    }

    uninstallCandidate?.let { manifest ->
        AlertDialog(
            onDismissRequest = { uninstallCandidate = null },
            title = {
                Text(
                    if (isTajik) "Плагинро нест кунем?"
                    else if (isRussian) "Удалить плагин?"
                    else "Uninstall plugin?"
                )
            },
            text = {
                Text(
                    if (isTajik) "Метамаълумоти плагин нест ва розигии доимии он бозхонд мешавад. Таърихи чат тағйир намеёбад."
                    else if (isRussian) "Манифест будет удалён, а постоянное согласие отозвано. История чатов не изменится."
                    else "The manifest will be removed and its persistent consent revoked. Chat history will remain unchanged."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstallPlugin(manifest.id)
                        uninstallCandidate = null
                    }
                ) {
                    Text(if (isTajik) "Нест кардан" else if (isRussian) "Удалить" else "Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallCandidate = null }) {
                    Text(if (isTajik) "Бекор" else if (isRussian) "Отмена" else "Cancel")
                }
            }
        )
    }
}
