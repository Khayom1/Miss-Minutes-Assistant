package com.khayom1.missminutes.permissions

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PermissionRequestPolicyTest {
    @Test
    fun requestsOnlyMissingPermissionsInOriginalOrder() {
        val requested = permissionsToRequest(
            listOf("android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS"),
            isGranted = { it == "android.permission.RECORD_AUDIO" }
        )

        assertArrayEquals(arrayOf("android.permission.POST_NOTIFICATIONS"), requested)
    }

    @Test
    fun removesDuplicatesAndAlreadyGrantedPermissions() {
        val requested = permissionsToRequest(
            listOf("permission.one", "permission.two", "permission.one"),
            isGranted = { it == "permission.two" }
        )

        assertArrayEquals(arrayOf("permission.one"), requested)
    }

    @Test
    fun returnsEmptyArrayWhenEveryPermissionIsGranted() {
        val requested = permissionsToRequest(
            listOf("permission.one", "permission.two"),
            isGranted = { true }
        )

        assertArrayEquals(emptyArray<String>(), requested)
    }
}
