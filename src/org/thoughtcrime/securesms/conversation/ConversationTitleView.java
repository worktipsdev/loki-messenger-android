package org.thoughtcrime.securesms.conversation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import network.loki.messenger.R;

public class ConversationTitleView extends RelativeLayout {

  @SuppressWarnings("unused")
  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private View            content;
  private AvatarImageView avatar;
  private TextView        title;
  private TextView        subtitle;
  private ImageView       verified;
  private View            subtitleContainer;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.content           = ViewUtil.findById(this, R.id.content);
    this.title             = ViewUtil.findById(this, R.id.title);
    this.subtitle          = ViewUtil.findById(this, R.id.subtitle);
    this.verified          = ViewUtil.findById(this, R.id.verified_indicator);
    this.subtitleContainer = ViewUtil.findById(this, R.id.subtitle_container);
    this.avatar            = ViewUtil.findById(this, R.id.contact_photo_image);

    this.avatar.setEnabled(false);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(@NonNull GlideRequests glideRequests, @Nullable Recipient recipient) {
    if      (recipient == null) setComposeTitle();
    else                        setRecipientTitle(recipient);

    if (recipient != null && recipient.isBlocked()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_white_18dp, 0, 0, 0);
    } else if (recipient != null && recipient.isMuted()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_white_18dp, 0, 0, 0);
    } else {
      title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    if (recipient != null) {
      this.avatar.setAvatar(glideRequests, recipient, false);
    }
  }

  public void setVerified(boolean verified) {
    this.verified.setVisibility(verified ? View.VISIBLE : View.GONE);
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener listener) {
    this.content.setOnClickListener(listener);
    this.avatar.setOnClickListener(listener);
  }

  @Override
  public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
    this.content.setOnLongClickListener(listener);
    this.avatar.setOnLongClickListener(listener);
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    this.subtitle.setVisibility(View.GONE);
  }

  private void setRecipientTitle(Recipient recipient) {
    if      (recipient.isGroupRecipient())           setGroupRecipientTitle(recipient);
    else if (recipient.isLocalNumber())              setSelfTitle();
    else if (TextUtils.isEmpty(recipient.getName())) setNonContactRecipientTitle(recipient);
    else                                             setContactRecipientTitle(recipient);
  }

  private void setGroupRecipientTitle(Recipient recipient) {
    String localNumber = TextSecurePreferences.getLocalNumber(getContext());

    this.title.setText(recipient.getName());
    this.subtitle.setText(Stream.of(recipient.getParticipants())
                                .filter(r -> !r.getAddress().serialize().equals(localNumber))
                                .map(Recipient::toShortString)
                                .collect(Collectors.joining(", ")));

    this.subtitle.setVisibility(View.GONE);
    this.subtitleContainer.setVisibility(VISIBLE);
  }

  private void setSelfTitle() {
    this.title.setText(R.string.note_to_self);
    this.subtitleContainer.setVisibility(View.GONE);
  }

  @SuppressLint("SetTextI18n")
  private void setNonContactRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getAddress().serialize());
    this.subtitleContainer.setVisibility(VISIBLE);

    if (TextUtils.isEmpty(recipient.getProfileName())) {
      this.subtitle.setText(null);
      this.subtitle.setVisibility(View.GONE);
    } else {
      this.subtitle.setText("~" + recipient.getProfileName());
      this.subtitle.setVisibility(View.VISIBLE);
    }
  }

  private void setContactRecipientTitle(Recipient recipient) {
    this.title.setText(recipient.getName());

    if (TextUtils.isEmpty(recipient.getCustomLabel())) {
      this.subtitle.setText(null);
      this.subtitle.setVisibility(View.GONE);
      this.subtitleContainer.setVisibility(View.GONE);
    } else {
      this.subtitle.setText(recipient.getCustomLabel());
      this.subtitle.setVisibility(View.VISIBLE);
      this.subtitleContainer.setVisibility(View.VISIBLE);
    }
  }
}
