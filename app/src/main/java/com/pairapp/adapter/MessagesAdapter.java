package com.pairapp.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.text.format.DateUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.Errors.PairappException;
import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.ui.PairAppBaseActivity;
import com.pairapp.util.PLog;
import com.pairapp.util.PreviewsHelper;
import com.pairapp.util.SimpleDateUtil;
import com.pairapp.util.TaskManager;
import com.pairapp.util.TypeFaceUtil;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.pairapp.view.ProgressWheel;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.util.Date;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
@SuppressWarnings("ConstantConditions")
public class MessagesAdapter extends RealmBaseAdapter<Message> implements View.OnLongClickListener {
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final int OUTGOING_MESSAGE = 0x1, INCOMING_MESSAGE = 0x2,
            DATE_MESSAGE = 0x0, TYPING_MESSAGE = 0x3,
            INCOMING_MESSAGE_ONE_LINE = 0x4,
            OUTGOING_MESSAGE_ONE_LINE = 0x5,
            INCOMING_MESSAGE_ONE_LINE_EXTRA = 0x6,
            OUTGOING_MESSAGE_ONE_LINE_EXTRA = 0x7,
            INCOMING_MESSAGE_EXTRA = 0x8,
            OUTGOING_MESSAGE_EXTRA = 0x9;


    //    private final Drawable bgOut, bgOutXtra, bgIn, bgInXtra;
    private final SparseIntArray messageStates;
    private final Picasso PICASSO;
    private static final LruCache<String, Bitmap> thumbnailCache = new LruCache<>(5);

    private final Delegate delegate;
    private final boolean isGroupMessages;
    private boolean isSameSender;

    public MessagesAdapter(Delegate delegate, RealmResults<Message> realmResults, boolean isGroupMessages) {
        super(delegate.getContext(), realmResults, true);
        this.delegate = delegate;
        messageStates = new SparseIntArray(4);
        messageStates.put(Message.STATE_PENDING, R.drawable.ic_vertical_align_top_white_18dp);
        messageStates.put(Message.STATE_SENT, R.drawable.ic_done_white_18dp);
        messageStates.put(Message.STATE_SEND_FAILED, R.drawable.ic_error_white_18dp);
        messageStates.put(Message.STATE_SEEN, R.drawable.ic_visibility_white_18dp);
        messageStates.put(Message.STATE_RECEIVED, R.drawable.ic_done_all_white_18dp);
        this.isGroupMessages = isGroupMessages;
        PICASSO = Picasso.with(context);
//        Resources resources = context.getResources();
//        bgOut = resources.getDrawable(R.drawable.bg_msg_outgoing_normal);
//        bgOutXtra = resources.getDrawable(R.drawable.bg_msg_outgoing_normal_ext);
//        bgIn = resources.getDrawable(R.drawable.bg_msg_incoming_normal);
//        bgInXtra = resources.getDrawable(R.drawable.bg_msg_incoming_normal_ext);
    }

    //optimisations as we are facing performance issues
    //these two fields hold the current state of the message we showing in getView;
    // they will be set in getItemViewType. Due to the fact that all ui stuffs happen on one thread
    // we will have no problem with race condition

    private int currentMessageType; //this holds the current message we showing.
    private boolean isOutgoingMessage; //from who is the currently showing message.

    @Override
    public int getViewTypeCount() {
        return messagesLayout.length;
    }

    private static final int[] messagesLayout = {
            R.layout.list_item_date_log,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming,
            R.layout.typing_dots,
            R.layout.one_line_message_list_item_incoming,
            R.layout.one_line_message_list_item_outgoing,
            R.layout.one_line_message_list_item_incoming_extra,
            R.layout.one_line_message_list_item_outgoing_extra,
            R.layout.list_item_message_incoming_extra,
            R.layout.list_item_message_outgoing_extra
    };

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        currentMessageType = message.getType();
        isOutgoingMessage = Message.isOutGoing(message);
        if (currentMessageType == Message.TYPE_TYPING_MESSAGE) {
            return TYPING_MESSAGE;
        }

        if (currentMessageType == Message.TYPE_DATE_MESSAGE || currentMessageType == Message.TYPE_CALL) {
            return DATE_MESSAGE;
        }

        boolean useOneLine = false;
        if (currentMessageType == Message.TYPE_TEXT_MESSAGE) {
            String messageBody = message.getMessageBody();
            //should we worry about '\r'?
            int length = messageBody.length();
            int dateLength = DateUtils.formatDateTime(context, message.getDateComposed().getTime(), DateUtils.FORMAT_SHOW_TIME).length();
            useOneLine = length < dateLength * 4 && messageBody.indexOf('\n') == -1;
        }

        isSameSender = isSameSender(position);

        if (isOutgoingMessage) {
            if (isSameSender) {
                return useOneLine ? OUTGOING_MESSAGE_ONE_LINE_EXTRA : OUTGOING_MESSAGE_EXTRA;
            } else {
                return useOneLine ? OUTGOING_MESSAGE_ONE_LINE : OUTGOING_MESSAGE;
            }
        }
        if (isSameSender) {
            return useOneLine ? INCOMING_MESSAGE_ONE_LINE_EXTRA : INCOMING_MESSAGE_EXTRA;
        } else {
            return useOneLine ? INCOMING_MESSAGE_ONE_LINE : INCOMING_MESSAGE;
        }

    }

    private boolean isSameSender(int currPosition) {
        //if the previous message was from the same user,we wont show it again but first ensure it's a sendable message
        if (currPosition > 0 /*avoid ouf of bound ex*/) { //this condition is almost always true
            Message previous = getItem(currPosition - 1), message = getItem(currPosition);
            if (MessageUtils.isSendableMessage(previous) && previous.getFrom().equals(message.getFrom())) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        final ViewHolder holder;
        final Message message = getItem(position);
        attemptToMarkAsSeen(message);
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
        if (currentMessageType == Message.TYPE_DATE_MESSAGE) {
            String formattedDate = SimpleDateUtil.formatDateRage(context, messageDateComposed);
            holder.textMessage.setText(formattedDate);
            convertView.setOnTouchListener(touchListener);
            return convertView;
        } else if (currentMessageType == Message.TYPE_CALL) {
            holder.textMessage.append(Message.getCallSummary(message));
            convertView.setOnTouchListener(touchListener);
            return convertView;
        } else if (currentMessageType == Message.TYPE_TYPING_MESSAGE) {
            convertView.setOnTouchListener(touchListener);
            return convertView;
        }

        //hide all views and show only if it's needed. life will be easier this way
        ViewUtils.hideViews(holder.preview,
                holder.progressBar,
                holder.playOrDownload,
                holder.textMessage,
                holder.retry,
                holder.progressRootView,
                holder.sendersName);

        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (v.getId() == R.id.v_download_play) {
                        cancelDownloadOrSending(message);
                    } else if (v.getId() == R.id.iv_message_preview) {
                        openMessage(message);
                    } else if (v.getId() == R.id.tv_sender_name) {
                        UiHelpers.gotoProfileActivity(context, message.getFrom());
                    } else if (v.getId() == R.id.iv_retry) {
                        delegate.onReSendMessage(message);
                    }
                } catch (PairappException e) {
                    ErrorCenter.reportError("viewFile", e.getMessage());
                }
            }
        };

        if (!isSameSender && isGroupMessages && !isOutgoingMessage) {
            ViewUtils.showViews(holder.sendersName);
            holder.sendersName.setText(getSenderName(message));
            holder.sendersName.setOnClickListener(listener);
        }
        holder.preview.setOnClickListener(null);
        holder.playOrDownload.setOnClickListener(null);
        //common to all
        holder.dateComposed.setVisibility(View.VISIBLE);
        String dateComposed = DateUtils.formatDateTime(context, messageDateComposed.getTime(), DateUtils.FORMAT_SHOW_TIME);
        if (isOutgoingMessage) {
            holder.dateComposed.setText("  " + dateComposed);
            int drawableRes = messageStates.get(message.getState());

            holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0);
            if (message.getState() == Message.STATE_SEND_FAILED) {
                holder.retry.setVisibility(View.VISIBLE);
                holder.retry.setOnClickListener(listener);
            }
        } else {
            holder.dateComposed.setText(dateComposed);
        }

        String messageBody = message.getMessageBody();
        if (currentMessageType == Message.TYPE_TEXT_MESSAGE) {
            //normal message
            //TODO use one of the variants of the spannableString classes.
            holder.textMessage.setVisibility(View.VISIBLE);
            holder.textMessage.setText(messageBody);
            return convertView;
        }

        //if we are here then the message is binary example picture message or video message
        holder.preview.setVisibility(View.VISIBLE);
        holder.preview.setOnLongClickListener(this);
        final int placeHolderDrawable = PreviewsHelper.getPreview(messageBody);
        final int progress = delegate.getProgress(message);
        if (!messageBody.startsWith("http")/*we only use http*/) {
            // this binary message is downloaded
            if (currentMessageType == Message.TYPE_VIDEO_MESSAGE) {
                if (progress < 0) {
                    ViewUtils.showViews(holder.progressRootView, holder.playOrDownload);
                    holder.playOrDownload.setImageResource(R.drawable.ic_play_circle_outline_white_36dp);
                    holder.playOrDownload.setOnClickListener(listener);
                }
                //we use the body of the message instead of id so that we will not have to
                //create a thumbnail for the same file twice.
                final Bitmap bitmap;
                bitmap = thumbnailCache.get(messageBody);
                if (bitmap == null) {
                    makeThumbnail(messageBody); //async
                    holder.preview.setImageResource(placeHolderDrawable);
                } else {
                    holder.preview.setImageBitmap(bitmap);
                }
            } else if (currentMessageType == Message.TYPE_PICTURE_MESSAGE) {
                Bitmap bitmap = thumbnailCache.get(message.getMessageBody());
                if (bitmap == null) {
                    PICASSO.load(new File(messageBody))
                            .placeholder(placeHolderDrawable)
                            .error(R.drawable.nophotos)
                            .into(new Target() {
                                @Override
                                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
                                    thumbnailCache.put(message.getMessageBody(), bitmap);
                                    notifyDataSetChanged();
                                }

                                @Override
                                public void onBitmapFailed(Drawable drawable) {
                                    holder.preview.setImageDrawable(drawable);
                                }

                                @Override
                                public void onPrepareLoad(Drawable drawable) {
                                    holder.preview.setImageDrawable(drawable);
                                }
                            });
                } else {
                    holder.preview.setImageBitmap(bitmap);
                }
            } else {
                holder.preview.setImageResource(placeHolderDrawable);
            }
            holder.preview.setOnClickListener(listener);
        } else {
            holder.preview.setImageResource(placeHolderDrawable);
            if (progress <= -1) {
                if (!isOutgoingMessage) {
                    ViewUtils.showViews(holder.progressRootView, holder.playOrDownload);
                    holder.playOrDownload.setImageResource(R.drawable.ic_file_download_white_36dp);
                    holder.playOrDownload.setOnClickListener(listener);
                }
            }
        }
        if (progress >= 0) {
            ViewUtils.showViews(holder.progressRootView, holder.progressBar);
            if (progress == 0 || isOutgoingMessage/* currently upload progress is fake and cannot be relied upon*/) {
                holder.progressBar.spin();
            } else {
                holder.progressBar.setProgress(progress);
            }
            ViewUtils.showViews(holder.playOrDownload);
            holder.playOrDownload.setImageResource(0);
            holder.playOrDownload.setImageResource(R.drawable.ic_clear_white_24dp);
            holder.playOrDownload.setOnClickListener(listener);
        }
        return convertView;
    }

    private String getSenderName(Message message) {
        return UserManager.getInstance().getName(message.getFrom());
    }

    private void openMessage(Message message) throws PairappException {
        File messageFile = new File(message.getMessageBody());
        if (messageFile.exists()) {
            UiHelpers.attemptToViewFile((PairAppBaseActivity) context, messageFile);
        }
    }

    private void cancelDownloadOrSending(Message message) {
        if (Message.isIncoming(message)) {
            if (message.getMessageBody().startsWith("http")) {
                if (delegate.getProgress(message) >= 0) {
                    delegate.cancelDownload(message);
                } else {
                    delegate.download(message);
                }
            }
        } else if (message.getState() == Message.STATE_PENDING && !Message.isTextMessage(message)) {
            delegate.onCancelSendMessage(message);
        }

    }

    private void makeThumbnail(final String uri) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(uri, MediaStore.Images.Thumbnails.MINI_KIND);
                if (bitmap != null) {
                    thumbnailCache.put(uri, bitmap);
                    TaskManager.executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                notifyDataSetChanged();
                            } catch (Exception ignored) {
                                PLog.d(TAG, ignored.getMessage(), ignored.getCause());
                            }
                        }
                    });
                }
            }
        };
        TaskManager.executeNow(runnable, false);
    }


    private void attemptToMarkAsSeen(Message message) {
        if (message.getState() != Message.STATE_SEEN &&
                !isOutgoingMessage &&
                currentMessageType != Message.TYPE_DATE_MESSAGE &&
                currentMessageType != Message.TYPE_TYPING_MESSAGE
                ) {
            delegate.onMessageSeen(message);
        }
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
        holder.progressBar = (ProgressWheel) convertView.findViewById(R.id.pb_download_progress_indeterminate);
        holder.retry = convertView.findViewById(R.id.iv_retry);
        holder.progressRootView = convertView.findViewById(R.id.fl_progress_root_view);
//        holder.progressBarDeterminate = (ProgressBar) convertView.findViewById(R.id.pb_download_progress_determinate);
        holder.sendersName = ((TextView) convertView.findViewById(R.id.tv_sender_name));
        ViewUtils.setTypeface(holder.dateComposed, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        convertView.setTag(holder);
        return convertView;
    }

    @Override
    public void notifyDataSetChanged() {
        if (delegate.onDateSetChanged()) {
            super.notifyDataSetChanged();
        } else {
            PLog.d(TAG, "out of sync");
        }
    }


    @Override
    public boolean onLongClick(View v) {
        //do nothing. just to force the click event to be propagated to its parent
        //by returning false the containing ListView will get chance to handle the long click
        return false;
    }

    public interface Delegate {
        boolean onDateSetChanged();

        void onMessageSeen(Message message);

        void onReSendMessage(Message message);

        void onCancelSendMessage(Message message);

        int getProgress(Message message);

        void download(Message message);

        PairAppBaseActivity getContext();

        void cancelDownload(Message message);
    }

    private class ViewHolder {
        private TextView textMessage, dateComposed, sendersName;
        private ImageView preview, playOrDownload;
        private View retry;
        private View progressRootView;
        private ProgressWheel progressBar;
        //TODO add more fields as we support different media/file types
    }
}
