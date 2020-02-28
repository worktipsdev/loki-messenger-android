package org.thoughtcrime.securesms.jobs;

import android.support.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.loki.api.LokiFileServerAPI;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class AttachmentUploadJob extends BaseJob implements InjectableType {

  public static final String KEY = "AttachmentUploadJob";

  private static final String TAG = AttachmentUploadJob.class.getSimpleName();

  private static final String KEY_ROW_ID      = "row_id";
  private static final String KEY_UNIQUE_ID   = "unique_id";
  private static final String KEY_DESTINATION = "destination";

  private AttachmentId               attachmentId;
  private Address                    destination;
  @Inject SignalServiceMessageSender messageSender;

  public AttachmentUploadJob(AttachmentId attachmentId, Address destination) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(3)
                           .build(),
         attachmentId, destination);
  }

  private AttachmentUploadJob(@NonNull Job.Parameters parameters, @NonNull AttachmentId attachmentId, Address destination) {
    super(parameters);
    this.attachmentId = attachmentId;
    this.destination = destination;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                             .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                             .putString(KEY_DESTINATION, destination.serialize())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    AttachmentDatabase database           = DatabaseFactory.getAttachmentDatabase(context);
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new IllegalStateException("Cannot find the specified attachment.");
    }
    
    // Only upload attachment if necessary
    if (databaseAttachment.getUrl().isEmpty()) {
      MediaConstraints mediaConstraints = MediaConstraints.getPushMediaConstraints();
      Attachment scaledAttachment = scaleAndStripExif(database, mediaConstraints, databaseAttachment);
      SignalServiceAttachment localAttachment = getAttachmentFor(scaledAttachment);
      SignalServiceAttachmentPointer remoteAttachment = messageSender.uploadAttachment(localAttachment.asStream(), databaseAttachment.isSticker(), new SignalServiceAddress(destination.serialize()));
      Attachment attachment = PointerAttachment.forPointer(Optional.of(remoteAttachment), null, databaseAttachment.getFastPreflightId()).get();

      database.updateAttachmentAfterUpload(databaseAttachment.getAttachmentId(), attachment);
    }
  }

  @Override
  public void onCanceled() { }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private SignalServiceAttachment getAttachmentFor(Attachment attachment) {
    try {
      if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.getContentType())
                                    .withLength(attachment.getSize())
                                    .withFileName(attachment.getFileName())
                                    .withVoiceNote(attachment.isVoiceNote())
                                    .withWidth(attachment.getWidth())
                                    .withHeight(attachment.getHeight())
                                    .withCaption(attachment.getCaption())
                                    .withListener((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress)))
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  private Attachment scaleAndStripExif(@NonNull AttachmentDatabase attachmentDatabase,
                                       @NonNull MediaConstraints constraints,
                                       @NonNull Attachment attachment)
      throws UndeliverableMessageException
  {
    try {
      if (constraints.isSatisfied(context, attachment)) {
        if (MediaUtil.isJpeg(attachment)) {
          MediaStream stripped = constraints.getResizedMedia(context, attachment);
          return attachmentDatabase.updateAttachmentData(attachment, stripped);
        } else {
          return attachment;
        }
      } else if (constraints.canResize(attachment)) {
        MediaStream resized = constraints.getResizedMedia(context, attachment);
        return attachmentDatabase.updateAttachmentData(attachment, resized);
      } else {
        throw new UndeliverableMessageException("Size constraints could not be met!");
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  public static final class Factory implements Job.Factory<AttachmentUploadJob> {
    @Override
    public @NonNull AttachmentUploadJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      return new AttachmentUploadJob(parameters, new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)), Address.fromSerialized(data.getString(KEY_DESTINATION)));
    }
  }
}
