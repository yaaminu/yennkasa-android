package com.pair.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.pair.data.SelectedItem;
import com.pair.pairapp.R;
import com.rey.material.text.style.ContactChipSpan;
import com.rey.material.util.ThemeUtil;
import com.rey.material.util.TypefaceUtil;
import com.rey.material.widget.EditText;
import com.rey.material.widget.ListPopupWindow;
import com.rey.material.widget.ListView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.HashMap;

public class ContactEditText extends EditText {


    private HashMap<String, SelectedItem> mSelectedItemMap;

    private int mDefaultAvatarId = R.drawable.ic_user;
    private int mSpanHeight;
    private int mSpanMaxWidth;
    private int mSpanPaddingLeft;
    private int mSpanPaddingRight;
    private Typeface mSpanTypeface;
    private int mSpanTextSize;
    private int mSpanTextColor;
    private int mSpanBackgroundColor;
    private int mSpanSpacing;

    private ContactReplaceAdapter mReplacementAdapter;
    private ListPopupWindow mReplacementPopup;
    private SelectedItemSpan mSelectedSpan;

    private SelectedItemSpan mTouchedSpan;
    private Listener mListener;

    public ContactEditText(Context context) {
        super(context);

        init(context, null, 0, 0);
    }

    public ContactEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0, 0);
    }

    public ContactEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr, 0);
    }

    public ContactEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ContactEditText, defStyleAttr, defStyleRes);

        mSpanHeight = a.getDimensionPixelSize(R.styleable.ContactEditText_cet_spanHeight, ThemeUtil.dpToPx(context, 32));
        mSpanMaxWidth = a.getDimensionPixelSize(R.styleable.ContactEditText_cet_spanMaxWidth, ThemeUtil.dpToPx(context, 150));
        mSpanPaddingLeft = a.getDimensionPixelOffset(R.styleable.ContactEditText_cet_spanPaddingLeft, ThemeUtil.dpToPx(context, 8));
        mSpanPaddingRight = a.getDimensionPixelOffset(R.styleable.ContactEditText_cet_spanPaddingRight, ThemeUtil.dpToPx(context, 12));
        mSpanTypeface = Typeface.DEFAULT;
        mSpanTextSize = a.getDimensionPixelSize(R.styleable.ContactEditText_cet_spanTextSize, ThemeUtil.spToPx(context, 14));
        mSpanTextColor = a.getColor(R.styleable.ContactEditText_cet_spanTextColor, 0xFF000000);
        mSpanBackgroundColor = a.getColor(R.styleable.ContactEditText_cet_spanBackgroundColor, 0xFFE0E0E0);
        mSpanSpacing = a.getDimensionPixelOffset(R.styleable.ContactEditText_cet_spanSpacing, ThemeUtil.dpToPx(context, 4));

        String familyName = a.getString(R.styleable.ContactEditText_cet_spanFontFamily);
        int style = a.getInteger(R.styleable.ContactEditText_cet_spanTextStyle, Typeface.NORMAL);

        mSpanTypeface = TypefaceUtil.load(context, familyName, style);

        a.recycle();

        mSelectedItemMap = new HashMap<>();

        setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        addTextChangedListener(new ContactTextWatcher());

        setLineSpacing(mSpanSpacing, 1);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public SelectedItem[] getSelectedItems() {
        SelectedItemSpan[] spans = getText().getSpans(0, getText().length(), SelectedItemSpan.class);

        if (spans == null || spans.length == 0)
            return null;

        SelectedItem[] recipients = new SelectedItem[spans.length];
        for (int i = 0; i < spans.length; i++)
            recipients[i] = spans[i].getSelectedItem();

        return recipients;
    }

    public void setSelectedItems(SelectedItem[] recipients) {
        SelectedItem[] oldItems = getSelectedItems();
        mSelectedItemMap.clear();
        if (recipients == null) {
            setText(null);
            return;
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String separator = ", ";
        for (SelectedItem recipient : recipients) {
            int start = ssb.length();
            ssb.append(recipient.itemDescription)
                    .append(separator);

            int end = ssb.length();
            ssb.setSpan(new SelectedItemSpan(recipient), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mSelectedItemMap.put(recipient.itemDescription, recipient);
        }

        setText(ssb, TextView.BufferType.SPANNABLE);
        setSelection(ssb.length());
        if (mListener != null) {
            mListener.onContentChanged(getSelectedItems(), oldItems);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchedSpan = getTouchedSpan(event);
                if (mTouchedSpan != null)
                    return true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchedSpan != null) {
                    if (mTouchedSpan != getTouchedSpan(event))
                        mTouchedSpan = null;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTouchedSpan != null) {
                    onSpanClick(mTouchedSpan);
                    mTouchedSpan = null;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mTouchedSpan != null) {
                    mTouchedSpan = null;
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private SelectedItemSpan getTouchedSpan(MotionEvent event) {
        int off = getOffsetForPosition(event.getX(), event.getY());

        SelectedItemSpan[] spans = getText().getSpans(off, off, SelectedItemSpan.class);

        if (spans.length > 0) {
            float x = convertToLocalHorizontalCoordinate(event.getX());
            for (int i = 0; i < spans.length; i++)
                if (spans[i].mX <= x && spans[i].mX + spans[i].mWidth >= x)
                    return spans[i];
        }

        return null;
    }


    private void onSpanClick(SelectedItemSpan span) {
        if (span != mSelectedSpan) {
            dismissReplacementPopup();
            mSelectedSpan = span;
            if (mReplacementAdapter == null)
                mReplacementAdapter = new ContactReplaceAdapter(mSelectedSpan.getSelectedItem());
            else
                mReplacementAdapter.setSelectedItem(mSelectedSpan.getSelectedItem());

            mReplacementPopup = new ListPopupWindow(getContext());
            mReplacementPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mReplacementPopup = null;
                    mSelectedSpan = null;
                }
            });

            mReplacementPopup.setAnchorView(this);
            mReplacementPopup.setModal(true);
            mReplacementPopup.setAdapter(mReplacementAdapter);
            mReplacementPopup.show();

            mReplacementPopup.getListView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    ListView lv = mReplacementPopup.getListView();
                    ViewTreeObserver observer = lv.getViewTreeObserver();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                        observer.removeOnGlobalLayoutListener(this);
                    else
                        observer.removeGlobalOnLayoutListener(this);

                    View v = lv.getChildAt(0);
                    v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    mReplacementPopup.setContentWidth(v.getMeasuredWidth());


                    int[] popupLocation = new int[2];
                    lv.getLocationOnScreen(popupLocation);

                    int[] inputLocation = new int[2];
                    mInputView.getLocationOnScreen(inputLocation);

                    Drawable background = mReplacementPopup.getPopup().getBackground();
                    Rect backgroundPadding = new Rect();
                    int verticalOffset;
                    int horizontalOffset = inputLocation[0] + (int) mSelectedSpan.mX - (popupLocation[0] + backgroundPadding.left);

                    if (background != null)
                        background.getPadding(backgroundPadding);

                    if (inputLocation[1] < popupLocation[1]) //popup show at bottom
                        verticalOffset = inputLocation[1] + mSelectedSpan.mY - (popupLocation[1] + backgroundPadding.top);
                    else
                        verticalOffset = inputLocation[1] + mSelectedSpan.mY + mSpanHeight - (popupLocation[1] + lv.getHeight() - backgroundPadding.bottom);

                    mReplacementPopup.setVerticalOffset(verticalOffset);
                    mReplacementPopup.setHorizontalOffset(horizontalOffset);
                    mReplacementPopup.show();
                }
            });
        }
    }

    private void removeSpan(SelectedItemSpan span) {
        SelectedItem[] oldItems = getSelectedItems();
        Editable text = getText();
        int start = text.getSpanStart(span);
        int end = text.getSpanEnd(span);
        text.delete(start, end);
        text.removeSpan(span);

        if (mListener != null) {
            mListener.onContentChanged(getSelectedItems(), oldItems);
        }
    }

    private void replaceSpan(SelectedItemSpan span, SelectedItem newSelectedItem) {
        SelectedItem[] oldItems = getSelectedItems();
        Editable text = getText();
        int start = text.getSpanStart(span);
        int end = text.getSpanEnd(span);
        String replace = newSelectedItem.itemDescription;
        text.replace(start, end - 2, newSelectedItem.itemDescription, 0, replace.length());
        span.setSelectedItem(newSelectedItem);
        text.setSpan(span, start, start + replace.length() + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mListener != null) {
            mListener.onContentChanged(getSelectedItems(), oldItems);
        }
    }

    private void dismissReplacementPopup() {
        if (mReplacementPopup != null && mReplacementPopup.isShowing()) {
            mReplacementPopup.dismiss();
            mReplacementPopup = null;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.recipients = getSelectedItems();

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;

        super.onRestoreInstanceState(ss.getSuperState());

        setSelectedItems(ss.recipients);
        requestLayout();
    }

    static class SavedState extends BaseSavedState {
        SelectedItem[] recipients;

        /**
         * Constructor called from {@link ContactEditText#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            int length = in.readInt();
            if (length > 0) {
                recipients = new SelectedItem[length];
                in.readTypedArray(recipients, SelectedItem.CREATOR);
            }
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            int length = recipients == null ? 0 : recipients.length;
            out.writeInt(length);
            if (length > 0)
                out.writeTypedArray(recipients, flags);
        }

        @Override
        public String toString() {
            return "ContactEditText.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + "}";
        }

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }


    class ContactReplaceAdapter extends BaseAdapter implements OnClickListener {

        SelectedItem[] mItems;

        private final String COLS[] = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
        };

        public ContactReplaceAdapter(SelectedItem recipient) {
            queryNumber(recipient);
        }

        public void setSelectedItem(SelectedItem recipient) {
            queryNumber(recipient);
        }

        private void queryNumber(SelectedItem recipient) {
            String selection = ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY + "=?";
            String[] selectionArgs = new String[]{recipient.dpURl};
            Cursor cursor = getContext().getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, COLS, selection, selectionArgs, null);
            if (cursor.getCount() > 0) {
                mItems = new SelectedItem[cursor.getCount()];
                mItems[0] = recipient;
                int index = 1;
                while (cursor.moveToNext()) {
                    String number = cursor.getString(0);

                    if (!number.equals(recipient.itemDescription)) {
                        SelectedItem newSelectedItem = new SelectedItem();
                        newSelectedItem.dpURl = recipient.dpURl;
                        newSelectedItem.name = recipient.name;
                        newSelectedItem.itemDescription = number;
                        if (index == mItems.length) {
                            SelectedItem[] newItems = new SelectedItem[mItems.length + 1];
                            System.arraycopy(mItems, 0, newItems, 0, mItems.length);
                            mItems = newItems;
                        }
                        mItems[index] = newSelectedItem;
                        index++;
                    }
                }
            } else
                mItems = new SelectedItem[]{recipient};

            cursor.close();
            notifyDataSetChanged();
        }

        @Override
        public void onClick(View v) {
            int position = (Integer) v.getTag();
            if (position == 0)
                removeSpan(mSelectedSpan);
            else
                replaceSpan(mSelectedSpan, (SelectedItem) mReplacementAdapter.getItem(position));

            Selection.setSelection(getText(), getText().length());

            dismissReplacementPopup();
        }

        @Override
        public int getCount() {
            return mItems == null ? 0 : mItems.length;
        }

        @Override
        public Object getItem(int position) {
            return mItems == null ? null : mItems[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ContactView v = (ContactView) convertView;
            if (v == null) {
                v = (ContactView) LayoutInflater.from(parent.getContext()).inflate(position == 0 ? R.layout.row_contact_selected : R.layout.row_contact_replace, parent, false);
                v.setOnClickListener(this);
            }

            v.setTag(position);

            SelectedItem recipient = (SelectedItem) getItem(position);
            v.setNameText(position == 0 ? recipient.name : null);
            v.setAddressText(recipient.itemDescription);

            if (TextUtils.isEmpty(recipient.dpURl))
                v.setAvatarResource(mDefaultAvatarId);
            else
                Picasso.with(getContext())
                        .load(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, recipient.dpURl))
                        .placeholder(mDefaultAvatarId)
                        .into(v);

            return v;
        }
    }

    class SelectedItemSpan extends ContactChipSpan implements Target {

        private SelectedItem mSelectedItem;
        int mWidth;
        float mX;
        int mY;

        public SelectedItemSpan(SelectedItem recipient) {
            super(TextUtils.isEmpty(recipient.name) ? recipient.itemDescription : recipient.name,
                    mSpanHeight, mSpanMaxWidth, mSpanPaddingLeft, mSpanPaddingRight, mSpanTypeface, mSpanTextColor, mSpanTextSize, mSpanBackgroundColor);
            mSelectedItem = recipient;

            if (TextUtils.isEmpty(recipient.dpURl))
                setImageResource(mDefaultAvatarId);
            else
                Picasso.with(getContext())
                        .load(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, recipient.dpURl))
                        .placeholder(mDefaultAvatarId)
                        .into(this);
        }

        public void setSelectedItem(SelectedItem recipient) {
            mSelectedItem = recipient;
        }

        public SelectedItem getSelectedItem() {
            return mSelectedItem;
        }

        public void setImageResource(int id) {
            if (id == 0)
                return;

            Bitmap bm = BitmapFactory.decodeResource(getContext().getResources(), id);
            setImage(bm);
        }

        public void setImageDrawable(Drawable drawable) {
            if (drawable == null)
                return;

            if (drawable instanceof BitmapDrawable)
                setImage(((BitmapDrawable) drawable).getBitmap());
            else {
                Bitmap bm = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bm);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                setImage(bm);
            }
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            mWidth = super.getSize(paint, text, start, end, fm) + mSpanSpacing;
            return mWidth;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            mX = x;
            mY = top;
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            setImage(bitmap);
            ContactEditText.this.invalidate();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            setImageDrawable(errorDrawable);
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            setImageDrawable(placeHolderDrawable);
        }
    }

    class ContactTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            dismissReplacementPopup();
        }

    }

    public interface Listener {
        void onContentChanged(SelectedItem[] newItems, SelectedItem[] oldItems);
    }
}
