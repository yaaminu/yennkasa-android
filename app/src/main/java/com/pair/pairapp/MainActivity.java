package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.pair.data.User;
import com.pair.util.GcmHelper;
import com.pair.util.UserManager;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends ActionBarActivity implements SideBarFragment.MenuCallback{
    public static final String TAG = MainActivity.class.getSimpleName();
    private UserManager userManager;
    private static final String CONVERSATION_TAG = "conversationFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (GcmHelper.checkPlayServices(this)) {
            userManager = UserManager.getInstance(getApplication());
            User user = userManager.getCurrentUser();
            if (user == null) {
                gotoSetUpActivity();
            } else {
               getSupportFragmentManager()
                       .beginTransaction()
                       .replace(R.id.container,new CoversationsFragment(),CONVERSATION_TAG)
                       .commit();
            }
        } else {
            Log.e(TAG, "Google cloud messaging not available on this device");
        }

    }

    private void gotoSetUpActivity() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onItemSelected(int position, String recommendedTitle) {
        Log.i(TAG,"clicked " + recommendedTitle + " @ position: " + position);
    }
}
