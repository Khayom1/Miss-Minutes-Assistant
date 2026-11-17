package com.khayom1.missminutes.ui

import com.khayom1.missminutes.agent.AgentNodeStatus
import com.khayom1.missminutes.agent.AgentTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTaskLabelsTest {
    @Test
    fun taskStatusesAreTranslatedForAllSupportedLanguages() {
        assertEquals("Running", taskStatusLabel(AgentTaskStatus.RUNNING, AppLanguage.English))
        assertEquals("Выполняется", taskStatusLabel(AgentTaskStatus.RUNNING, AppLanguage.Russian))
        assertEquals("Иҷро шуда истодааст", taskStatusLabel(AgentTaskStatus.RUNNING, AppLanguage.Tajik))
        assertEquals("Recovery required", taskStatusLabel(AgentTaskStatus.RECOVERY_REQUIRED, AppLanguage.English))
        assertEquals("Требуется проверка", taskStatusLabel(AgentTaskStatus.RECOVERY_REQUIRED, AppLanguage.Russian))
        assertEquals("Санҷиши дастӣ лозим", taskStatusLabel(AgentTaskStatus.RECOVERY_REQUIRED, AppLanguage.Tajik))
    }

    @Test
    fun nodeStatusesAreTranslatedForAllSupportedLanguages() {
        assertEquals("Waiting for approval", nodeStatusLabel(AgentNodeStatus.WAITING_APPROVAL, AppLanguage.English))
        assertEquals("Ожидает подтверждения", nodeStatusLabel(AgentNodeStatus.WAITING_APPROVAL, AppLanguage.Russian))
        assertEquals("Интизори тасдиқ", nodeStatusLabel(AgentNodeStatus.WAITING_APPROVAL, AppLanguage.Tajik))
    }
}
