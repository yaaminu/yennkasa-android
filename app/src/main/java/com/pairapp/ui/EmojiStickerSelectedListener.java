package com.pairapp.ui;

import android.view.KeyEvent;
import android.widget.EditText;

import vc908.stickerfactory.ui.OnEmojiBackspaceClickListener;
import vc908.stickerfactory.ui.OnStickerSelectedListener;

/**
 * by yaaminu on 12/19/16.
 */
public class EmojiStickerSelectedListener implements
        OnStickerSelectedListener, OnEmojiBackspaceClickListener {

    private final EditText editText;

    public EmojiStickerSelectedListener(EditText editText) {
        this.editText = editText;
    }

    @Override
    public void onEmojiBackspaceClicked() {
        KeyEvent event = new KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL);
        editText.dispatchKeyEvent(event);
    }

    @Override
    public void onStickerSelected(String s) {
        editText.append(s);
    }

    @Override
    public void onEmojiSelected(String s) {
        editText.append(s);
    }
}
