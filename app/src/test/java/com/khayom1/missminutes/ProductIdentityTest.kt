package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductIdentityTest {
    @Test
    fun productIdentityIsStable() {
        assertEquals("Miss Minutes", ProductIdentity.NAME)
        assertEquals("com.khayom1.missminutes", ProductIdentity.APPLICATION_ID)
        assertEquals("0.1.0", ProductIdentity.VERSION_NAME)
    }
}
