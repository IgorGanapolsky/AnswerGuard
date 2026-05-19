package com.igorganapolsky.answerguard.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleOnboardingTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        // RoleManager was added in Android 10 (Q)
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        
        // Ensure the app doesn't already hold the role for a clean test
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            // We can't easily revoke a role via API, but we can assume for now 
            // or use shell commands if permitted.
        }
    }

    @Test
    fun testRoleRequestDialogAppears() {
        // Launch the RoleOnboardingActivity directly
        val intent = Intent(context, RoleOnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        // Wait for the system role request dialog. 
        // Note: The text on the dialog is system-dependent but usually contains "AnswerGuard".
        val dialogFound = device.wait(Until.hasObject(By.textContains("AnswerGuard")), 5000)
        assertThat(dialogFound).isTrue()

        // Optionally, simulate clicking "Set as default" (button text varies by locale)
        // val setAsDefaultButton = device.findObject(By.text("Set as default"))
        // setAsDefaultButton?.click()
    }
}
