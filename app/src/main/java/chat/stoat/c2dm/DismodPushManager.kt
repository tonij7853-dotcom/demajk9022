package chat.stoat.c2dm

import android.content.Context
import chat.stoat.StoatApplication
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.push.subscribePush
import chat.stoat.api.routes.push.unsubscribePush
import chat.stoat.persistence.KVStorage
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

object DismodPushManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun syncSubscription(context: Context = StoatApplication.instance) {
        scope.launch {
            try {
                val kvStorage = KVStorage(context)
                val sessionToken = StoatAPI.sessionToken.ifEmpty {
                    kvStorage.get("sessionToken").orEmpty()
                }
                if (sessionToken.isEmpty()) {
                    logcat(LogPriority.DEBUG) { "DismodPushManager: No session token available, skipping push sync" }
                    return@launch
                }

                val notificationsEnabled = kvStorage.getBoolean("notifications_enabled") ?: true
                if (!notificationsEnabled) {
                    logcat(LogPriority.DEBUG) { "DismodPushManager: Notifications disabled by user, skipping push sync" }
                    return@launch
                }

                val fcmToken = try {
                    withTimeoutOrNull(10000) {
                        FirebaseMessaging.getInstance().token.await()
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "DismodPushManager: Failed to retrieve FCM token: ${e.asLog()}" }
                    null
                }

                if (fcmToken.isNullOrEmpty()) {
                    logcat(LogPriority.WARN) { "DismodPushManager: FCM token is empty, skipping push registration" }
                    return@launch
                }

                val cachedToken = kvStorage.get("fcmToken")
                val isSubscribed = kvStorage.getBoolean("fcmSubscribed") ?: false
                if (isSubscribed && cachedToken == fcmToken) {
                    logcat(LogPriority.INFO) { "DismodPushManager: FCM token already registered and active" }
                    return@launch
                }

                logcat(LogPriority.INFO) { "DismodPushManager: Subscribing push with FCM token" }
                // 1. Primary: Stoat native FCM registration
                var success = subscribePush(endpoint = "fcm", auth = fcmToken)

                // 2. Secondary fallback: Web Push RFC 8291 endpoint mapping with ECDH P-256 keys
                if (!success) {
                    logcat(LogPriority.INFO) { "DismodPushManager: Native FCM failed, attempting Web Push VAPID shape fallback" }
                    val (p256dh, authSecret) = WebPushKeyManager.getOrCreateKeys(context)
                    val webPushEndpoint = "https://fcm.googleapis.com/fcm/send/$fcmToken"
                    success = subscribePush(
                        endpoint = webPushEndpoint,
                        auth = authSecret,
                        p256diffieHellman = p256dh
                    )
                }

                if (success) {
                    kvStorage.set("fcmToken", fcmToken)
                    kvStorage.set("fcmSubscribed", true)
                    kvStorage.remove("pushNotificationsRejected")
                    logcat(LogPriority.INFO) { "DismodPushManager: Successfully subscribed push notifications" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "DismodPushManager: Error in syncSubscription: ${e.asLog()}" }
            }
        }
    }

    suspend fun unregister(context: Context = StoatApplication.instance) {
        withContext(Dispatchers.IO) {
            try {
                val kvStorage = KVStorage(context)
                val token = kvStorage.get("fcmToken")
                if (!token.isNullOrEmpty()) {
                    withTimeoutOrNull(4000) {
                        unsubscribePush()
                    }
                }
                kvStorage.remove("fcmToken")
                kvStorage.remove("fcmSubscribed")
                logcat(LogPriority.INFO) { "DismodPushManager: Push subscription unregistered" }
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "DismodPushManager: Error unregistering push: ${e.asLog()}" }
            }
        }
    }

    fun handleNewToken(token: String, context: Context = StoatApplication.instance) {
        scope.launch {
            try {
                val kvStorage = KVStorage(context)
                val sessionToken = StoatAPI.sessionToken.ifEmpty {
                    kvStorage.get("sessionToken").orEmpty()
                }
                if (sessionToken.isEmpty()) {
                    kvStorage.set("fcmToken", token)
                    kvStorage.set("fcmSubscribed", false)
                    return@launch
                }

                logcat(LogPriority.INFO) { "DismodPushManager: Handling new FCM token onNewToken" }
                var success = subscribePush(endpoint = "fcm", auth = token)
                if (!success) {
                    val (p256dh, authSecret) = WebPushKeyManager.getOrCreateKeys(context)
                    val webPushEndpoint = "https://fcm.googleapis.com/fcm/send/$token"
                    success = subscribePush(
                        endpoint = webPushEndpoint,
                        auth = authSecret,
                        p256diffieHellman = p256dh
                    )
                }

                if (success) {
                    kvStorage.set("fcmToken", token)
                    kvStorage.set("fcmSubscribed", true)
                    kvStorage.remove("pushNotificationsRejected")
                    logcat(LogPriority.INFO) { "DismodPushManager: Successfully refreshed push token subscription" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "DismodPushManager: Failed to handle new FCM token: ${e.asLog()}" }
            }
        }
    }
}
