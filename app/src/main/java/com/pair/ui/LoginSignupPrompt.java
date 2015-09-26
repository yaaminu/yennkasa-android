package com.pair.ui;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;

import com.pair.pairapp.R;
import com.pair.util.UiHelpers;

public class LoginSignupPrompt extends PairAppBaseActivity implements NoticeFragment.NoticeFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_signup_prompt);
    }

    @Override
    public Spanned getNoticeText() {

        if (SetUpActivity.getStage() == SetUpActivity.UNKNOWN) {
            return Html.fromHtml(getString(R.string.get_started_noitce).toUpperCase());
        } else {
            return Html.fromHtml(getString(R.string.complete).toUpperCase());
        }
    }

    @Override
    public CharSequence getActionText() {
        if (SetUpActivity.getStage() == SetUpActivity.UNKNOWN) {
            return getString(R.string.title_activity_set_up);
        } else {
            return getString(R.string.st_complete_setup);
        }
    }

    @Override
    public void onAction() {
        UiHelpers.gotoSetUpActivity(this);
        finish();
    }

    @Override
    public void onBackPressed() {
        onAction();
    }
}
