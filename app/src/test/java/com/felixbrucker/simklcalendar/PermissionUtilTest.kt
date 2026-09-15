package com.felixbrucker.simklcalendar.data.util

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilTest {

    @Test
    fun testHasExactAlarmPermissionPreAndroid12() {
        val context = mockk<Context>()
        // Default Build.VERSION.SDK_INT in standard JUnit environment is usually 0, so it returns true
        assertTrue(PermissionUtil.hasExactAlarmPermission(context))
    }
}
