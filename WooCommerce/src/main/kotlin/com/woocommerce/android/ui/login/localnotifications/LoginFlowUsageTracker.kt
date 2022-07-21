package com.woocommerce.android.ui.login.localnotifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.ui.login.localnotifications.LocalNotificationWorker.Companion.PRE_LOGIN_LOCAL_NOTIFICATION_ID
import com.woocommerce.android.util.FeatureFlag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LoginFlowUsageTracker @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefsWrapper: AppPrefsWrapper
) {
    companion object {
        const val LOGIN_NOTIFICATION_TYPE_KEY = "Notification-type"
        const val NOTIFICATION_TEST_DELAY_IN_SECONDS = 5L
    }

    private val workManager = WorkManager.getInstance(appContext)

    fun onLoginSuccess() {
        cancelCurrentNotificationWorkRequest()
        NotificationManagerCompat.from(appContext).cancel(PRE_LOGIN_LOCAL_NOTIFICATION_ID)
    }

    fun scheduleNotification(notificationType: LoginSupportNotificationType) {
        if (FeatureFlag.PRE_LOGIN_NOTIFICATIONS.isEnabled() && !prefsWrapper.hasPreLoginNotificationBeenDisplayed()) {
            cancelCurrentNotificationWorkRequest()
            val notificationData = workDataOf(
                LOGIN_NOTIFICATION_TYPE_KEY to notificationType.name
            )
            val workRequest: WorkRequest =
                OneTimeWorkRequestBuilder<LocalNotificationWorker>()
                    .setInputData(notificationData)
                    .setInitialDelay(NOTIFICATION_TEST_DELAY_IN_SECONDS, TimeUnit.SECONDS)
                    .build()

            prefsWrapper.setPreLoginNotificationWorkRequestId(workRequest.id.toString())
            workManager.enqueue(workRequest)
        }
    }

    private fun cancelCurrentNotificationWorkRequest() {
        val currentWorkRequestId = prefsWrapper.getPreLoginNotificationWorkRequestId()
        if (currentWorkRequestId.isNotEmpty()) {
            workManager.cancelWorkById(UUID.fromString(currentWorkRequestId))
            prefsWrapper.setPreLoginNotificationWorkRequestId("")
        }
    }

    enum class LoginSupportNotificationType(val notification: String) {
        NO_LOGIN_INTERACTION("no_login_interaction"),
        LOGIN_ERROR_WRONG_EMAIL("wrong_email"),
        LOGIN_SITE_ADDRESS_ERROR("site_address_error"),
        DEFAULT_SUPPORT("default_support")
    }
}