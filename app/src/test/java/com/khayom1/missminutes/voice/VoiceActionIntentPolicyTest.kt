package com.khayom1.missminutes.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionIntentPolicyTest {
    @Test
    fun reminderRequiresAnExplicitRequestInSupportedLanguages() {
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Remind me to call the dentist at 4 PM"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Поставь напоминание на завтра"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Ёдрас кун, ки пагоҳ ба духтур занг занам"))
    }

    @Test
    fun alarmRequiresAnExplicitRequestInSupportedLanguages() {
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "Set an alarm for 6 tomorrow"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "Разбуди меня в семь утра"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "Маро соати шаш бедор кун"))
    }

    @Test
    fun openingAnAppRequiresAnExplicitOpenCommand() {
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("open_app", "Open the calculator app"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("open_app", "Открой настройки"))
        assertTrue(VoiceActionIntentPolicy.isExplicitlyRequested("open_app", "Барномаро кушо"))
    }

    @Test
    fun unrelatedRequestsDoNotApproveConsequentialActions() {
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Read the calendar and summarize next week"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "What alarms are configured on my phone?"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("open_app", "Read the current screen"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("delete_account", "Delete my account"))
    }

    @Test
    fun explicitlyNegatedRequestsNeverGrantApproval() {
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Don't remind me to call the dentist"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "Do not set an alarm for tomorrow"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("set_alarm", "Не ставь будильник на шесть"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Не надо, не напомни мне"))
        assertFalse(VoiceActionIntentPolicy.isExplicitlyRequested("create_reminder", "Ин корро ёдрас накун"))
    }
}
