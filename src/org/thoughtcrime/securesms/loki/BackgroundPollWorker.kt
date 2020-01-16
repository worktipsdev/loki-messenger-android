package org.thoughtcrime.securesms.loki

import android.content.Context
import android.content.Intent
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.service.PersistentAlarmManagerListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.loki.api.LokiAPI
import org.whispersystems.signalservice.loki.utilities.Analytics
import java.util.concurrent.TimeUnit

class BackgroundPollWorker : PersistentAlarmManagerListener() {

    companion object {
        private val pollInterval = TimeUnit.MINUTES.toMillis(2)

        @JvmStatic
        fun schedule(context: Context) {
            BackgroundPollWorker().onReceive(context, Intent())
        }
    }

    override fun getNextScheduledExecutionTime(context: Context): Long {
        return TextSecurePreferences.getBackgroundPollTime(context)
    }

    override fun onAlarm(context: Context, scheduledTime: Long): Long {
        if (scheduledTime != 0L) {
            Analytics.shared.track("Performed Background Fetch")
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
            val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
            try {
                LokiAPI(userHexEncodedPublicKey, lokiAPIDatabase, (context.applicationContext as ApplicationContext).broadcaster).getMessages().map { messages ->
                    messages.forEach {
                        PushContentReceiveJob(context).processEnvelope(SignalServiceEnvelope(it))
                    }
                }
            } catch (exception: Throwable) {
                // Do nothing
            }
        }
        val nextTime = System.currentTimeMillis() + pollInterval
        TextSecurePreferences.setBackgroundPollTime(context, nextTime)
        return nextTime
    }
}
