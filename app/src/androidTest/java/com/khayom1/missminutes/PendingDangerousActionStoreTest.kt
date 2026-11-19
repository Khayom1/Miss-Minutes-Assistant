package com.khayom1.missminutes

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.accessibility.PendingDangerousActionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingDangerousActionStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPendingAction() {
        context.getSharedPreferences("miss_minutes_dangerous_action", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun confirmationRequiresExactControlAndSameForegroundPackage() {
        assertTrue(
            PendingDangerousActionStore.save(
                context,
                "Delete account",
                "Delete account",
                "com.example.target"
            )
        )

        assertTrue(
            PendingDangerousActionStore.isConfirmationValid(
                context,
                "Delete account",
                "com.example.target",
                "I confirm Delete account"
            )
        )
        assertFalse(
            PendingDangerousActionStore.isConfirmationValid(
                context,
                "Transfer money",
                "com.example.target",
                "I confirm Transfer money"
            )
        )
        assertFalse(
            PendingDangerousActionStore.isConfirmationValid(
                context,
                "Delete account",
                "com.example.other",
                "I confirm Delete account"
            )
        )
        assertFalse(
            PendingDangerousActionStore.isConfirmationValid(
                context,
                "Delete account",
                "com.example.target",
                "Please delete the account"
            )
        )
    }

    @Test
    fun approvalIsBoundToCurrentContextAndCanOnlyBeConsumedOnce() {
        assertTrue(
            PendingDangerousActionStore.save(
                context,
                "Delete account",
                "Delete account",
                "com.example.target"
            )
        )

        assertFalse(
            PendingDangerousActionStore.consumeIfCurrentContext(
                context,
                "Delete account",
                "com.example.target",
                "Transfer money"
            )
        )
        assertNotNull(PendingDangerousActionStore.peek(context))

        assertTrue(
            PendingDangerousActionStore.consumeIfCurrentContext(
                context,
                "Delete account",
                "com.example.target",
                "Delete account"
            )
        )
        assertFalse(
            PendingDangerousActionStore.consumeIfCurrentContext(
                context,
                "Delete account",
                "com.example.target",
                "Delete account"
            )
        )
        assertNull(PendingDangerousActionStore.peek(context))
    }

    @Test
    fun expiredApprovalIsDeletedAndCannotBeReused() {
        assertTrue(
            PendingDangerousActionStore.save(
                context,
                "Delete account",
                "Delete account",
                "com.example.target"
            )
        )
        context.getSharedPreferences("miss_minutes_dangerous_action", Context.MODE_PRIVATE)
            .edit()
            .putLong("created_at", System.currentTimeMillis() - 2 * 60 * 1_000L - 1L)
            .commit()

        assertNull(PendingDangerousActionStore.peek(context))
        assertFalse(
            PendingDangerousActionStore.consumeIfCurrentContext(
                context,
                "Delete account",
                "com.example.target",
                "Delete account"
            )
        )
    }
}
