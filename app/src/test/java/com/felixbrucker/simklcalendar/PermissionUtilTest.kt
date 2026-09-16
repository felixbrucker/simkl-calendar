package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.util.PermissionUtil
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilTest {

    @Test
    fun testHasExactAlarmPermission() {
        val context = mockk<Context>(relaxed = true)

        val result = PermissionUtil.hasExactAlarmPermission(context)

        assertTrue(result)
    }
}
