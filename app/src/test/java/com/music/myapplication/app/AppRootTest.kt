package com.music.myapplication.app

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootTest {

    @Test
    fun requestsNotificationPermissionWhenPlaybackNeedsItOnAndroid13Plus() {
        assertTrue(
            shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermission = "android.permission.POST_NOTIFICATIONS",
                hasPermission = false,
                hasRequestedInSession = false,
                hasCurrentTrack = true
            )
        )
    }

    @Test
    fun skipsNotificationPermissionWhenItIsNotNeeded() {
        assertFalse(
            shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION_CODES.S_V2,
                notificationPermission = null,
                hasPermission = false,
                hasRequestedInSession = false,
                hasCurrentTrack = true
            )
        )
        assertFalse(
            shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermission = "android.permission.POST_NOTIFICATIONS",
                hasPermission = true,
                hasRequestedInSession = false,
                hasCurrentTrack = true
            )
        )
        assertFalse(
            shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermission = "android.permission.POST_NOTIFICATIONS",
                hasPermission = false,
                hasRequestedInSession = true,
                hasCurrentTrack = true
            )
        )
        assertFalse(
            shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                notificationPermission = "android.permission.POST_NOTIFICATIONS",
                hasPermission = false,
                hasRequestedInSession = false,
                hasCurrentTrack = false
            )
        )
    }
}
