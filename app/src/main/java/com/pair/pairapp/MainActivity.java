package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.Dispatcher;
import com.pair.util.GcmHelper;
import com.pair.util.UserManager;

import io.realm.Realm;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private BaseJsonAdapter<Message> adapter;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (GcmHelper.checkPlayServices(this)) {
            //available
            userManager = UserManager.getInstance(this.getApplication());
            User user = userManager.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(this, SetUpActivity.class);
                startActivity(intent);
                finish();
            } else {
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new InboxFragment()).commit();
            }
        } else {
            Log.e(TAG, "no google cloud services available on this device");
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        GcmHelper.checkPlayServices(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pair_app, menu);
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

    class Monitor implements Dispatcher.DispatcherMonitor {

        TextView tv;

        public Monitor(TextView tv) {
            this.tv = tv;
        }

        @Override
        public void onSendFailed(String reason, long messageId) {
            Log.w(TAG, reason);
            Realm realm = Realm.getInstance(MainActivity.this);
            realm.beginTransaction();
            Message message = realm.where(Message.class).equalTo("id", messageId).findFirst();
            message.setState(Message.SEND_FAILED);
            realm.commitTransaction();
            realm.close();

            tv.append(message.getMessageBody() + " : " + message.getState() + "\n");

        }

        @Override
        public void onSendSucceeded(long messageId) {
            Realm realm = Realm.getInstance(MainActivity.this);
            realm.beginTransaction();
            Message message = realm.where(Message.class).equalTo("id", messageId).findFirst();
            message.setState(Message.SENT);
            realm.commitTransaction();
            realm.close();
            tv.append(message.getMessageBody() + " : " + message.getState() + "\n");

        }
    }
}
