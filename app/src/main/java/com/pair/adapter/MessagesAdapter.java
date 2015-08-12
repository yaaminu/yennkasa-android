package com.pair.adapter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.Config;
import com.pair.pairapp.R;
import com.pair.pairapp.ui.ImageViewer;
import com.pair.util.FileUtils;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
@SuppressWarnings("ConstantConditions")
public class MessagesAdapter extends RealmBaseAdapter<Message> {
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final long TEN_SECONDS = 10000L;
    private static final int OUTGOING_MESSAGE = 0x1, INCOMING_MESSAGE = 0x2, DATE_MESSAGE = 0x0;
    private final SparseIntArray previewsMap;
    private final SparseIntArray downloadingRows = new SparseIntArray();
    private final Picasso PICASSO;

    public MessagesAdapter(Activity context, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        previewsMap = new SparseIntArray(3);
        previewsMap.put(Message.TYPE_PICTURE_MESSAGE, R.drawable.image_placeholder);
        previewsMap.put(Message.TYPE_VIDEO_MESSAGE, R.drawable.video_placeholder);
        previewsMap.put(Message.TYPE_BIN_MESSAGE, R.drawable.file_placeholder);
        PICASSO = Picasso.with(context);
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (Message.isDateMessage(message)) {
            return DATE_MESSAGE;
        } else {
            if (isOutgoingMessage(message)) {
                return OUTGOING_MESSAGE;
            } else {
                return INCOMING_MESSAGE;
            }
        }
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        final ViewHolder holder;
        final Message message = getItem(position);
        if (convertView == null) {
            convertView = hookupViews(messagesLayout[getItemViewType(position)], parent);
        }
        holder = (ViewHolder) convertView.getTag();

        if (Message.isDateMessage(message)) {
            holder.textMessage.setText(message.getMessageBody());
            return convertView;
        }

        String dateComposed = DateUtils.formatDateTime(context, message.getDateComposed().getTime(), DateUtils.FORMAT_SHOW_TIME);

        //show all views hide if it's not required
        holder.preview.setVisibility(View.GONE);
        holder.messageStatus.setVisibility(View.GONE);
        holder.downloadButton.setVisibility(View.GONE);
        holder.progress.setVisibility(View.GONE);

        //common to all
        holder.dateComposed.setVisibility(View.VISIBLE);
        holder.dateComposed.setText(dateComposed);

        if (isOutgoingMessage(message)) {
            holder.messageStatus.setVisibility(View.VISIBLE);
            holder.messageStatus.setText(getStringRepresentation(message.getState()));
        }

        if (Message.isTextMessage(message)) {
            //normal message
            holder.textMessage.setVisibility(View.VISIBLE);
            holder.textMessage.setText(message.getMessageBody());
            return convertView;
        }

        //if we are here then the message is binary example picture message or video message
        holder.preview.setVisibility(View.VISIBLE);

        final int placeHolder = previewsMap.get(message.getType());
        final File messageFile = new File(message.getMessageBody());
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.bt_download) {
                    download(position);
                } else if (v.getId() == R.id.iv_message_preview) {
                    attemptToViewFile(v.getContext(), messageFile, message);
                }
            }
        };
        if (messageFile.exists()) {
            // this binary message is downloaded
            final RequestCreator creator;
            if (Message.isPictureMessage(message)) {
                creator = PICASSO.load(messageFile)
                        .resize(250, 200)
                        .centerCrop()
                        .error(placeHolder);
            } else {
                creator = PICASSO.load(placeHolder);
            }
            creator.into(holder.preview);
            holder.preview.setOnClickListener(listener);
        } else {
            if (downloadingRows.indexOfKey(position) < 0) { //not downloaded/downloading
                holder.downloadButton.setVisibility(View.VISIBLE);
            }
            holder.downloadButton.setOnClickListener(listener);
            PICASSO.load(placeHolder)
                    .into(holder.preview);
        }

        if (downloadingRows.indexOfKey(position) > -1) {
            holder.progress.setVisibility(View.VISIBLE);
        }

        return convertView;
    }

    private void attemptToViewFile(Context context, File file, Message message) {
        if (file.exists()) {
            Intent intent;
            if (Message.isPictureMessage(message)) {
                intent = new Intent(context, ImageViewer.class);
                intent.setData(Uri.fromFile(file));
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), FileUtils.getMimeType(file.getAbsolutePath()));
            }
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                UiHelpers.showErrorDialog(context, R.string.error_sorry_no_application_to_open_file);
            }
        }
    }

    private String getStringRepresentation(int status) {
        switch (status) {
            case Message.STATE_PENDING:
                return context.getString(R.string.st_message_state_pending);
            case Message.STATE_SEND_FAILED:
                return context.getString(R.string.st_message_state_failed);
            case Message.STATE_RECEIVED:
                return context.getString(R.string.st_message_state_delivered);
            case Message.STATE_SEEN:
                return context.getString(R.string.st_message_state_seen);
            case Message.STATE_SENT:
                return context.getString(R.string.st_message_state_sent);
            default:
                throw new AssertionError("new on unknown message status");
        }
    }

    private void download(final int position) {

        final Message message = getItem(position);
        final String messageId = message.getId(),
                messageBody = message.getMessageBody();
        final File finalFile;
        switch (message.getType()) {
            case Message.TYPE_VIDEO_MESSAGE:
                finalFile = new File(Config.APP_VID_MEDIA_BASE_DIR, messageBody + ".mp4");
                break;
            case Message.TYPE_PICTURE_MESSAGE:
                finalFile = new File(Config.APP_IMG_MEDIA_BASE_DIR, messageBody + ".jpeg");
                break;
            case Message.TYPE_BIN_MESSAGE:
                finalFile = new File(Config.APP_BIN_FILES_BASE_DIR, messageBody);
                break;
            default:
                throw new AssertionError("should never happen");
        }
        downloadingRows.put(position, message.getType());
        notifyDataSetChanged(); //this will show the progress indicator and hide the download button
        downloader.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        Realm realm = null;
                        try {
                            FileUtils.save(finalFile, new URL(Config.MESSAGE_ENDPOINT + "/" + messageBody).openStream());
                            realm = Realm.getInstance(Config.getApplicationContext());
                            realm.beginTransaction();
                            Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                            toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                            realm.commitTransaction();
                            onComplete(null);
                        } catch (IOException e) {
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, e.getMessage(), e.getCause());
                            } else {
                                Log.e(TAG, e.getMessage());
                            }
                            onComplete(e);
                        } finally {
                            if (realm != null) {
                                realm.close();
                            }
                        }
                    }

                    private void onComplete(final Exception error) {
                        final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
                        mainThreadHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            downloadingRows.delete(position);
                                            notifyDataSetChanged();
                                            //show download button
                                            if (error != null) {
                                                UiHelpers.showErrorDialog(Config.getApplicationContext(), error.getMessage());
                                            }
                                        } catch (Exception ignored) {
                                            //may be user navigated from this activity hence the context is now
                                            //invalid, invalid tokens and other stuffs
                                            Log.e(TAG, ignored.getMessage(), ignored.getCause());
                                        }
                                    }
                                });
                    }
                });
    }

    @NonNull
    private View hookupViews(int layoutResource, ViewGroup parent) {
        View convertView;
        ViewHolder holder;
        convertView = LayoutInflater.from(parent.getContext()).inflate(layoutResource, parent, false);
        holder = new ViewHolder();
        holder.textMessage = ((TextView) convertView.findViewById(R.id.tv_message_content));
        holder.preview = (ImageView) convertView.findViewById(R.id.iv_message_preview);
        holder.dateComposed = ((TextView) convertView.findViewById(R.id.tv_message_date));
        holder.downloadButton = ((Button) convertView.findViewById(R.id.bt_download));
        holder.progress = (ProgressBar) convertView.findViewById(R.id.pb_download_progress);
        holder.messageStatus = ((TextView) convertView.findViewById(R.id.tv_message_status));
        convertView.setTag(holder);
        return convertView;
    }

    private void hideDateIfClose(int position, ViewHolder holder, Message message) {
        //show messages at a particular time together
        if ((message.getType() == Message.TYPE_TEXT_MESSAGE)) {
            try {
                Message nextMessage = getItem(position + 1); //might throw
                //ensure they are all from same user
                if ((isOutgoingMessage(message) && isOutgoingMessage(nextMessage))
                        || (!isOutgoingMessage(message) && !isOutgoingMessage(nextMessage))) {
                    if (nextMessage.getDateComposed().getTime() - message.getDateComposed().getTime() < TEN_SECONDS) { //close enough!
                        holder.dateComposed.setVisibility(View.GONE);
                    } else {
                        holder.dateComposed.setVisibility(View.VISIBLE);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                //this is the last message so don't hide!
                holder.dateComposed.setVisibility(View.VISIBLE);
            }
        }
    }


    private boolean isDateMessage(int position) {
        return getItem(position).getType() == Message.TYPE_DATE_MESSAGE;
    }

    private boolean isOutgoingMessage(Message message) {
        return (message.getFrom().equals(UserManager.getInstance().getMainUser().get_id()));
    }

    private class ViewHolder {
        private TextView textMessage, dateComposed;
        private ImageView preview;
        private Button downloadButton;
        private ProgressBar progress;
        private TextView messageStatus;
        //TODO add more fields as we support different media/file types
    }

    private static final int[] messagesLayout = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming
    };

    private final Executor downloader = Executors.newCachedThreadPool();
}
