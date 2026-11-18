package com.khayom1.missminutes

import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OptionalTelephonyFeatureManifestTest {
    @Test
    fun telephonyHardwareIsOptionalForSmsCapableBuilds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_CONFIGURATIONS
        )

        val telephonyFeature = packageInfo.reqFeatures
            ?.firstOrNull { it.name == PackageManager.FEATURE_TELEPHONY }

        assertNotNull(
            "The manifest must declare telephony so SMS permissions do not exclude tablets/ChromeOS.",
            telephonyFeature
        )
        assertFalse(
            "Telephony must remain optional; SMS features are conditional on device support.",
            telephonyFeature!!.flags and FeatureInfo.FLAG_REQUIRED != 0
        )
    }
}
