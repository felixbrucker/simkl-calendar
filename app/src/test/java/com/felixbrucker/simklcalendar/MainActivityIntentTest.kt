package com.felixbrucker.simklcalendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.OAuthRepository
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import com.felixbrucker.simklcalendar.receiver.notification.makeOpenReleaseDetailViewIntent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityIntentTest {

    private lateinit var context: Context
    private lateinit var oAuthRepository: OAuthRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var mainActivity: MainActivity

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(Toast::class)
        val toastMock = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns toastMock

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val urlString = firstArg<String>()
            val mockUri = mockk<Uri>(relaxed = true)
            every { mockUri.scheme } answers { urlString.substringBefore("://").takeIf { urlString.contains("://") } }
            every { mockUri.host } answers { urlString.substringAfter("://").substringBefore("?").substringBefore("/") }
            every { mockUri.toString() } returns urlString
            every { mockUri.equals(any()) } answers {
                val other = firstArg<Any?>()
                other is Uri && other.toString() == urlString
            }
            every { mockUri.getQueryParameter(any()) } answers {
                val param = firstArg<String>()
                if (urlString.contains("$param=")) {
                    urlString.substringAfter("$param=").substringBefore("&")
                } else null
            }
            mockUri
        }

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk(relaxed = true)

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        oAuthRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)

        mainActivity = spyk(MainActivity())
        mainActivity.oAuthRepository = oAuthRepository
        mainActivity.notificationManager = notificationManager
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkConstructor(Intent::class)
        unmockkStatic(PendingIntent::class)
        unmockkStatic(Uri::class)
        unmockkStatic(Toast::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun testMakeOpenReleaseDetailViewIntentUsesCorrectUriAndExtra() {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        val uriSlot = slot<Uri>()

        item.makeOpenReleaseDetailViewIntent(context)
        verify { anyConstructed<Intent>().setData(capture(uriSlot)) }
        val capturedUriString = uriSlot.captured.toString()

        assertEquals("simklcalendar://release_detail", capturedUriString)
        verify { anyConstructed<Intent>().putExtra(MainActivity.EXTRA_ITEM_KEY, "v2_100_1_1") }
    }

    @Test
    fun testHandleNotificationNavigationWithNonMatchingUriDoesNotNavigate() = runTest {
        val intent = mockk<Intent>()
        every { intent.data } returns Uri.parse("simklcalendar://other")

        mainActivity.handleNotificationNavigation(intent)

        coVerify(exactly = 0) { notificationManager.removeActiveNotification(any()) }
        assertEquals(mainActivity.pendingReleaseDetailKey.value, null)
    }

    @Test
    fun testHandleNotificationNavigationWithMatchingUriNavigates() = runTest {
        val intent = mockk<Intent>()
        every { intent.data } returns Uri.parse("simklcalendar://release_detail")
        every { intent.getStringExtra(MainActivity.EXTRA_ITEM_KEY) } returns "v2_100_1_1"

        mainActivity.handleNotificationNavigation(intent)

        coVerify { notificationManager.removeActiveNotification("v2_100_1_1") }
        assertEquals(mainActivity.pendingReleaseDetailKey.value, "v2_100_1_1")
    }

    @Test
    fun testHandleOAuthUriWithNonMatchingUriDoesNotProcess() {
        val uri = Uri.parse("simklcalendar://release_detail")

        mainActivity.handleOAuthUri(uri)

        verify(exactly = 0) { oAuthRepository.onOAuthCodeReceived(any(), any(), any()) }
    }

    @Test
    fun testHandleOAuthUriWithMatchingUriProcessesCode() {
        val uri = Uri.parse("simklcalendar://auth?code=my_code&state=my_state")

        mainActivity.handleOAuthUri(uri)

        verify { oAuthRepository.onOAuthCodeReceived("my_code", "my_state", "simklcalendar://auth") }
    }

    @Test
    fun testHandleOAuthUriWithInvalidIssRejects() {
        val uri = Uri.parse("simklcalendar://auth?code=my_code&iss=https://fake.com")

        mainActivity.handleOAuthUri(uri)

        verify(exactly = 0) { oAuthRepository.onOAuthCodeReceived(any(), any(), any()) }
    }
}
