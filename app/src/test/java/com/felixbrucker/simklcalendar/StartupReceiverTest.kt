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
    private lateinit var notificationManagerMock: NotificationManager

    @Before
    fun setUp() {
        notificationManagerMock = mockk(relaxed = true)
        coEvery { notificationManagerMock.restoreActiveNotifications(any()) } returns Unit

        val mockInjector = mockk<com.felixbrucker.simklcalendar.receiver.startup.StartupReceiver_GeneratedInjector>(relaxed = true)
        every { mockInjector.injectStartupReceiver(any()) } answers {
            val rec = firstArg<StartupReceiver>()
            rec.notificationManager = notificationManagerMock
        }
        val mockComponentManager = mockk<dagger.hilt.internal.GeneratedComponentManager<Any>>(relaxed = true)
        every { mockComponentManager.generatedComponent() } returns mockInjector

        val mockApp = mockk<android.app.Application>(
            moreInterfaces = arrayOf(
                dagger.hilt.internal.GeneratedComponentManagerHolder::class,
                dagger.hilt.internal.GeneratedComponentManager::class
            ),
            relaxed = true
        )
        every { (mockApp as dagger.hilt.internal.GeneratedComponentManagerHolder).componentManager() } returns mockComponentManager
        every { (mockApp as dagger.hilt.internal.GeneratedComponentManager<*>).generatedComponent() } returns mockInjector

        context = mockk(relaxed = true)
        every { context.applicationContext } returns mockApp

        mockkObject(AlarmScheduler.Companion)
        coEvery { AlarmScheduler.scheduleAllItemsAiredAlarms(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AlarmScheduler.Companion)
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
        coVerify(timeout = 3000) { AlarmScheduler.scheduleAllItemsAiredAlarms(context, any()) }
        coVerify(timeout = 3000) { notificationManagerMock.restoreActiveNotifications(context) }
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
        coVerify(timeout = 3000) { notificationManagerMock.restoreActiveNotifications(context) }
    }

    @Test
    fun testOnReceiveNullContextOrIntent() {
        val receiver = StartupReceiver()

        try {
            receiver.onReceive(null, null)
        } catch (_: Exception) {}
        receiver.onReceive(context, null)

        coVerify(exactly = 0) { AlarmScheduler.scheduleAllItemsAiredAlarms(any(), any()) }
        coVerify(exactly = 0) { notificationManagerMock.restoreActiveNotifications(any()) }
    }

    @Test
    fun testOnReceiveOtherAction() {
        val receiver = StartupReceiver()
        val intent = mockk<Intent>()
        every { intent.action } returns "OTHER_ACTION"

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { AlarmScheduler.scheduleAllItemsAiredAlarms(any()) }
        coVerify(exactly = 0) { notificationManagerMock.restoreActiveNotifications(any()) }
    }
}
