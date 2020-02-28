package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation.view.profilePictureView
import kotlinx.android.synthetic.main.view_user.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class UserView : LinearLayout {
    var user: String? = null

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_user, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun bind(user: String, isSelected: Boolean, glide: GlideRequests) {
        profilePictureView.hexEncodedPublicKey = user
        profilePictureView.additionalHexEncodedPublicKey = null
        profilePictureView.isRSSFeed = false
        profilePictureView.glide = glide
        profilePictureView.update()
        nameTextView.text = Recipient.from(context, Address.fromSerialized(user), false).name ?: "Unknown Contact"
        tickImageView.setImageResource(if (isSelected) R.drawable.ic_circle_check else R.drawable.ic_circle)
    }
    // endregion
}