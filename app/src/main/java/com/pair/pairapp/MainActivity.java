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
import com.pair.util.GcmHelper;
import com.pair.util.UserManager;

import io.realm.Realm;

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
                drawer = (DrawerLayout) findViewById(R.id.drawer);
                Fragment fragment = new CoversationsFragment();
                Bundle bundle = new Bundle();
                bundle.putString(ARG_TITLE, "Conversations");
                fragment.setArguments(bundle);
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
            userManager.LogOut(this);
            gotoSetUpActivity();
            finish();
            return true;
        }else if(item.getItemId() == R.id.action_seed_users){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    seedUsers();
                }
            }).start();
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
        Fragment fragment = null;
        switch (position) {
            case 0:
                fragment = new CoversationsFragment();
                break;
            case 1:
                fragment = new FriendsFragment();
                break;
            case 2://fall through
            case 3:
            case 5:
            default:
                fragment = new CoversationsFragment();
                break;
        }
        Bundle bundle = new Bundle();
        bundle.putString(ARG_TITLE, recommendedTitle);
        fragment.setArguments(bundle);
        addFragment(fragment);
    }

    private void seedUsers() {
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        for (int i = 0; i < 20; i++) {
            User user = realm.createObject(User.class);
            user.set_id("User " + i);
            user.setName("user " + i);
        }
        realm.commitTransaction();
        realm.close();
    }

}
