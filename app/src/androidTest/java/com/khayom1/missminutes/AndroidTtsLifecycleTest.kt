package com.khayom1.missminutes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.ai.AndroidTtsProvider
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTtsLifecycleTest {
    @Test
    fun speechRequestCompletesOrFailsWithoutHangingAndProviderClosesSafely() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AndroidTtsProvider(context, Locale.US)
        val callback = CountDownLatch(1)

        try {
            provider.speak(
                text = "Miss Minutes is ready.",
                onDone = { callback.countDown() },
                onError = { callback.countDown() }
            )
            assertTrue("TTS must report completion or a bounded error", callback.await(15, TimeUnit.SECONDS))
            provider.stop()
        } finally {
            provider.close()
            provider.close()
        }
    }
}
