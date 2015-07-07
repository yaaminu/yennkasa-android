package com.pair.adapter;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.Message;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.UserManager;

import java.io.File;

import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
public class MessagesAdapter extends RealmBaseAdapter<Message> {
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final long ONE_MINUTE = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15;
    private static final int OUTGOING_MESSAGE = 0x1, INCOMING_MESSAGE = 0x2, DATE_MESSAGE = 0x0;
    private final LruCache<String, Bitmap> imageCaches;

    public MessagesAdapter(Activity context, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        long cacheSize = Runtime.getRuntime().totalMemory();
        cacheSize /= 8;
        imageCaches = new LruCache<>(((int) cacheSize));
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
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
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        final Message message = getItem(position);
        if (convertView == null) {
            convertView = hookupViews(layoutResources[getItemViewType(position)], parent);
        }
        holder = (ViewHolder) convertView.getTag();

        if (isDateMessage(position)) {
            holder.content.setText(message.getMessageBody());
            return convertView;
        }

        if (message.getType() == Message.TYPE_TEXT_MESSAGE) {
            //normal message
            hideViews(holder.preview, holder.downloadButton);
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(message.getMessageBody());
        } else if (message.getType() == Message.TYPE_PICTURE_MESSAGE) {
            getPictureView(holder, message);
        } else if (message.getType() == Message.TYPE_VIDEO_MESSAGE) {
            showPreview(holder, message, "video/*", R.drawable.video_placeholder);
        } else if (message.getType() == Message.TYPE_BIN_MESSAGE) {
            String mimeType = FileHelper.getMimeType(message.getMessageBody());
            showPreview(holder, message, mimeType, R.drawable.file_placeholder);
        }
        hideDateIfClose(position, holder, message);
        return convertView;
    }

    @NonNull
    private View hookupViews(int layoutResource, ViewGroup parent) {
        View convertView;
        ViewHolder holder;
        convertView = LayoutInflater.from(parent.getContext()).inflate(layoutResource, parent, false);
        holder = new ViewHolder();
        holder.content = ((TextView) convertView.findViewById(R.id.tv_message_content));
        holder.preview = (ImageView) convertView.findViewById(R.id.iv_message_preview);
        holder.dateComposed = ((TextView) convertView.findViewById(R.id.tv_message_date));
        holder.downloadButton = ((Button) convertView.findViewById(R.id.bt_download));
        convertView.setTag(holder);
        return convertView;
    }

    private void getPictureView(ViewHolder holder, final Message message) {
        hideViews(holder.content);
        holder.preview.setVisibility(View.VISIBLE);
        Bitmap cachedImage = imageCaches.get(message.getId());
        Log.d(TAG, message.getMessageBody());
        if (new File(message.getMessageBody()).exists()) { //whew downloaded!
            hideViews(holder.downloadButton);
            if (cachedImage == null) {
                cachedImage = BitmapFactory.decodeFile(message.getMessageBody());
                imageCaches.put(message.getId(), cachedImage);
            }
            holder.preview.setImageBitmap(cachedImage);
            //downloaded
            final View.OnClickListener imageClickHandler = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = Uri.fromFile(new File(message.getMessageBody()));
                    intent.setDataAndType(uri, "image/*");
                    context.startActivity(intent);
                }
            };
            holder.preview.setOnClickListener(imageClickHandler);
        } else { //not downloaded show a notice for user to download
            holder.downloadButton.setVisibility(View.VISIBLE);
            holder.preview.setOnClickListener(null); //clear click handler that was previously set
            holder.downloadButton.setOnClickListener(new downloader(null));
            // TODO: 6/18/2015 replace the avatar_empty with a correct message
            if (cachedImage == null) {
                cachedImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.avatar_empty);//show a place holder image
                imageCaches.put(message.getId(), cachedImage);
            }
            holder.preview.setImageBitmap(cachedImage);
        }
    }

    private void showPreview(ViewHolder holder, final Message message, final String mimeType, int drawable) {
        hideViews(holder.content);
        holder.preview.setVisibility(View.VISIBLE);
        Bitmap cachedImage = imageCaches.get(message.getId());
        Log.d(TAG, message.getMessageBody());
        if (cachedImage == null) {
            cachedImage = BitmapFactory.decodeResource(context.getResources(), drawable);
            imageCaches.put(message.getId(), cachedImage);
        }
        holder.preview.setImageBitmap(cachedImage);
        if (new File(message.getMessageBody()).exists()) {
            holder.downloadButton.setVisibility(View.GONE);
            final View.OnClickListener videoClickHandler = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = Uri.fromFile(new File(message.getMessageBody()));
                    intent.setDataAndType(uri, mimeType);
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(context, R.string.error_sory_no_application_to_open_file, Toast.LENGTH_LONG).show();
                    }
                }
            };
            holder.preview.setOnClickListener(videoClickHandler);
        } else {
            holder.downloadButton.setVisibility(View.VISIBLE);
            holder.preview.setOnClickListener(null);//clear click handler if it was set in the recycled view
        }
    }

    private class downloader implements View.OnClickListener {
        ProgressBar progressBar;

        public downloader(ProgressBar progressBar) {
            this.progressBar = progressBar;
        }

        @Override
        public void onClick(View v) {
            //download
            Log.i(TAG, "download here");

        }
    }

    private void hideDateIfClose(int position, ViewHolder holder, Message message) {
        String formattedDate = DateUtils.formatDateTime(context, message.getDateComposed().getTime(), DateUtils.FORMAT_SHOW_TIME);
        boolean hideDate = false;
        //show messages at a particular time together
        if (!isLast(position)) { //prevents out of bound access
            Message nextMessage = getItem(position + 1); //safe
            //ensure they are all from same user
            if ((isOutgoingMessage(message) && isOutgoingMessage(nextMessage))
                    || (!isOutgoingMessage(message) && !isOutgoingMessage(nextMessage))) {
                if (nextMessage.getDateComposed().getTime() - message.getDateComposed().getTime() < ONE_MINUTE) { //close enough!
                    hideDate = true;
                }
            }
        }
//        (hideDate == true)?
//                (holder.dateComposed.setVisibility(View.GONE)):
//        (doNotCollapse(holder, formattedDate));
        if (hideDate) {
            hideViews(holder.dateComposed);
        } else {
            doNotCollapse(holder, formattedDate);
        }

    }

    private void doNotCollapse(ViewHolder holder, String formattedDate) {
        holder.dateComposed.setVisibility(View.VISIBLE);
        holder.dateComposed.setText(formattedDate);
    }

    private boolean isLast(int position) {
        return getCount() - 1 == position;
    }

    private boolean isDateMessage(int position) {
        return getItem(position).getType() == Message.TYPE_DATE_MESSAGE;
    }

    private boolean isOutgoingMessage(Message message) {
        return (message.getFrom().equals(UserManager.getInstance(Config.getApplication()).getMainUser().get_id()));
    }

    private void hideViews(View... viewsToHide) {
        for (View view : viewsToHide) {
            view.setVisibility(View.GONE);
        }
    }

    private class ViewHolder {
        TextView content, dateComposed;
        public ImageView preview;
        public Button downloadButton;
        //TODO add more fields as we support different media/file types
    }

    int[] layoutResources = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming
    };
}
