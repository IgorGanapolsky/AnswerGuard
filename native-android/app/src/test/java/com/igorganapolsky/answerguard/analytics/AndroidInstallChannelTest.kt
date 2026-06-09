package com.igorganapolsky.answerguard.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidInstallChannelTest {
    @Test
    fun play_store_installer_maps_to_play_store_channel() {
        assertThat(AndroidInstallChannel.fromInstallerPackageName("com.android.vending"))
            .isEqualTo(AndroidInstallChannel.PLAY_STORE)
    }

    @Test
    fun sideload_installer_maps_to_non_play_install() {
        assertThat(
            AndroidInstallChannel.fromInstallerPackageName("com.google.android.packageinstaller"),
        ).isEqualTo(AndroidInstallChannel.NON_PLAY_INSTALL)
    }
}
