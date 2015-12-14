package com.pairapp.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.pairapp.util.FileUtils;
import com.pairapp.util.PLog;
import com.rey.material.drawable.RippleDrawable;
import com.rey.material.widget.RippleManager;

public class RoundedImageView extends ImageView {
    private static final String TAG = RoundedImageView.class.getSimpleName();
    private RippleManager mRippleManager;

    public RoundedImageView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();

        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;

        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        final Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        if (copy == null) {
            return;
        }
        Bitmap roundedBitmap = getCroppedBitmap(copy, getWidth());
        if (roundedBitmap == null) {
            return;
        }
        canvas.drawBitmap(roundedBitmap, 0, 0, null);
    }

    public static Bitmap getCroppedBitmap(Bitmap bmp, int size) {
        Bitmap newBmp;
        if (bmp.getWidth() != size || bmp.getHeight() != size) {
            float free = Runtime.getRuntime().maxMemory();
            if (free < FileUtils.ONE_MB * 2) {
                PLog.d(TAG, "not enough memory");
                return null;
            }
            newBmp = Bitmap.createScaledBitmap(bmp, size, size, false);
            if (newBmp == null) {
                return null;
            }
        } else newBmp = bmp;

        Rect rect = new Rect(0, 0, newBmp.getWidth(), newBmp.getHeight());
        Bitmap output = Bitmap.createBitmap(newBmp.getWidth(),
                newBmp.getHeight(), Bitmap.Config.ARGB_8888);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
//        paint.setColor(Color.parseColor("#BAB399"));

        Canvas canvas = new Canvas(output);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(newBmp.getWidth() / 2 + 0f, newBmp.getHeight() / 2 + 0f,
                newBmp.getWidth() / 2 + 0f, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(newBmp, rect, rect, paint);

        return output;
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        applyStyle(context, attrs, defStyleAttr, defStyleRes);
    }

    public void applyStyle(int resId) {
        applyStyle(getContext(), null, 0, resId);
    }

    private void applyStyle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        getRippleManager().onCreate(this, context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    public void setBackgroundDrawable(Drawable drawable) {
        Drawable background = getBackground();
        if (background instanceof RippleDrawable && !(drawable instanceof RippleDrawable))
            ((RippleDrawable) background).setBackgroundDrawable(drawable);
        else
            super.setBackgroundDrawable(drawable);
    }

    protected RippleManager getRippleManager() {
        if (mRippleManager == null) {
            synchronized (RippleManager.class) {
                if (mRippleManager == null)
                    mRippleManager = new RippleManager();
            }
        }

        return mRippleManager;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        RippleManager rippleManager = getRippleManager();
        if (l == rippleManager)
            super.setOnClickListener(l);
        else {
            rippleManager.setOnClickListener(l);
            setOnClickListener(rippleManager);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        return getRippleManager().onTouchEvent(event) || result;
    }

}
