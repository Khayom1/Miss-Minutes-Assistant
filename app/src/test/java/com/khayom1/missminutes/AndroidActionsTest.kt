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
    @Test fun timerDurationParserHandlesAllLocalizedUnitsAndBounds() {\n        assertEquals(300, parseTimerDurationSeconds("таймер мон барои 5 дақиқа"))\n        assertEquals(30, parseTimerDurationSeconds("set a timer for 30 seconds"))\n        assertEquals(7200, parseTimerDurationSeconds("поставь таймер на 2 часа"))\n        assertEquals(null, parseTimerDurationSeconds("таймер мон"))\n        assertEquals(null, parseTimerDurationSeconds("set timer for 999 hours"))\n    }\n\n    @Test fun timerAndAlarmRequireAnExplicitCommand() {
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("поставь таймер на 5 минут"))
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("таймер на 5 минут"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("будильник на 07:30"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("поставь мне будильник на 07:30"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("как работает таймер?"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("расскажи про будильник"))
    }
    @Test fun timerDurationsParseAcrossAllThreeLanguages() {
        assertEquals(300, parseTimerDurationSeconds("set a timer for 5 minutes"))
        assertEquals(5, parseTimerDurationSeconds("таймер мон барои 5 сония"))
        assertEquals(7200, parseTimerDurationSeconds("set timer for 2 hours"))
        assertEquals(300, parseTimerDurationSeconds("таймер мон барои 5 дақиқа"))
        assertEquals(null, parseTimerDurationSeconds("0 minutes"))
    }
    @Test fun localCommandsAreRecognizedInTajikAndEnglish() {
        assertEquals(LocalCommandType.HOME, classifyLocalCommand("go home", "en"))
        assertEquals(LocalCommandType.BACK, classifyLocalCommand("ақиб", "tg"))
        assertEquals(LocalCommandType.TIMER, classifyLocalCommand("таймер мон барои 5 дақиқа", "tg"))
        assertEquals(LocalCommandType.ALARM, classifyLocalCommand("set an alarm for 07:30", "en"))
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("open YouTube", "en"))
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("кушо YouTube", "tg"))
    }
    @Test fun appLaunchMustBeAnImperativeCommand() {
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("открой YouTube"))
        assertEquals(LocalCommandType.OPEN_APP, classifyLocalCommand("запусти календарь"))
        assertEquals(LocalCommandType.NONE, classifyLocalCommand("я открыл YouTube вчера"))
    }
}
