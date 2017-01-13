package com.yennkasa.ui;

/**
 * Created by yaaminu on 1/12/17.
 */
public class ChatSettingsItem {
    public int value;
    public String summary; //summary could actually be value
    public String title;

    public ChatSettingsItem(String title, String summary, int value) {
        this.title = title;
        this.summary = summary;
        this.value = value;
    }
}
