package `in`.bitotsav.network.fcm

import `in`.bitotsav.MainActivity
import `in`.bitotsav.events.data.EventRepository
import `in`.bitotsav.feed.data.Feed
import `in`.bitotsav.feed.data.FeedRepository
import `in`.bitotsav.feed.data.FeedType
import `in`.bitotsav.network.NetworkJobService
import `in`.bitotsav.notification.Channel
import `in`.bitotsav.notification.displayNotification
import `in`.bitotsav.shared.Singleton
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.firebase.jobdispatcher.Constraint
import com.firebase.jobdispatcher.Lifetime
import com.firebase.jobdispatcher.RetryStrategy
import com.firebase.jobdispatcher.Trigger
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.*

//TODO("Complete this list")
private enum class UpdateType{
    EVENT,
    RESULT,
    ANNOUNCEMENT,
    PM,
    ALL_EVENTS,
    ALL_TEAMS
}

class DefaultFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMsgService"
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // TODO(developer): Handle FCM messages here.
        Log.d(TAG, "From: ${remoteMessage?.from}")

        // Check if message contains a data payload.
        remoteMessage?.data?.isNotEmpty()?.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)

            val updateType: UpdateType
            try {
                updateType = UpdateType.valueOf(remoteMessage.data["type"] ?: return)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.message)
                return
            }

            if (UpdateType.ALL_EVENTS == updateType || UpdateType.ALL_TEAMS == updateType) {
//                TODO("Schedule Job")
                return
            }

            val title = remoteMessage.data["title"] ?: return
            val content = remoteMessage.data["content"] ?: return
            val timestamp = remoteMessage.data["timestamp"]?.toLong() ?: System.currentTimeMillis()
            val feedId = remoteMessage.data["feedId"]?.toLong() ?: return
            val feedType = FeedType.valueOf(updateType.name)
            var channel = Channel.valueOf(updateType.name)

            val database = Singleton.database.getInstance(applicationContext)

            when(updateType) {
                UpdateType.ANNOUNCEMENT, UpdateType.PM -> {
                    val feed = Feed(
                        feedId,
                        title,
                        content,
                        feedType.name,
                        timestamp,
                        false,
                        null,
                        null
                    )
                    CoroutineScope(Dispatchers.IO).async {
                        FeedRepository(database.feedDao()).insert(feed)
                    }
//                    TODO("Pass appropriate intent")
                    val intent = Intent(this, MainActivity::class.java)
                    displayNotification(title, content, timestamp, channel, intent, this)
                }
                else -> {
                    val eventId = remoteMessage.data["eventId"]?.toInt() ?: return
                    val deferredIsStarred = CoroutineScope(Dispatchers.IO).async {
                        EventRepository(database.eventDao()).isStarred(eventId)
                    }
                    val deferredEventName = CoroutineScope(Dispatchers.IO).async {
                        EventRepository(database.eventDao()).getEventName(eventId)
                    }
//                    TODO("Refresh data from server here")
//                    TODO("Schedule Job")
                    val isStarred = runBlocking { deferredIsStarred.await() } ?: return
                    val eventName = runBlocking { deferredEventName.await() } ?: return
                    val feed = Feed(
                        feedId,
                        title,
                        content,
                        feedType.name,
                        timestamp,
                        isStarred,
                        eventId,
                        eventName
                    )
                    CoroutineScope(Dispatchers.IO).async {
                        FeedRepository(database.feedDao()).insert(feed)
                    }
                    if (isStarred) channel = Channel.STARRED
//                    TODO("Pass appropriate intent")
                    val intent = Intent(this, MainActivity::class.java)
                    displayNotification(title, content, timestamp, channel, intent, applicationContext)
                }
            }
        }
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String?) {
        Log.d(TAG, "Refreshed token: $token")
//        TODO("Check logged in status and send token if true")
        if (/*Check if user is logged in*/false) sendTokenToServer(token)
    }

    /**
     * Schedule a job using FirebaseJobDispatcher.
     */
    private fun scheduleJob(bundle: Bundle, tag: String) {
        val dispatcher = Singleton.dispatcher.getInstance(applicationContext)
        Log.d(TAG, "Scheduling new job")
        val random = Random()
        val timeDelay = random.nextInt(5)
        val myJob = dispatcher.newJobBuilder()
            .setService(NetworkJobService::class.java)
            .setTag(tag)
            .setRecurring(false)
            .setLifetime(Lifetime.FOREVER)
            .setTrigger(Trigger.executionWindow(timeDelay, 10))
            .setReplaceCurrent(false)
            .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
            .setConstraints(
                Constraint.ON_ANY_NETWORK
            )
            .setExtras(bundle)
            .build()

        dispatcher.mustSchedule(myJob)
    }

    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM InstanceID token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    fun sendTokenToServer(token: String?) {
//        TODO("Call this method on login")
        // TODO: Implement this method to send token to your app server.
        // Decide whether to use sharedPref or continue with this approach
        // https://codelabs.developers.google.com/codelabs/kotlin-coroutines/#0
        if (token.isNullOrEmpty()) {
            FirebaseInstanceId.getInstance().instanceId
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "getInstanceId failed", task.exception)
                        return@OnCompleteListener
                    }

                    val token = task.result?.token

                    val msg = "InstanceID Token: $token"
                    Log.d(TAG, msg)
                    TODO("Send token here")

                })
        } else {
            TODO("Send token here")
        }
    }
}