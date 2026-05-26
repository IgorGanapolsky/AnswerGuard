package com.igorganapolsky.answerguard.review

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.igorganapolsky.answerguard.analytics.AnalyticsEvents
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoreReviewManagerTest {
    /** Backing store for the mocked SharedPreferences. */
    private val store = mutableMapOf<String, Any?>()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var packageManager: PackageManager
    private lateinit var analytics: AnalyticsService
    private lateinit var activity: Activity
    private lateinit var reviewManager: ReviewManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = mockk()
        editor = mockk()
        packageManager = mockk()
        analytics = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        reviewManager = mockk(relaxed = true)

        every {
            context.getSharedPreferences("review_prefs", Context.MODE_PRIVATE)
        } returns prefs
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.igorganapolsky.answerguard"

        // SharedPreferences reads delegate to the in-memory store.
        every { prefs.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Int>()
            (store[key] as? Int) ?: default
        }
        every { prefs.getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            (store[key] as? Long) ?: default
        }
        every { prefs.getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Boolean>()
            (store[key] as? Boolean) ?: default
        }
        every { prefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            (store[key] as? String) ?: default
        }

        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } answers {
            store[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            store[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            store[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.apply() } just Runs

        mockkStatic(ReviewManagerFactory::class)
        every { ReviewManagerFactory.create(context) } returns reviewManager
    }

    @After
    fun tearDown() {
        unmockkStatic(ReviewManagerFactory::class)
        store.clear()
    }

    // ---------- reviewPromptMilestoneForActionCount ----------

    @Test
    fun `milestone is null below five actions`() {
        assertNull(reviewPromptMilestoneForActionCount(0))
        assertNull(reviewPromptMilestoneForActionCount(4))
    }

    @Test
    fun `milestone is five between five and nineteen actions`() {
        assertEquals(5, reviewPromptMilestoneForActionCount(5))
        assertEquals(5, reviewPromptMilestoneForActionCount(19))
    }

    @Test
    fun `milestone is twenty between twenty and forty-nine actions`() {
        assertEquals(20, reviewPromptMilestoneForActionCount(20))
        assertEquals(20, reviewPromptMilestoneForActionCount(49))
    }

    @Test
    fun `milestone follows fifty-step ladder at and beyond fifty actions`() {
        assertEquals(50, reviewPromptMilestoneForActionCount(50))
        assertEquals(50, reviewPromptMilestoneForActionCount(99))
        assertEquals(100, reviewPromptMilestoneForActionCount(100))
        assertEquals(150, reviewPromptMilestoneForActionCount(175))
        assertEquals(200, reviewPromptMilestoneForActionCount(249))
    }

    // ---------- isEligibleForReviewPrompt ----------

    @Test
    fun `not eligible when below first milestone`() {
        assertFalse(
            isEligibleForReviewPrompt(
                actionCount = 3,
                lastPromptMilestone = 0,
                lastReviewTimestampMillis = 0L,
                nowMillis = 1_000_000L,
                minDaysBetweenRequests = 30L,
            ),
        )
    }

    @Test
    fun `not eligible when milestone already reached`() {
        assertFalse(
            isEligibleForReviewPrompt(
                actionCount = 10,
                lastPromptMilestone = 5,
                lastReviewTimestampMillis = 0L,
                nowMillis = 1_000_000L,
                minDaysBetweenRequests = 30L,
            ),
        )
    }

    @Test
    fun `eligible at first milestone with no prior review`() {
        assertTrue(
            isEligibleForReviewPrompt(
                actionCount = 5,
                lastPromptMilestone = 0,
                lastReviewTimestampMillis = 0L,
                nowMillis = 1_000_000L,
                minDaysBetweenRequests = 30L,
            ),
        )
    }

    @Test
    fun `eligible at next milestone when more than min days elapsed`() {
        val now = 100L * 24L * 60L * 60L * 1000L // 100 days in millis
        assertTrue(
            isEligibleForReviewPrompt(
                actionCount = 20,
                lastPromptMilestone = 5,
                lastReviewTimestampMillis = 1L,
                nowMillis = now,
                minDaysBetweenRequests = 30L,
            ),
        )
    }

    @Test
    fun `not eligible at next milestone when too soon since last review`() {
        val now = 10L * 24L * 60L * 60L * 1000L // 10 days
        assertFalse(
            isEligibleForReviewPrompt(
                actionCount = 20,
                lastPromptMilestone = 5,
                lastReviewTimestampMillis = 1L,
                nowMillis = now,
                minDaysBetweenRequests = 30L,
            ),
        )
    }

    // ---------- StoreReviewManager.recordAction ----------

    @Test
    fun `recordAction increments stored count`() {
        val manager = StoreReviewManager(context, analytics)

        manager.recordAction()
        assertEquals(1, store["action_count"])

        manager.recordAction()
        manager.recordAction()
        assertEquals(3, store["action_count"])
    }

    @Test
    fun `recordAction does not flag pending review below milestone`() {
        val manager = StoreReviewManager(context, analytics)

        manager.recordAction()
        manager.recordAction()

        assertFalse(manager.hasPendingReview())
    }

    @Test
    fun `recordAction flags pending review once milestone reached`() {
        val manager = StoreReviewManager(context, analytics)

        // 5 actions hits the first milestone (5).
        repeat(5) { manager.recordAction() }

        assertTrue(manager.hasPendingReview())
        assertEquals(true, store["pending_review"])
    }

    @Test
    fun `recordAction past milestone keeps pending review flagged`() {
        val manager = StoreReviewManager(context, analytics)
        repeat(6) { manager.recordAction() }

        assertTrue(manager.hasPendingReview())
    }

    @Test
    fun `hasPendingReview defaults to false on a fresh install`() {
        val manager = StoreReviewManager(context, analytics)
        assertFalse(manager.hasPendingReview())
    }

    // ---------- StoreReviewManager.requestReview ----------

    @Test
    fun `requestReview is a no-op when nothing is pending`() {
        val manager = StoreReviewManager(context, analytics)

        manager.requestReview(activity)

        verify(exactly = 0) { analytics.track(any(), any()) }
        verify(exactly = 0) { analytics.track(any<String>()) }
        verify(exactly = 0) { reviewManager.requestReviewFlow() }
    }

    @Test
    fun `requestReview clears pending flag and tracks REVIEW_PROMPT_REQUESTED`() {
        store["pending_review"] = true
        // Capture the listener but never invoke it — we want to confirm the
        // synchronous side effects (pending flag cleared, event tracked) before
        // the async task completes.
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        val task = mockk<Task<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } returns task

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        assertEquals(false, store["pending_review"])
        verify(exactly = 1) { analytics.track(AnalyticsEvents.REVIEW_PROMPT_REQUESTED) }
        assertTrue(listenerSlot.isCaptured)
    }

    @Test
    fun `requestReview success path launches flow and persists last review metadata`() {
        store["pending_review"] = true
        store["action_count"] = 5

        val pkgInfo = PackageInfo().apply { versionName = "1.2.3" }
        every { packageManager.getPackageInfo("com.igorganapolsky.answerguard", 0) } returns pkgInfo

        val reviewInfo = mockk<ReviewInfo>()
        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            // Invoke immediately so the success path executes inline.
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns true
        every { task.result } returns reviewInfo

        val launchTask = mockk<Task<Void>>(relaxed = true)
        every { reviewManager.launchReviewFlow(activity, reviewInfo) } returns launchTask

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        verify(exactly = 1) { analytics.track(AnalyticsEvents.REVIEW_PROMPT_REQUESTED) }
        verify(exactly = 1) { analytics.track(AnalyticsEvents.WRITE_REVIEW_TAPPED) }
        verify(exactly = 1) { reviewManager.launchReviewFlow(activity, reviewInfo) }

        assertTrue((store["last_review_timestamp"] as Long) > 0L)
        assertEquals("1.2.3", store["last_review_version"])
        assertEquals(5, store["last_prompt_milestone"])
    }

    @Test
    fun `requestReview success persists zero milestone when action count is below threshold`() {
        store["pending_review"] = true
        store["action_count"] = 2 // below first milestone, so milestone helper returns null

        val pkgInfo = PackageInfo().apply { versionName = "9.9.9" }
        every { packageManager.getPackageInfo("com.igorganapolsky.answerguard", 0) } returns pkgInfo

        val reviewInfo = mockk<ReviewInfo>()
        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns true
        every { task.result } returns reviewInfo
        every { reviewManager.launchReviewFlow(activity, reviewInfo) } returns mockk(relaxed = true)

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        assertEquals(0, store["last_prompt_milestone"])
        assertEquals("9.9.9", store["last_review_version"])
    }

    @Test
    fun `requestReview success persists unknown version when PackageManager returns null version`() {
        store["pending_review"] = true
        store["action_count"] = 5

        val pkgInfo = PackageInfo().apply { versionName = null }
        every { packageManager.getPackageInfo("com.igorganapolsky.answerguard", 0) } returns pkgInfo

        val reviewInfo = mockk<ReviewInfo>()
        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns true
        every { task.result } returns reviewInfo
        every { reviewManager.launchReviewFlow(activity, reviewInfo) } returns mockk(relaxed = true)

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        assertEquals("unknown", store["last_review_version"])
    }

    @Test
    fun `requestReview success persists unknown version when PackageManager throws`() {
        store["pending_review"] = true
        store["action_count"] = 5

        every {
            packageManager.getPackageInfo("com.igorganapolsky.answerguard", 0)
        } throws PackageManager.NameNotFoundException("missing")

        val reviewInfo = mockk<ReviewInfo>()
        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns true
        every { task.result } returns reviewInfo
        every { reviewManager.launchReviewFlow(activity, reviewInfo) } returns mockk(relaxed = true)

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        assertEquals("unknown", store["last_review_version"])
    }

    @Test
    fun `requestReview failure path does not launch flow or persist metadata`() {
        store["pending_review"] = true
        store["action_count"] = 5

        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns false

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)

        // Pending flag was cleared synchronously, REVIEW_PROMPT_REQUESTED tracked,
        // but no launchReviewFlow / WRITE_REVIEW_TAPPED / metadata persistence.
        verify(exactly = 1) { analytics.track(AnalyticsEvents.REVIEW_PROMPT_REQUESTED) }
        verify(exactly = 0) { analytics.track(AnalyticsEvents.WRITE_REVIEW_TAPPED) }
        verify(exactly = 0) { reviewManager.launchReviewFlow(any(), any()) }
        assertFalse(store.containsKey("last_review_timestamp"))
        assertFalse(store.containsKey("last_review_version"))
        assertFalse(store.containsKey("last_prompt_milestone"))
    }

    @Test
    fun `requestReview after success does not re-trigger when pending flag already cleared`() {
        // First, a successful flow clears the pending flag.
        store["pending_review"] = true
        store["action_count"] = 5
        every { packageManager.getPackageInfo("com.igorganapolsky.answerguard", 0) } returns
            PackageInfo().apply { versionName = "1.0" }

        val task = mockk<Task<ReviewInfo>>()
        val listenerSlot = slot<OnCompleteListener<ReviewInfo>>()
        every { reviewManager.requestReviewFlow() } returns task
        every { task.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(task)
            task
        }
        every { task.isSuccessful } returns true
        every { task.result } returns mockk()
        every { reviewManager.launchReviewFlow(any(), any()) } returns mockk(relaxed = true)

        val manager = StoreReviewManager(context, analytics)
        manager.requestReview(activity)
        manager.requestReview(activity) // second call should short-circuit

        verify(exactly = 1) { reviewManager.requestReviewFlow() }
        verify(exactly = 1) { reviewManager.launchReviewFlow(any(), any()) }
    }
}
