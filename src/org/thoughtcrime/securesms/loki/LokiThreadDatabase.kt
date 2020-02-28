package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.redesign.utilities.*
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.loki.LokiSessionResetStatus
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.loki.api.LokiPublicChat
import org.whispersystems.signalservice.loki.messaging.LokiThreadDatabaseProtocol
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class LokiThreadDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiThreadDatabaseProtocol {
    var delegate: LokiThreadDatabaseDelegate? = null

    companion object {
        private val friendRequestTableName = "loki_thread_friend_request_database"
        private val sessionResetTableName = "loki_thread_session_reset_database"
        val publicChatTableName = "loki_public_chat_database"
        val threadID = "thread_id"
        private val friendRequestStatus = "friend_request_status"
        private val sessionResetStatus = "session_reset_status"
        val publicChat = "public_chat"
        @JvmStatic val createFriendRequestTableCommand = "CREATE TABLE $friendRequestTableName ($threadID INTEGER PRIMARY KEY, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createSessionResetTableCommand = "CREATE TABLE $sessionResetTableName ($threadID INTEGER PRIMARY KEY, $sessionResetStatus INTEGER DEFAULT 0);"
        @JvmStatic val createPublicChatTableCommand = "CREATE TABLE $publicChatTableName ($threadID INTEGER PRIMARY KEY, $publicChat TEXT);"
    }

    override fun getThreadID(hexEncodedPublicKey: String): Long {
        val address = Address.fromSerialized(hexEncodedPublicKey)
        val recipient = Recipient.from(context, address, false)
        return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
    }

    fun getThreadID(messageID: Long): Long {
        return DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageID)
    }

    fun getFriendRequestStatus(threadID: Long): LokiThreadFriendRequestStatus {
        if (threadID < 0) { return LokiThreadFriendRequestStatus.NONE }

        // Loki - Friend request logic doesn't apply to group chats, always treat them as friends
        val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadID)
        if (recipient != null && recipient.isGroupRecipient) { return LokiThreadFriendRequestStatus.FRIENDS; }

        val database = databaseHelper.readableDatabase
        val result = database.get(friendRequestTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiThreadFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiThreadFriendRequestStatus.NONE
        }
    }

    override fun setFriendRequestStatus(threadID: Long, friendRequestStatus: LokiThreadFriendRequestStatus) {
        if (threadID < 0) { return }
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(friendRequestTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
        delegate?.handleThreadFriendRequestStatusChanged(threadID)
    }

    fun hasPendingFriendRequest(threadID: Long): Boolean {
        val friendRequestStatus = getFriendRequestStatus(threadID)
        return friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENDING || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENT
            || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_RECEIVED
    }

    fun getSessionResetStatus(hexEncodedPublicKey: String): LokiSessionResetStatus {
        val threadID = getThreadID(hexEncodedPublicKey)
        val database = databaseHelper.readableDatabase
        val result = database.get(sessionResetTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(sessionResetStatus)
        }
        return if (result != null) {
            LokiSessionResetStatus.values().first { it.rawValue == result }
        } else {
            LokiSessionResetStatus.NONE
        }
    }

    fun setSessionResetStatus(hexEncodedPublicKey: String, sessionResetStatus: LokiSessionResetStatus) {
        val threadID = getThreadID(hexEncodedPublicKey)
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.sessionResetStatus, sessionResetStatus.rawValue)
        database.insertOrUpdate(sessionResetTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
    }

    fun getAllPublicChats(): Map<Long, LokiPublicChat> {
        val database = databaseHelper.readableDatabase
        var cursor: Cursor? = null
        val result = mutableMapOf<Long, LokiPublicChat>()
        try {
            cursor = database.rawQuery("select * from $publicChatTableName", null)
            while (cursor != null && cursor.moveToNext()) {
                val threadID = cursor.getLong(threadID)
                val string = cursor.getString(publicChat)
                val publicChat = LokiPublicChat.fromJSON(string)
                if (publicChat != null) { result[threadID] = publicChat }
            }
        } catch (e: Exception) {
            // Do nothing
        }  finally {
            cursor?.close()
        }
        return result
    }

    fun getAllPublicChatServers(): Set<String> {
        return getAllPublicChats().values.fold(setOf()) { set, chat -> set.plus(chat.server) }
    }

    override fun getPublicChat(threadID: Long): LokiPublicChat? {
        if (threadID < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(publicChatTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            val publicChatAsJSON = cursor.getString(publicChat)
            LokiPublicChat.fromJSON(publicChatAsJSON)
        }
    }

    override fun setPublicChat(publicChat: LokiPublicChat, threadID: Long) {
        if (threadID < 0) { return }
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.publicChat, JsonUtil.toJson(publicChat.toJSON()))
        database.insertOrUpdate(publicChatTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }

    override fun removePublicChat(threadID: Long) {
        databaseHelper.writableDatabase.delete(publicChatTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }

    fun addSessionRestoreDevice(threadID: Long, hexEncodedPublicKey: String) {
        val devices = getSessionRestoreDevices(threadID).toMutableSet()
        if (devices.add(hexEncodedPublicKey)) {
            TextSecurePreferences.setStringPreference(context, "session_restore_devices_$threadID", devices.joinToString(","))
            delegate?.handleSessionRestoreDevicesChanged(threadID)
        }
    }

    fun getSessionRestoreDevices(threadID: Long): Set<String> {
        return TextSecurePreferences.getStringPreference(context, "session_restore_devices_$threadID", "")
            .split(",")
            .filter { PublicKeyValidation.isValid(it) }
            .toSet()
    }

    fun removeAllSessionRestoreDevices(threadID: Long) {
        TextSecurePreferences.setStringPreference(context, "session_restore_devices_$threadID", "")
        delegate?.handleSessionRestoreDevicesChanged(threadID)
    }
}