package com.pair.pairapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;

import com.pair.data.Conversation;
import com.pair.data.User;
import com.pair.pairapp.ui.ContactFragment;
import com.pair.pairapp.ui.ConversationsFragment;
import com.pair.pairapp.ui.GroupsFragment;
import com.pair.pairapp.ui.SideBarFragment;
import com.pair.util.Config;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends ActionBarActivity implements SideBarFragment.MenuCallback {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String ARG_TITLE = "title";
    private UserManager userManager;
    private DrawerLayout drawer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GcmHelper.checkPlayServices(this)) {
            userManager = UserManager.getInstance(getApplication());
            User user = userManager.getMainUser();
            if (user == null) {
                gotoSetUpActivity();
            } else {
                setContentView(R.layout.activity_main);
                drawer = (DrawerLayout) findViewById(R.id.drawer);

                Fragment fragment = findFragment(Conversation.class.getSimpleName());
                if (fragment == null) {
                    fragment = new ConversationsFragment();
                    setArgs("Conversations", fragment);
                }
                addFragment(fragment);
            }
        } else {
            Log.e(TAG, "Google cloud messaging not available on this device");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pair_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Logging out");
            progressDialog.show();
            userManager.LogOut(this, new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    progressDialog.dismiss();
                    if (e == null) {
                        Config.disableComponents();
                        gotoSetUpActivity();
                        finish();
                    } else {
                        UiHelpers.showErrorDialog(MainActivity.this, e.getMessage());
                    }
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void gotoSetUpActivity() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish();
    }

    private void addFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment, fragment.getClass().getSimpleName())
                .commit();
    }

    @Override
    public void onItemSelected(int position, String recommendedTitle) {
        drawer.closeDrawer(Gravity.START);
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = findFragment(ConversationsFragment.class.getSimpleName());
                if (fragment == null) {
                    fragment = new ConversationsFragment();
                    setArgs(recommendedTitle, fragment);
                }
                break;
            case 1:
                fragment = findFragment(ContactFragment.class.getSimpleName());
                if (fragment == null) {
                    fragment = new ContactFragment();
                    setArgs(recommendedTitle, fragment);
                }
                break;
            case 2:
                fragment = findFragment(GroupsFragment.class.getSimpleName());
                if (fragment == null) {
                    fragment = new GroupsFragment();
                    setArgs(recommendedTitle, fragment);
                }
                break;
            case 3:
                UiHelpers.gotoProfileActivity(this, userManager.getMainUser().get_id());
                return;
            default:
                throw new AssertionError("impossible"); //redundant but safe
        }
        addFragment(fragment);
    }

    private void setArgs(String recommendedTitle, Fragment fragment) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_TITLE, recommendedTitle);
        fragment.setArguments(bundle);
    }

    private Fragment findFragment(String tag) {
        return getSupportFragmentManager().findFragmentByTag(tag);
    }
}
