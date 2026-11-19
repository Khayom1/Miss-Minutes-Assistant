package com.khayom1.missminutes.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSpeechInputProviderTest {
    @Test
    fun stopAndCloseAreIdempotentWithoutAnActiveRecognizer() {
        val provider = AndroidSpeechInputProvider(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        provider.stop()
        provider.stop()
        provider.close()
        provider.close()
        provider.stop()
    }

    @Test
    fun closedProviderRejectsNewRecognitionWithoutStartingARecognizer() {
        val provider = AndroidSpeechInputProvider(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        var results = 0
        var errors = 0

        provider.close()
        provider.start(
            locale = Locale.US,
            onResult = { results++ },
            onError = { errors++ }
        )

        assertEquals(0, results)
        assertEquals(1, errors)
    }
}
