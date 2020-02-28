package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.toPx
import org.thoughtcrime.securesms.mms.GlideRequests
import org.whispersystems.signalservice.loki.messaging.Mention

class MentionCandidateSelectionView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr) {
    private var mentionCandidates = listOf<Mention>()
        set(newValue) { field = newValue; mentionCandidateSelectionViewAdapter.mentionCandidates = newValue }
    var glide: GlideRequests? = null
        set(newValue) { field = newValue; mentionCandidateSelectionViewAdapter.glide = newValue }
    var publicChatServer: String? = null
        set(newValue) { field = newValue; mentionCandidateSelectionViewAdapter.publicChatServer = publicChatServer }
    var publicChatChannel: Long? = null
        set(newValue) { field = newValue; mentionCandidateSelectionViewAdapter.publicChatChannel = publicChatChannel }
    var onMentionCandidateSelected: ((Mention) -> Unit)? = null

    private val mentionCandidateSelectionViewAdapter by lazy { Adapter(context) }

    private class Adapter(private val context: Context) : BaseAdapter() {
        var mentionCandidates = listOf<Mention>()
            set(newValue) { field = newValue; notifyDataSetChanged() }
        var glide: GlideRequests? = null
        var publicChatServer: String? = null
        var publicChatChannel: Long? = null

        override fun getCount(): Int {
            return mentionCandidates.count()
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItem(position: Int): Mention {
            return mentionCandidates[position]
        }

        override fun getView(position: Int, cellToBeReused: View?, parent: ViewGroup): View {
            val cell = cellToBeReused as MentionCandidateView? ?: MentionCandidateView.inflate(LayoutInflater.from(context), parent)
            val mentionCandidate = getItem(position)
            cell.glide = glide
            cell.mentionCandidate = mentionCandidate
            cell.publicChatServer = publicChatServer
            cell.publicChatChannel = publicChatChannel
            return cell
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        clipToOutline = true
        adapter = mentionCandidateSelectionViewAdapter
        mentionCandidateSelectionViewAdapter.mentionCandidates = mentionCandidates
        setOnItemClickListener { _, _, position, _ ->
            onMentionCandidateSelected?.invoke(mentionCandidates[position])
        }
    }

    fun show(mentionCandidates: List<Mention>, threadID: Long) {
        val publicChat = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        if (publicChat != null) {
            publicChatServer = publicChat.server
            publicChatChannel = publicChat.channel
        }
        this.mentionCandidates = mentionCandidates
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = toPx(Math.min(mentionCandidates.count(), 4) * 44, resources)
        this.layoutParams = layoutParams
    }

    fun hide() {
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = 0
        this.layoutParams = layoutParams
    }
}