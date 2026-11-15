package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidActionsTest {
    @Test fun onlyExplicitHomeAndBackCommandsTriggerNavigation() {
        assertEquals(LocalCommandType.HOME, classifyLocalCommand("Перейди на главный экран!"))
        assertEquals(LocalCommandType.HOME, classifyLocalCommand("домой"))
        assertEquals(LocalCommandType.BACK, classifyLocalCommand("вернись назад"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("я хочу домой"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("что означает слово назад?"))
    }
    @Test fun timerAndAlarmRequireAnExplicitCommand() {
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("поставь таймер на 5 минут"))
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("таймер на 5 минут"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("будильник на 07:30"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("поставь мне будильник на 07:30"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("как работает таймер?"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("расскажи про будильник"))
    }
    @Test fun appLaunchMustBeAnImperativeCommand() {
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("открой YouTube"))
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("запусти календарь"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("я открыл YouTube вчера"))
    }
}
