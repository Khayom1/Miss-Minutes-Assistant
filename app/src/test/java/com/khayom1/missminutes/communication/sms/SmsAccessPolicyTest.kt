package com.khayom1.missminutes.communication.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAccessPolicyTest {
    @Test
    fun incomingSmsRequiresAssistantRoleAndReceivePermission() {
        assertTrue(SmsAccessPolicy.mayProcessIncomingSms(true, true))
        assertFalse(SmsAccessPolicy.mayProcessIncomingSms(false, true))
        assertFalse(SmsAccessPolicy.mayProcessIncomingSms(true, false))
        assertFalse(SmsAccessPolicy.mayProcessIncomingSms(false, false))
    }

    @Test
    fun inboxReadRequiresAssistantRoleAndReadPermission() {
        assertTrue(SmsAccessPolicy.mayReadInbox(true, true))
        assertFalse(SmsAccessPolicy.mayReadInbox(false, true))
        assertFalse(SmsAccessPolicy.mayReadInbox(true, false))
        assertFalse(SmsAccessPolicy.mayReadInbox(false, false))
    }
}
