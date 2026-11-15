package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidActionsTest {
    @Test
    fun onlyExplicitHomeAndBackCommandsTriggerNavigation() {
        assertEquals(LocalCommandType.HOME, classifyLocalCommand("Перейди на главный экран!"))
        assertEquals(LocalCommandType.HOME, classifyLocalCommand("go home"))
        assertEquals(LocalCommandType.BACK, classifyLocalCommand("вернись назад"))
        assertEquals(LocalCommandType.BACK, classifyLocalCommand("press back"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("я хочу домой"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("что означает слово назад?"))
    }

    @Test
    fun timerAndAlarmRequireAnExplicitCommand() {
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("поставь таймер на 5 минут"))
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("set a timer for 5 minutes"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("будильник на 07:30"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("поставь мне будильник на 07:30"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("set an alarm for 07:30"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("как работает таймер?"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("расскажи про будильник"))
    }

    @Test
    fun appLaunchMustBeAnImperativeCommand() {
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("открой YouTube"))
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("launch YouTube"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("я открыл YouTube вчера"))
    }
}
