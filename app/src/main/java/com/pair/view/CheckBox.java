package com.pair.view;

import android.content.Context;
import android.util.AttributeSet;

import com.rey.material.drawable.CheckBoxDrawable;

/**
 * @author Null-Pointer on 9/21/2015.
 */
public class CheckBox extends com.rey.material.widget.CheckBox {

    public CheckBox(Context context) {
        super(context);
    }

    public CheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setChecked(boolean checked) {
        if (mButtonDrawable instanceof CheckBoxDrawable) {
            CheckBoxDrawable drawable = (CheckBoxDrawable) mButtonDrawable;
            drawable.setAnimEnable(false);
            super.setChecked(checked);
            drawable.setAnimEnable(true);
        } else
            super.setChecked(checked);
    }

    public void setCheckedAnimated(boolean checked) {
        super.setChecked(checked);
    }
}
