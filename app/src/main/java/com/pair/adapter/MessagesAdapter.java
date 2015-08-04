package com.pair.adapter;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.pairapp.ui.ImageViewer;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.PicassoWrapper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import io.realm.Realm;
import io.realm.RealmBaseAdapter;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 5/31/2015.
 */
@SuppressWarnings("ConstantConditions")
public class MessagesAdapter extends RealmBaseAdapter<Message> {
    private static final String TAG = MessagesAdapter.class.getSimpleName();
    private static final long ONE_MINUTE = AlarmManager.INTERVAL_FIFTEEN_MINUTES / 15;
    private static final int OUTGOING_MESSAGE = 0x1, INCOMING_MESSAGE = 0x2, DATE_MESSAGE = 0x0;
    private final SparseIntArray previewsMap;
    private final SparseIntArray downloadingRows = new SparseIntArray();

    public MessagesAdapter(Activity context, RealmResults<Message> realmResults, boolean automaticUpdate) {
        super(context, realmResults, automaticUpdate);
        previewsMap = new SparseIntArray(3);
        previewsMap.put(Message.TYPE_PICTURE_MESSAGE, R.drawable.image_placeholder);
        previewsMap.put(Message.TYPE_VIDEO_MESSAGE, R.drawable.video_placeholder);
        previewsMap.put(Message.TYPE_BIN_MESSAGE, R.drawable.file_placeholder);
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
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
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
            holder.preview.setVisibility(View.GONE);
            holder.downloadButton.setVisibility(View.GONE);
            holder.progress.setVisibility(View.GONE);
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(message.getMessageBody());
        } else {
            final File messageFile = new File(message.getMessageBody());
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == R.id.iv_message_preview && messageFile.exists()) {
                        try {
                            Intent intent;
                            if(message.getType() == Message.TYPE_PICTURE_MESSAGE){
                              intent = new Intent(context,ImageViewer.class);
                            }else{
                                intent = new Intent(Intent.ACTION_VIEW);
                            }
                            intent.setDataAndType(Uri.parse(messageFile.getAbsolutePath()), FileHelper.getMimeType(messageFile.getAbsolutePath()));
                            context.startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            UiHelpers.showToast(context.getString(R.string.error_sorry_no_application_to_open_file));
                        }
                    } else if (v.getId() == R.id.bt_download) {
                        if (downloadingRows.indexOfKey(position) < 0 && !messageFile.exists()) {
                            UiHelpers.showToast("downloading " + (position + 1));
                            download(message, position);
                        }
                    }
                }
            };
            if (messageFile.exists()) {
                holder.downloadButton.setVisibility(View.GONE);
                holder.downloadButton.setOnClickListener(null);//in case we recycled a view that attached listener to this view,free it for GC
                holder.progress.setVisibility(View.GONE);
                if (message.getType() == Message.TYPE_PICTURE_MESSAGE) {
                    PicassoWrapper.with(context).load(messageFile)
                            .placeholder(R.drawable.avatar_empty)
                            .error(R.drawable.avatar_empty).into(holder.preview);
                } else {
                    PicassoWrapper.with(context).load(previewsMap.get(message.getType()))
                            .error(R.drawable.avatar_empty).into(holder.preview);
                }
                holder.preview.setVisibility(View.VISIBLE);
                holder.preview.setOnClickListener(listener);
            } else {
                PicassoWrapper.with(context).load(previewsMap.get(message.getType()))
                        .error(R.drawable.avatar_empty).into(holder.preview);
                if (!isOutgoingMessage(message)) {
                    if (downloadingRows.indexOfKey(position) > -1) {
                        holder.progress.setVisibility(View.VISIBLE);
                        holder.downloadButton.setVisibility(View.GONE);
                    } else {
                        holder.downloadButton.setVisibility(View.VISIBLE);
                        holder.downloadButton.setOnClickListener(listener);
                        holder.progress.setVisibility(View.GONE);
                    }
                } else {
                    holder.progress.setVisibility(View.GONE);
                    holder.downloadButton.setVisibility(View.GONE);
                    holder.downloadButton.setOnClickListener(null);
                }
                holder.preview.setOnClickListener(null);//in case we recycled a view that attached listener to this view
            }
        }
        hideDateIfClose(position, holder, message);
        return convertView;
    }

    private void download(final Message message, final int position) {
        File file;
        switch (message.getType()) {
            case Message.TYPE_VIDEO_MESSAGE:
                file = new File(Config.APP_VID_MEDIA_BASE_DIR, message.getMessageBody() + ".mp4");
                break;
            case Message.TYPE_PICTURE_MESSAGE:
                file = new File(Config.APP_IMG_MEDIA_BASE_DIR, message.getMessageBody() + ".jpeg");
                break;
            case Message.TYPE_BIN_MESSAGE:
                file = new File(Config.APP_BIN_FILES_BASE_DIR, message.getMessageBody());
                break;
            default:
                throw new AssertionError("should never happen");
        }
        final File finalFile = file;
        final Message copied = new Message(message);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                downloadingRows.put(position, position);
                notifyDataSetChanged();
            }

            @Override
            protected Void doInBackground(Void... params) {
                Realm realm = null;
                try {
                    FileHelper.save(finalFile, new URL(Config.MESSAGE_ENDPOINT + "/" + copied.getMessageBody()).openStream());
                    realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, copied.getId()).findFirst();
                    toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                    realm.commitTransaction();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        Log.e(TAG, e.getMessage());
                    }
                } finally {
                    if (realm != null) {
                        realm.close();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                downloadingRows.delete(position);
                notifyDataSetChanged();
            }
        }.execute();
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
        holder.progress = (ProgressBar) convertView.findViewById(R.id.pb_download_progress);
        convertView.setTag(holder);
        return convertView;
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

        if (hideDate) {
            hideViews(holder.dateComposed);
        } else {
            //noinspection ConstantConditions
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
        return (message.getFrom().equals(UserManager.getInstance().getMainUser().get_id()));
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
        public ProgressBar progress;
        //TODO add more fields as we support different media/file types
    }

    int[] layoutResources = {
            R.layout.message_item_session_date,
            R.layout.list_item_message_outgoing,
            R.layout.list_item_message_incoming
    };
}
