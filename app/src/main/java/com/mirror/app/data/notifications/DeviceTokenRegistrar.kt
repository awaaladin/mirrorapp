package com.mirror.app.data.notifications

import com.mirror.app.data.network.DeviceTokenRequestDto
import com.mirror.app.data.network.MirrorBackendApi
import java.io.IOException

/**
 * Registers an FCM token with mirrorcentral so this device can receive "new version available"
 * push notifications. Shared by MainActivity (registers the current token on every app launch,
 * since a token already issued before this device last opened the app wouldn't otherwise trigger
 * FirebaseMessagingService.onNewToken again) and MirrorMessagingService (registers on refresh).
 * Fire-and-forget: a failure here just means this device won't get push updates until the next
 * successful attempt, not a user-facing error.
 */
suspend fun registerDeviceToken(localApi: MirrorBackendApi, deployedApi: MirrorBackendApi, token: String) {
    try {
        localApi.registerDeviceToken(DeviceTokenRequestDto(token, "android"))
    } catch (e: IOException) {
        try {
            deployedApi.registerDeviceToken(DeviceTokenRequestDto(token, "android"))
        } catch (e2: IOException) {
            // Offline - nothing to do, see fire-and-forget note above.
        }
    }
}
