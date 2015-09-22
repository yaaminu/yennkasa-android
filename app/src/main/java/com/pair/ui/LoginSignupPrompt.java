package com.pair.ui;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;

import com.pair.pairapp.R;

public class LoginSignupPrompt extends PairAppBaseActivity implements NoticeFragment.NoticeFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_signup_prompt);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login_signup_prompt, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
