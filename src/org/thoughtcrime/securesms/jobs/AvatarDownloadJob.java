package org.thoughtcrime.securesms.jobs;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

public class AvatarDownloadJob extends BaseJob implements InjectableType {

  public static final String KEY = "AvatarDownloadJob";

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private static final int MAX_AVATAR_SIZE = 20 * 1024 * 1024;

  private static final String KEY_GROUP_ID = "group_id";

  @Inject SignalServiceMessageReceiver receiver;

  private String groupId;

  public AvatarDownloadJob(@NonNull String groupId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         groupId);
  }

  private AvatarDownloadJob(@NonNull Job.Parameters parameters, @NonNull String groupId) {
    super(parameters);
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, groupId).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    GroupDatabase         database   = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord> record     = database.getGroup(groupId);
    File                  attachment = null;

    try {
      if (record.isPresent()) {
        long             avatarId    = record.get().getAvatarId();
        String           contentType = record.get().getAvatarContentType();
        byte[]           key         = record.get().getAvatarKey();
        String           relay       = record.get().getRelay();
        Optional<byte[]> digest      = Optional.fromNullable(record.get().getAvatarDigest());
        Optional<String> fileName    = Optional.absent();
        String url = record.get().getUrl();

        if (avatarId == -1 || key == null || url.isEmpty()) {
          return;
        }

        if (digest.isPresent()) {
          Log.i(TAG, "Downloading group avatar with digest: " + Hex.toString(digest.get()));
        }

        attachment = File.createTempFile("avatar", "tmp", context.getCacheDir());
        attachment.deleteOnExit();

        SignalServiceAttachmentPointer pointer     = new SignalServiceAttachmentPointer(avatarId, contentType, key, Optional.of(0), Optional.absent(), 0, 0, digest, fileName, false, Optional.absent(), url);
        InputStream                    inputStream = receiver.retrieveAttachment(pointer, attachment, MAX_AVATAR_SIZE);
        Bitmap                         avatar      = BitmapUtil.createScaledBitmap(context, new AttachmentModel(attachment, key, 0, digest), 500, 500);

        database.updateAvatar(groupId, avatar);
        inputStream.close();
      }
    } catch (BitmapDecodingException | NonSuccessfulResponseCodeException | InvalidMessageException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  public static final class Factory implements Job.Factory<AvatarDownloadJob> {
    @Override
    public @NonNull AvatarDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AvatarDownloadJob(parameters, data.getString(KEY_GROUP_ID));
    }
  }
}
