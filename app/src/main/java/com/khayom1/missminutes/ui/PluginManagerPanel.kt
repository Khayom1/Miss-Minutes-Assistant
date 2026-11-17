package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.khayom1.missminutes.plugins.PluginAuditEntry
import com.khayom1.missminutes.plugins.PluginManifest

@Composable
internal fun PluginManagerPanel(
    language: AppLanguage,
    manifests: List<PluginManifest>,
    consentStates: Map<String, Boolean>,
    auditEntries: List<PluginAuditEntry>,
    onConsentChange: (String, Boolean) -> Boolean,
    onUninstall: (String) -> Boolean
) {
    val isTajik = language == AppLanguage.Tajik
    val isRussian = language == AppLanguage.Russian
    var pendingUninstall by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun text(en: String, ru: String, tg: String): String =
        if (isTajik) tg else if (isRussian) ru else en

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text("Installed plugins", "Установленные плагины", "Плагинҳои насбшуда"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text(
                "Imported plugins contain metadata only. Actions run only through compiled allowlisted handlers.",
                "Импортированные плагины содержат только метаданные. Действия выполняются только через встроенные разрешённые обработчики.",
                "Плагинҳои воридшуда танҳо метамаълумот доранд. Амалҳо фақат тавассути коркардкунандагони иҷозатдодаи дохили барнома иҷро мешаванд."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        if (manifests.isEmpty()) {
            Text(
                text("No external plugins are installed.", "Внешние плагины не установлены.", "Ҳоло плагини беруна насб нашудааст."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            manifests.forEach { manifest ->
                val granted = consentStates[manifest.id] == true
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(manifest.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            manifest.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text(
                                if (granted) "Consent enabled" else "Consent disabled",
                                if (granted) "Разрешение включено" else "Разрешение выключено",
                                if (granted) "Иҷозат фаъол аст" else "Иҷозат хомӯш аст"
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Switch(
                                checked = granted,
                                onCheckedChange = { enabled ->
                                    val updated = onConsentChange(manifest.id, enabled)
                                    status = if (updated) {
                                        if (enabled) {
                                            text("Plugin consent granted.", "Разрешение плагина выдано.", "Розигии плагин дода шуд.")
                                        } else {
                                            text("Plugin consent revoked.", "Разрешение плагина отозвано.", "Розигии плагин бекор карда шуд.")
                                        }
                                    } else {
                                        text(
                                            "The consent change could not be saved safely.",
                                            "Не удалось безопасно сохранить изменение разрешения.",
                                            "Тағйири иҷозатро бехатар нигоҳ доштан нашуд."
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .testTag("plugin_consent_${manifest.id}")
                                    .semantics {
                                        contentDescription = "${manifest.displayName} plugin consent"
                                        stateDescription = if (granted) "Enabled" else "Disabled"
                                    }
                            )
                            TextButton(
                                onClick = { pendingUninstall = manifest.id },
                                modifier = Modifier.testTag("plugin_uninstall_${manifest.id}")
                            ) {
                                Text(text("Uninstall", "Удалить", "Насбро бекор кардан"))
                            }
                        }
                    }
                }
            }
        }

        status?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text("Recent plugin activity", "Последние действия плагинов", "Фаъолияти охирини плагинҳо"),
            style = MaterialTheme.typography.titleSmall
        )
        if (auditEntries.isEmpty()) {
            Text(
                text("No plugin activity has been recorded.", "Действия плагинов пока не записаны.", "Ҳоло фаъолияти плагинҳо сабт нашудааст."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(
                modifier = Modifier.testTag("plugin_audit_log"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                auditEntries.take(20).forEach { entry ->
                    val outcome = when (entry.outcome) {
                        "success" -> text("completed", "выполнено", "анҷом ёфт")
                        "failed" -> text("failed", "ошибка", "ноком")
                        "denied" -> text("denied", "отклонено", "рад шуд")
                        "consent_granted" -> text("consent granted", "разрешение выдано", "розигӣ дода шуд")
                        "consent_revoked" -> text("consent revoked", "разрешение отозвано", "розигӣ бекор шуд")
                        "uninstalled" -> text("uninstalled", "удалён", "насб бекор шуд")
                        else -> entry.outcome.replace('_', ' ')
                    }
                    Text(
                        "${entry.pluginId} · ${entry.actionId} · $outcome",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val uninstallId = pendingUninstall
    if (uninstallId != null) {
        val selected = manifests.firstOrNull { it.id == uninstallId }
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = {
                Text(text("Uninstall plugin?", "Удалить плагин?", "Плагинро нест кардан?"))
            },
            text = {
                Text(
                    text(
                        "This revokes consent before removing the saved manifest. The plugin will not be allowed to run.",
                        "Перед удалением будут отозваны разрешения и удалён сохранённый манифест. Плагин не сможет выполняться.",
                        "Пеш аз нест кардан иҷозат бекор ва манифест пок карда мешавад. Плагин дигар иҷро шуда наметавонад."
                    ) + (selected?.displayName?.let { "\n\n$it" } ?: "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val removed = onUninstall(uninstallId)
                    status = if (removed) {
                        text("Plugin uninstalled.", "Плагин удалён.", "Плагин нест карда шуд.")
                    } else {
                        text(
                            "Uninstall could not be completed. Consent remains disabled.",
                            "Не удалось завершить удаление. Разрешение остаётся выключенным.",
                            "Нест кардан анҷом нашуд. Иҷозат хомӯш боқӣ мемонад."
                        )
                    }
                    if (removed) pendingUninstall = null
                }) {
                    Text(text("Uninstall", "Удалить", "Нест кардан"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) {
                    Text(text("Cancel", "Отмена", "Бекор кардан"))
                }
            }
        )
    }
}
