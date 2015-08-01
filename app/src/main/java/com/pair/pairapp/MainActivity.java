package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.TypedValue;
import android.view.Menu;

import com.pair.pairapp.ui.ContactFragment;
import com.pair.pairapp.ui.ConversationsFragment;
import com.pair.pairapp.ui.FriendsActivity;
import com.pair.pairapp.ui.GroupsFragment;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.ArrayList;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class MainActivity extends ActionBarActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static String groupName;
    public static final String ARG_TITLE = "title";
    private static int savedPosition = -1;
    public static final int SELECT_USERS_REQUEST = 1001;
    private ViewPager pager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //user cannot get pass this if there is no gcm support as he will be presented a blocking dialog that cannot be dismissed
        GcmHelper.checkPlayServices(this);
        if (UserManager.getInstance().isUserVerified()) {
            setContentView(R.layout.activity_main);
            //noinspection ConstantConditions
            pager = ((ViewPager) findViewById(R.id.vp_pager));
            PagerTabStrip tabStrip = ((PagerTabStrip) findViewById(R.id.pts_title_strip));
            tabStrip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            tabStrip.setDrawFullUnderline(true);
            pager.setAdapter(new MyFragmentStatePagerAdapter(getSupportFragmentManager()));
        } else {
            gotoSetUpActivity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GcmHelper.checkPlayServices(this);
        if (savedPosition != -1) {
            pager.setCurrentItem(savedPosition);
        }
    }

    @Override
    protected void onStop() {
        savedPosition = pager.getCurrentItem();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pair_app, menu);
        return true;
    }

    private void gotoSetUpActivity() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish();
    }

    private class MyFragmentStatePagerAdapter extends FragmentStatePagerAdapter {
        String[] pageTitles;

        public MyFragmentStatePagerAdapter(FragmentManager fm) {
            super(fm);
            pageTitles = getResources().getStringArray(R.array.menuItems);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment;
            switch (position) {
                case 0:
                    fragment = new ConversationsFragment();
                    break;
                case 1:
                    fragment = new ContactFragment();
                    break;
                case 2:
                    fragment = new GroupsFragment();
                    break;
                default:
                    throw new AssertionError("impossible");
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pageTitles[position];
        }
    }

    // FIXME: 7/19/2015 for one or two reasons the request code returned does not match so we don't check for it now
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            ArrayList<String> members = data.getStringArrayListExtra(FriendsActivity.SELECTED_USERS);
            if (members == null || members.isEmpty()) {
                UiHelpers.showErrorDialog(this,getString(R.string.st_could_not_create_group));
                return;
            }
            UserManager.getInstance().createGroup(groupName, members, new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    if (e != null) {
                        UiHelpers.showErrorDialog(getApplicationContext(), e.getMessage());
                    }
                }
            });
        }
    }
}
