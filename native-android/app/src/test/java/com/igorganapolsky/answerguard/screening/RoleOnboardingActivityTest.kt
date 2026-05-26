package com.igorganapolsky.answerguard.screening

import android.app.Activity
import android.app.Application
import android.app.role.RoleManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric coverage for [RoleOnboardingActivity].
 *
 * Drives the activity through:
 *  - The path where the role is available and not yet held (must launch RoleManager intent).
 *  - The path where the role is unavailable / already held (must finish with RESULT_OK).
 *  - The activity-result callback that forwards resultCode and finishes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoleOnboardingActivityTest {

    @Test
    fun `requests ROLE_CALL_SCREENING via RoleManager when available and not held`() {
        val controller = Robolectric.buildActivity(RoleOnboardingActivity::class.java)
        val activity = controller.get()

        val roleManager = activity.getSystemService(RoleManager::class.java)
        shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        // Real RoleManager is available under Robolectric; by default the role is
        // not held, so the activity should launch the request intent and remain alive
        // (waiting for the system role chooser result).
        controller.create()

        // Activity should have launched the role request — it isn't finished yet,
        // because the result hasn't come back.
        val shadow = shadowOf(activity)
        val nextStarted = shadow.peekNextStartedActivityForResult()
        assertThat(nextStarted).isNotNull()
        assertThat(activity.isFinishing).isFalse()
    }

    @Test
    fun `finishes with RESULT_OK when role is already held`() {
        val controller = Robolectric.buildActivity(RoleOnboardingActivity::class.java)
        val activity = controller.get()

        val roleManager = activity.getSystemService(RoleManager::class.java)
        shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
        // Force "role held" so the early-exit branch is taken.
        shadowOf(roleManager).addHeldRole(RoleManager.ROLE_CALL_SCREENING)

        controller.create()

        val shadow = shadowOf(activity)
        // No role request intent launched
        assertThat(shadow.peekNextStartedActivityForResult()).isNull()
        // Activity finished with RESULT_OK
        assertThat(activity.isFinishing).isTrue()
        assertThat(shadow.resultCode).isEqualTo(Activity.RESULT_OK)
    }

    @Test
    fun `forwards result and finishes after role request returns`() {
        val controller = Robolectric.buildActivity(RoleOnboardingActivity::class.java)
        val activity = controller.get()

        val roleManager = activity.getSystemService(RoleManager::class.java)
        shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        controller.create().start().resume()

        val shadow = shadowOf(activity)
        val started = shadow.peekNextStartedActivityForResult()
        assertThat(started).isNotNull()

        // Simulate the system returning a CANCELED result for the role request.
        activity.activityResultRegistry.dispatchResult(
            started.requestCode,
            Activity.RESULT_CANCELED,
            null
        )

        assertThat(activity.isFinishing).isTrue()
        assertThat(shadow.resultCode).isEqualTo(Activity.RESULT_CANCELED)
    }
}
