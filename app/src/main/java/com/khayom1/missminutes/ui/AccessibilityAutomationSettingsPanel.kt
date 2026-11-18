package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AccessibilityAutomationSettingsPanel(
    language: AppLanguage,
    enabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val isTajik = language == AppLanguage.Tajik
    val isRussian = language == AppLanguage.Russian
    val title = when {
        isTajik -> "Идоракунии умумии барномаҳо"
        isRussian -> "Управление любыми приложениями"
        else -> "Cross-app automation"
    }
    val description = when {
        isTajik -> "Пас аз фаъол кардани хидмати Accessibility, Miss Minutes метавонад экрани барномаи кушодаро ҳангоми фармони шумо хонад, тугмаҳоро пахш кунад, ба майдонҳо матн ворид кунад ва паём ё шарҳ фиристад. Ин ба Instagram маҳдуд нест. Матни намоёни экран барои фаҳмидани супориш ба DeepSeek фиристода мешавад. Амалҳои хатарнок, молиявӣ ва несткунанда бастаанд ё тасдиқи иловагӣ мехоҳанд. Иҷозатро як бор дар танзимоти Android фаъол кунед."
        isRussian -> "После включения службы Accessibility Miss Minutes сможет по вашей команде читать видимый экран открытого приложения, нажимать кнопки, вводить текст и отправлять сообщения или комментарии. Это не ограничено Instagram. Видимый текст экрана передаётся DeepSeek для понимания задачи. Опасные, финансовые и разрушающие действия блокируются или требуют отдельного подтверждения. Разрешение включается один раз в настройках Android."
        else -> "After you enable the Accessibility service, Miss Minutes can read the visible screen of the open app, tap controls, enter text, and send messages or comments when you ask. It is not limited to Instagram. Visible screen text is sent to DeepSeek to understand the task. Dangerous, financial and destructive actions are blocked or require separate confirmation. Enable this permission once in Android Settings."
    }
    val status = when {
        enabled && isTajik -> "Фаъол аст"
        enabled && isRussian -> "Включено"
        enabled -> "Enabled"
        isTajik -> "Ҳанӯз фаъол нест"
        isRussian -> "Ещё не включено"
        else -> "Not enabled"
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("accessibility_automation_panel"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Button(onClick = onOpenSettings, modifier = Modifier.testTag("accessibility_automation_open_settings")) {
                    Text(
                        when {
                            isTajik -> if (enabled) "Танзимотро кушо" else "Фаъол кардан"
                            isRussian -> if (enabled) "Открыть настройки" else "Включить"
                            else -> if (enabled) "Open settings" else "Enable"
                        }
                    )
                }
            }
        }
    }
}
