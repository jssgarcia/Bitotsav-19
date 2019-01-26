package `in`.bitotsav.shared.workers

import `in`.bitotsav.notification.utils.deleteFcmTokenAsync
import `in`.bitotsav.notification.utils.sendFcmTokenAsync
import `in`.bitotsav.shared.workers.FcmTokenWorkType.valueOf
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

private const val TAG = "FcmTokenWorker"

enum class FcmTokenWorkType {
    SEND_TOKEN,
    DELETE_TOKEN
}

class FcmTokenWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {
        val type = valueOf(inputData.getString("type")!!)
        val authToken = "" //TODO: Get from shared Prefs
        val fcmToken = inputData.getString("fcmToken") ?: throw Exception("Fcm token not found")

        return runBlocking {
            try {
                when (type) {
                    FcmTokenWorkType.SEND_TOKEN -> sendFcmTokenAsync(authToken, fcmToken).await()
                    FcmTokenWorkType.DELETE_TOKEN -> deleteFcmTokenAsync(authToken, fcmToken).await()
                }
                return@runBlocking Result.success()
            } catch (e: Exception) {
                Log.d(TAG, e.message)
                return@runBlocking Result.retry()
            }
        }
    }
}