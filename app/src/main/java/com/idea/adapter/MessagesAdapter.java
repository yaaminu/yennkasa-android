package com.idea.adapter;

import android.graphics.Bitmap;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.pairapp.R;
import com.idea.ui.PairAppBaseActivity;
import com.idea.util.PLog;
import com.idea.util.PreviewsHelper;
import com.idea.util.SimpleDateUtil;
import com.idea.util.TaskManager;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.jmpergar.awesometext.AwesomeTextHandler;
import com.jmpergar.awesometext.MentionSpanRenderer;
import com.squareup.picasso.Picasso;

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
            OUTGOING_MESSAGE_ONE_LINE = 0x5;

    private static final int[] messagesLayout = {
            R.layout.message_item_log,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming,
            R.layout.typing_dots,
            R.layout.one_line_message_list_item_incoming,
            R.layout.one_line_message_list_item_outgoing
    };
    private final SparseIntArray messageStates;
    private final Picasso PICASSO;
    private final LruCache<String, Bitmap> thumbnailCache;
    private final Delegate delegate;
    private static final String MENTION_PATTERN = "(@.*)";

    public MessagesAdapter(Delegate delegate, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(delegate.getContext(), realmResults, automaticUpdate);
        this.delegate = delegate;
        messageStates = new SparseIntArray(4);
        messageStates.put(Message.STATE_PENDING, R.drawable.ic_vertical_align_top_white_18dp);
        messageStates.put(Message.STATE_SENT, R.drawable.ic_done_white_18dp);
        messageStates.put(Message.STATE_SEND_FAILED, R.drawable.ic_error_white_18dp);
        messageStates.put(Message.STATE_SEEN, R.drawable.ic_visibility_white_18dp);
        messageStates.put(Message.STATE_RECEIVED, R.drawable.ic_done_all_white_18dp);
        thumbnailCache = new LruCache<>(5);
        PICASSO = Picasso.with(context);

    }

    @Override
    public int getViewTypeCount() {
        return 6;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (Message.isTypingMessage(message)) {
            return TYPING_MESSAGE;
        }

        if (Message.isDateMessage(message)) {
            return DATE_MESSAGE;
        }

        boolean useOneLine = false;
        if (Message.isTextMessage(message)) {
            int length = message.getMessageBody().length();
            int dateLength = formatDateMessage(message.getDateComposed()).length();
            useOneLine = length < dateLength * 4;
        }
        if (Message.isOutGoing(message)) {
            return useOneLine ? OUTGOING_MESSAGE_ONE_LINE : OUTGOING_MESSAGE;
        }
        return useOneLine ? INCOMING_MESSAGE_ONE_LINE : INCOMING_MESSAGE;

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
            AwesomeTextHandler awesomeTextViewHandler = new AwesomeTextHandler();
            awesomeTextViewHandler
                    .addViewSpanRenderer(MENTION_PATTERN, new MentionSpanRenderer())
                    .setView(holder.textMessage);
            awesomeTextViewHandler.setText("@" + formattedDate);
            convertView.setOnTouchListener(touchListener);
            return convertView;
        } else if (Message.isTypingMessage(message)) {
            convertView.setOnTouchListener(touchListener);
            return convertView;
        }

        //hide all views and show only if it's required. life will be easier this way
        ViewUtils.hideViews(holder.preview,
                holder.progressBarIndeterminate,
                holder.playOrDownload,
                holder.textMessage,
                holder.retry,
                holder.progressRootView,
                holder.progressBarDeterminate);

        holder.preview.setOnClickListener(null);
        holder.playOrDownload.setOnClickListener(null);
        //common to all
        holder.dateComposed.setVisibility(View.VISIBLE);
        String dateComposed = formatDateMessage(messageDateComposed);
        if (Message.isOutGoing(message)) {
            holder.dateComposed.setText("  " + dateComposed);
            int drawableRes = messageStates.get(message.getState());

            holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            holder.dateComposed.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0);
            if (message.getState() == Message.STATE_SEND_FAILED) {
                holder.retry.setVisibility(View.VISIBLE);
                holder.retry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delegate.onReSendMessage(message);
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
                            delegate.download(message);
                        }
                    } else if (v.getId() == R.id.iv_message_preview) {
                        UiHelpers.attemptToViewFile(((PairAppBaseActivity) context), messageFile);
                    }
                } catch (PairappException e) {
                    ErrorCenter.reportError("viewFile", e.getMessage());
                }
            }
        };
        final int progress = delegate.getProgress(message);
        if (messageFile.exists()) {
            // this binary message is downloaded
            if (Message.isVideoMessage(message)) {
                holder.playOrDownload.setVisibility(View.VISIBLE);
                holder.playOrDownload.setImageResource(R.drawable.playvideo);
                holder.playOrDownload.setOnClickListener(listener);
                //we use the body of the message so that we will not have to
                //create a thumbnail for the same file twice.
                final Bitmap bitmap;
                bitmap = thumbnailCache.get(messageFile.getAbsolutePath());
                if (bitmap == null) {
                    makeThumbnail(messageFile.getAbsolutePath());
                    holder.preview.setImageResource(placeHolderDrawable);
                } else {
                    holder.preview.setImageBitmap(bitmap);
                }
            } else if (Message.isPictureMessage(message)) {
                PICASSO.load(messageFile)
                        .placeholder(placeHolderDrawable)
                        .error(R.drawable.format_pic_broken)
                        .into(holder.preview);
            } else {
                holder.preview.setImageResource(placeHolderDrawable);
            }
            holder.preview.setOnClickListener(listener);
        } else {
            holder.preview.setImageResource(placeHolderDrawable);
            if (progress <= -1) {
                if (Message.isIncoming(message)) {
                    ViewUtils.showViews(holder.playOrDownload);
                    holder.playOrDownload.setImageResource(R.drawable.photoload);
                    holder.playOrDownload.setOnClickListener(listener);
                }
            }
        }
        if (progress > -1) {
            if (progress == 0) {
                ViewUtils.showViews(holder.progressBarIndeterminate);
            } else {
                ViewUtils.showViews(holder.progressRootView);
                ViewUtils.showViews(holder.progressBarDeterminate);
                holder.progressBarDeterminate.setProgress(progress);
            }
        }
        return convertView;
    }

    private String formatDateMessage(Date messageDateComposed) {
        return DateUtils.formatDateTime(context, messageDateComposed.getTime(), DateUtils.FORMAT_SHOW_TIME);
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
                            }
                        }
                    });
                }
            }
        };
        if (!TaskManager.executeNow(runnable)) {
            TaskManager.execute(runnable);
        }
    }


    private void attemptToMarkAsSeen(Message message) {
        if (message.getState() != Message.STATE_SEEN &&
                !Message.isDateMessage(message) &&
                !Message.isTypingMessage(message)
                && Message.isIncoming(message)) {
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
        holder.progressBarIndeterminate = convertView.findViewById(R.id.pb_download_progress_indeterminate);
        holder.retry = convertView.findViewById(R.id.iv_retry);
        holder.progressRootView = convertView.findViewById(R.id.fl_progress_root_view);
        holder.progressBarDeterminate = (ProgressBar) convertView.findViewById(R.id.pb_download_progress_determinate);
        ViewUtils.setTypeface(holder.dateComposed, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface(holder.textMessage, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
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

        int getProgress(Message message);

        void download(Message message);

        PairAppBaseActivity getContext();
    }

    private class ViewHolder {
        private TextView textMessage, dateComposed;
        private ImageView preview, playOrDownload;
        private View progressBarIndeterminate;
        private View retry, progressRootView;
        private ProgressBar progressBarDeterminate;
        //TODO add more fields as we support different media/file types
    }
}
