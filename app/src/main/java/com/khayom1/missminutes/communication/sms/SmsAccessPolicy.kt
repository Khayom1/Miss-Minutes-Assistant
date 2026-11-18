package com.khayom1.missminutes.communication.sms

/** Role and permission checks are deliberately independent and must both pass. */
object SmsAccessPolicy {
    fun mayProcessIncomingSms(assistantRoleHeld: Boolean, receiveSmsGranted: Boolean) =
        assistantRoleHeld && receiveSmsGranted

    fun mayReadInbox(assistantRoleHeld: Boolean, readSmsGranted: Boolean) =
        assistantRoleHeld && readSmsGranted
}
