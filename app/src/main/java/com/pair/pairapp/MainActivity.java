package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;

import com.pair.data.User;
import com.pair.pairapp.ui.ContactFragment;
import com.pair.pairapp.ui.CoversationsFragment;
import com.pair.pairapp.ui.FriendsFragment;
import com.pair.pairapp.ui.SetUpActivity;
import com.pair.pairapp.ui.SideBarFragment;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.pair.workers.RealmHelper;
import com.pair.workers.UserServices;

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
        setContentView(R.layout.activity_main);
        if (GcmHelper.checkPlayServices(this)) {
            userManager = UserManager.getInstance(getApplication());
            User user = userManager.getCurrentUser();
            if (user == null) {
                gotoSetUpActivity();
            } else {
                //attempt to update user friends at startup
                Intent intent = new Intent(this, UserServices.class);
                intent.putExtra(UserServices.ACTION,UserServices.ACTION_FETCH_FRIENDS);
                startService(intent);

                drawer = (DrawerLayout) findViewById(R.id.drawer);
                Fragment fragment = new CoversationsFragment();
                Bundle bundle = new Bundle();
                bundle.putString(ARG_TITLE, "Conversations");
                fragment.setArguments(bundle);
                addFragment(fragment);
                RealmHelper.runRealmOperation(this);
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
            userManager.LogOut(this, new UserManager.LogOutCallback() {
                @Override
                public void done(Exception e) {
                    if(e == null) {
                        gotoSetUpActivity();
                        finish();
                    }else{
                        UiHelpers.showErrorDialog(MainActivity.this,e.getMessage());
                    }
                }
            });
            return true;
        } else if (item.getItemId() == R.id.action_seed_users) {

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
                .replace(R.id.container, fragment)
                .commit();
    }

    @Override
    public void onItemSelected(int position, String recommendedTitle) {
        drawer.closeDrawer(Gravity.LEFT);
        Log.i(TAG, "clicked " + recommendedTitle + " @ position: " + position);
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new CoversationsFragment();
                break;
            case 1:
                fragment = new FriendsFragment();
                break;
            case 2:
                fragment = new ContactFragment();
                break;
            case 3://fall through
            case 5:
            default:
                fragment = new CoversationsFragment();
                break; //redundant but safe
        }
        Bundle bundle = new Bundle();
        bundle.putString(ARG_TITLE, recommendedTitle);
        fragment.setArguments(bundle);
        addFragment(fragment);
    }

}
