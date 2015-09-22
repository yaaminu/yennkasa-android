package com.pair.adapter;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.Errors.PairappException;
import com.pair.data.Message;
import com.pair.messenger.PairAppClient;
import com.pair.pairapp.R;
import com.pair.ui.ChatActivity;
import com.pair.ui.PairAppBaseActivity;
import com.pair.util.Config;
import com.pair.util.FileUtils;
import com.pair.util.PreviewsHelper;
import com.pair.util.SimpleDateUtil;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
@SuppressWarnings("ConstantConditions")
public class MessagesAdapter extends RealmBaseAdapter<Message> implements View.OnLongClickListener {
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static final long TEN_SECONDS = 10 * 1000L;
    private static final int OUTGOING_MESSAGE = 0x1, INCOMING_MESSAGE = 0x2, DATE_MESSAGE = 0x0, TYPING_MESSAGE = 0x3;
    private final SparseIntArray previewsMap, messageStates;
    private static final Map<String, Integer> downloadingRows = new Hashtable<>();
    private final Picasso PICASSO;
    private final int PREVIEW_WIDTH;
    private final int PREVIEW_HEIGHT;
    private final LruCache<String, Bitmap> thumbnailCache;
    private final ChatActivity chatActivity;
    private final int height;

    public MessagesAdapter(ChatActivity context, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        previewsMap = new SparseIntArray(3);
        previewsMap.put(Message.TYPE_PICTURE_MESSAGE, R.drawable.image_placeholder);
        previewsMap.put(Message.TYPE_VIDEO_MESSAGE, R.drawable.video_placeholder);
        previewsMap.put(Message.TYPE_BIN_MESSAGE, R.drawable.file_placeholder);
        messageStates = new SparseIntArray(4);
        messageStates.put(Message.STATE_PENDING, R.drawable.ic_action_upload);
        messageStates.put(Message.STATE_SENT, R.drawable.ic_action_sent);
        messageStates.put(Message.STATE_SEND_FAILED, R.drawable.ic_action_error);
        messageStates.put(Message.STATE_SEEN, R.drawable.ic_visibility_white_24dp);
        messageStates.put(Message.STATE_RECEIVED, R.drawable.ic_done_all_white_24dp);
        height = context.getResources().getDrawable(R.drawable.ic_action_error).getIntrinsicHeight();
        PREVIEW_WIDTH = context.getResources().getDimensionPixelSize(R.dimen.message_preview_item_width);
        PREVIEW_HEIGHT = context.getResources().getDimensionPixelSize(R.dimen.message_preview_item_height);
        thumbnailCache = new LruCache<>(3);
        PICASSO = Picasso.with(context);
        chatActivity = context;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (Message.isTypingMessage(message)) {
            return TYPING_MESSAGE;
        } else if (Message.isDateMessage(message)) {
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
        attemptToMarkAsSeen(message); //async
        if (convertView == null) {
            convertView = hookupViews(messagesLayout[getItemViewType(position)], parent);
        }
        holder = (ViewHolder) convertView.getTag();

        Date messageDateComposed = message.getDateComposed();
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        };
        if (Message.isDateMessage(message)) {
            // TODO: 9/18/2015 improve this
            String formattedDate = SimpleDateUtil.formatDateRage(context, messageDateComposed);
            holder.textMessage.setText(formattedDate);
            convertView.setOnTouchListener(touchListener);
            return convertView;
        } else if (Message.isTypingMessage(message)) {
            convertView.setOnTouchListener(touchListener);
            return convertView;
        }

        //hide all views and show only if it's required. life will be easier this way
        holder.preview.setVisibility(View.GONE);
        holder.progress.setVisibility(View.GONE);
        holder.playOrDownload.setVisibility(View.GONE);
        holder.preview.setOnClickListener(null);
        holder.playOrDownload.setOnClickListener(null);
        holder.textMessage.setVisibility(View.GONE);
        holder.retry.setVisibility(View.GONE);
        //common to all
        holder.dateComposed.setVisibility(View.VISIBLE);

        String dateComposed = DateUtils.formatDateTime(context, messageDateComposed.getTime(), DateUtils.FORMAT_SHOW_TIME);
        if (isOutgoingMessage(message)) {
            if (message.getState() == Message.STATE_PENDING) {
                holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); //reset every thing
                holder.dateComposed.setMinHeight(height);
                holder.dateComposed.setText(Html.fromHtml("<h1><b>...</b></h1>") + dateComposed, TextView.BufferType.SPANNABLE);
            } else {
                holder.dateComposed.setText(dateComposed);
                holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(messageStates.get(message.getState()), 0, 0, 0);
            }

            if (message.getState() == Message.STATE_SEND_FAILED) {
                holder.retry.setVisibility(View.VISIBLE);
                holder.retry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO: 9/18/2015 offload this to a background thread
                        chatActivity.resendMessage(message.getId());
                    }
                });
            }
        } else {
            holder.dateComposed.setText(dateComposed);
        }

        if (Message.isTextMessage(message)) {
            //normal message
            //TODO use one of the variants of the spannableString classes.
            holder.textMessage.setVisibility(View.VISIBLE);
            holder.textMessage.setText(message.getMessageBody());
            return convertView;
        }

        //if we are here then the message is binary example picture message or video message
        holder.preview.setVisibility(View.VISIBLE);
        holder.preview.setOnLongClickListener(this);
        final int placeHolderDrawable = PreviewsHelper.getPreview(message.getMessageBody());
        final File messageFile = new File(message.getMessageBody());
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (v.getId() == R.id.v_download_play) {
                        if (messageFile.exists()) {
                            UiHelpers.attemptToViewFile((PairAppBaseActivity) context, messageFile);
                        } else {
                            download(message);
                        }
                    } else if (v.getId() == R.id.iv_message_preview) {
                        UiHelpers.attemptToViewFile(((PairAppBaseActivity) context), messageFile);
                    }
                } catch (PairappException e) {
                    ErrorCenter.reportError("viewFile", e.getMessage());
                }
            }
        };
        if (messageFile.exists()) {
            // this binary message is downloaded
            if (Message.isVideoMessage(message)) {
                holder.playOrDownload.setVisibility(View.VISIBLE);
                holder.playOrDownload.setImageResource(R.drawable.playvideo);
                holder.playOrDownload.setOnClickListener(listener);
                //we use the body of the message so that we will not have to
                //create a thumbnail for the same file twice.
                final Bitmap bitmap;
                synchronized (thumbnailCache) {
                    bitmap = thumbnailCache.get(messageFile.getAbsolutePath());
                }
                if (bitmap == null) {
                    makeThumbnail(messageFile.getAbsolutePath());
                    holder.preview.setImageResource(placeHolderDrawable);
                } else {
                    holder.preview.setImageBitmap(bitmap);
                }
            } else if (Message.isPictureMessage(message)) {
                PICASSO.load(messageFile)
                        .resize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                        .centerCrop()
                        .placeholder(placeHolderDrawable)
                        .error(R.drawable.format_pic_broken)
                        .into(holder.preview);

            } else {
                holder.preview.setImageResource(placeHolderDrawable);
            }
            holder.preview.setOnClickListener(listener);
        } else {
            if (!downloadingRows.containsKey(message.getId()) && Message.isIncoming(message)) {
                holder.playOrDownload.setVisibility(View.VISIBLE);
                holder.playOrDownload.setImageResource(R.drawable.photoload);
                holder.playOrDownload.setOnClickListener(listener);
            }
            PICASSO.load(placeHolderDrawable).into(holder.preview);
        }

        // FIXME: 9/17/2015 synchronise on the downloadingRows map
        if (downloadingRows.containsKey(message.getId())) {
            convertView.post(new Runnable() {
                @Override
                public void run() {
                    holder.progress.setVisibility(View.VISIBLE);
                }
            });
        }
        return convertView;
    }

    private void makeThumbnail(final String uri) {
        WORKER.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(uri, MediaStore.Images.Thumbnails.MINI_KIND);
                if (bitmap != null) {
                    synchronized (thumbnailCache) {
                        thumbnailCache.put(uri, bitmap);
                    }
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }

    @SuppressWarnings("unused")
    private String getStringRepresentation(int status) {
//        switch (status) {
//            case Message.STATE_PENDING:
//                return context.getString(R.string.st_message_state_pending);
//            case Message.STATE_SEND_FAILED:
//                return context.getString(R.string.st_message_state_failed);
//            case Message.STATE_RECEIVED:
//                return context.getString(R.string.st_message_state_delivered);
//            case Message.STATE_SEEN:
//                return context.getString(R.string.st_message_state_seen);
//            case Message.STATE_SENT:
//                return context.getString(R.string.st_message_state_sent);
//            default:
//                throw new AssertionError("new on unknown message status");
//        }
        return Message.state(context, status);
    }

    private void attemptToMarkAsSeen(Message message) {

        if (Message.isIncoming(message) && message.getState() != Message.STATE_SEEN) {
            final String msgId = message.getId(), recipient = message.getTo();
            WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    Realm realm = Message.REALM(context);
                    realm.beginTransaction();
                    Message message1 = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
                    if (message1 != null) {
                        message1.setState(Message.STATE_SEEN);
                        realm.commitTransaction();
                        message1 = Message.copy(message1);
                    }
                    realm.close();
                    if (message1 != null) {
                        PairAppClient.notifyMessageSeen(message1);
                    }
                }
            });
        }
    }

    private void download(final Message realmMessage) {

        final Message message = Message.copy(realmMessage); //detach from realm
        final String messageId = message.getId(),
                messageBody = message.getMessageBody();
        downloadingRows.put(message.getId(), 0);
        notifyDataSetChanged(); //this will show the progress indicator and hide the download button
        WORKER.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        Realm realm = null;
                        final File finalFile;
                        String destination = Base64.encodeToString(messageBody.getBytes(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                        // FIXME: 9/3/2015  find a better way of handling file extensions rather than use sniffing methods
                        //may be MimeTypeMap#guessFileExtensionFromStream() will do.

                        switch (message.getType()) {
                            case Message.TYPE_VIDEO_MESSAGE:
                                finalFile = new File(Config.getAppVidMediaBaseDir(), destination + ".mp4");
                                break;
                            case Message.TYPE_PICTURE_MESSAGE:
                                finalFile = new File(Config.getAppImgMediaBaseDir(), destination + ".jpeg");
                                break;
                            case Message.TYPE_BIN_MESSAGE:
                                finalFile = new File(Config.getAppBinFilesBaseDir(), destination);
                                break;
                            default:
                                throw new AssertionError("should never happen");
                        }
                        try {
//                            URL url = new URL(messageBody);
//                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            FileUtils.save(finalFile, messageBody);
                            realm = Realm.getInstance(Config.getApplicationContext());
                            realm.beginTransaction();
                            Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                            toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                            realm.commitTransaction();
                            onComplete(null);
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage(), e.getCause());
                            onComplete(e);
                        } finally {
                            if (realm != null) {
                                realm.close();
                            }
                        }
                    }

                    private void onComplete(final Exception error) {
                        mainThreadHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        downloadingRows.remove(message.getId());
                                        notifyDataSetChanged();
                                        if (error != null) { //user might have left
                                            ErrorCenter.reportError(TAG, error.getMessage());
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
        holder.textMessage = ((TextView) convertView.findViewById(R.id.tv_log_message));
        holder.preview = (ImageView) convertView.findViewById(R.id.iv_message_preview);
        holder.dateComposed = ((TextView) convertView.findViewById(R.id.tv_message_date));
        holder.playOrDownload = ((ImageView) convertView.findViewById(R.id.v_download_play));
        holder.progress = convertView.findViewById(R.id.pb_download_progress);
        holder.retry = convertView.findViewById(R.id.iv_retry);
        convertView.setTag(holder);
        return convertView;
    }

    @SuppressWarnings("unused")
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

    private boolean isOutgoingMessage(Message message) {
        return Message.isOutGoing(message);
    }

    @Override
    public boolean onLongClick(View v) {
        //do nothing. just to force the click event to be propagated to its parent
        //by returning false the containing list will get chance to handle the long click
        return false;
    }

    private class ViewHolder {
        private TextView textMessage, dateComposed;
        private ImageView preview, playOrDownload;
        private View progress;
        private View retry;
        //TODO add more fields as we support different media/file types
    }

    private static final int[] messagesLayout = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming,
            R.layout.typing_dots
    };

    private final Executor WORKER = Executors.newCachedThreadPool();
}
