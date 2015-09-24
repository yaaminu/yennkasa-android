package com.pair.ui;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;

import com.pair.pairapp.R;

public class LoginSignupPrompt extends PairAppBaseActivity implements NoticeFragment.NoticeFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_signup_prompt);
    }

    @Override
    public Spanned getNoticeText() {
        return Html.fromHtml("<p>" + "Hi there! thanks for installing PairApp.".toUpperCase() + "<br/>" + "Please take a few seconds to set things up".toUpperCase() + "</p>");
    }

    @Override
    public CharSequence getActionText() {
        return "Get Started".toUpperCase();
    }

    @Override
    public void onAction() {
        setResult(RESULT_OK);
        finish();
    }

}
