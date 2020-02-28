package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_mention_candidate.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.mms.GlideRequests
import org.whispersystems.signalservice.loki.api.LokiPublicChatAPI
import org.whispersystems.signalservice.loki.messaging.Mention

class MentionCandidateView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var mentionCandidate = Mention("", "")
        set(newValue) { field = newValue; update() }
    var glide: GlideRequests? = null
    var publicChatServer: String? = null
    var publicChatChannel: Long? = null

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    companion object {

        fun inflate(layoutInflater: LayoutInflater, parent: ViewGroup): MentionCandidateView {
            return layoutInflater.inflate(R.layout.view_mention_candidate, parent, false) as MentionCandidateView
        }
    }

    private fun update() {
        displayNameTextView.text = mentionCandidate.displayName
        profilePictureView.hexEncodedPublicKey = mentionCandidate.hexEncodedPublicKey
        profilePictureView.additionalHexEncodedPublicKey = null
        profilePictureView.isRSSFeed = false
        profilePictureView.glide = glide!!
        profilePictureView.update()
        if (publicChatServer != null && publicChatChannel != null) {
            val isUserModerator = LokiPublicChatAPI.isUserModerator(mentionCandidate.hexEncodedPublicKey, publicChatChannel!!, publicChatServer!!)
            moderatorIconImageView.visibility = if (isUserModerator) View.VISIBLE else View.GONE
        } else {
            moderatorIconImageView.visibility = View.GONE
        }
    }
}