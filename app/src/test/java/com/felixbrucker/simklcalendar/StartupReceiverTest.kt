package com.felixbrucker.simklcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import com.felixbrucker.simklcalendar.receiver.startup.StartupReceiver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class StartupReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockkObject(AlarmScheduler)
        mockkObject(NotificationManager)
        coEvery { AlarmScheduler.scheduleAllItemsAiredAlarms(any()) } returns Unit
        coEvery { NotificationManager.restoreActiveNotifications(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AlarmScheduler)
        unmockkObject(NotificationManager)
    }

    @Test
    fun testOnReceiveBootCompleted() {
        val receiver = spyk(StartupReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { AlarmScheduler.scheduleAllItemsAiredAlarms(context) }
        coVerify(timeout = 3000) { NotificationManager.restoreActiveNotifications(context) }
    }

    @Test
    fun testOnReceiveMyPackageReplaced() {
        val receiver = spyk(StartupReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MY_PACKAGE_REPLACED

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { AlarmScheduler.scheduleAllItemsAiredAlarms(context) }
        coVerify(timeout = 3000) { NotificationManager.restoreActiveNotifications(context) }
    }

    @Test
    fun testOnReceiveNullContextOrIntent() {
        val receiver = StartupReceiver()

        receiver.onReceive(null, null)
        receiver.onReceive(context, null)

        coVerify(exactly = 0) { AlarmScheduler.scheduleAllItemsAiredAlarms(any()) }
        coVerify(exactly = 0) { NotificationManager.restoreActiveNotifications(any()) }
    }

    @Test
    fun testOnReceiveOtherAction() {
        val receiver = StartupReceiver()
        val intent = mockk<Intent>()
        every { intent.action } returns "OTHER_ACTION"

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { AlarmScheduler.scheduleAllItemsAiredAlarms(any()) }
        coVerify(exactly = 0) { NotificationManager.restoreActiveNotifications(any()) }
    }
}
