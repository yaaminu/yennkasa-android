package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Chat;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.Dispatcher;
import com.pair.util.GcmHelper;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String INBOX = "inbox";
    private BaseJsonAdapter<Message> adapter;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addChatsInBackGround();
        if (GcmHelper.checkPlayServices(this)) {
            //available
            userManager = UserManager.getInstance(this.getApplication());
            User user = userManager.getCurrentUser();
            if (user == null) {
                goToSetup();
            } else {
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new InboxFragment(), INBOX).commit();
            }
        } else {
            Log.e(TAG, "error: no google cloud services available on this device");
        }

    }

    private void goToSetup() {
        Intent intent = new Intent(this, SetUpActivity.class);
        startActivity(intent);
        finish(); //remove this activity from the stack
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
        if (id == R.id.action_logout) {
            userManager.LogOut(this);
            goToSetup();
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

    private void addChatsInBackGround(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                Realm realm = Realm.getInstance(getApplicationContext());
                if(realm.allObjects(Chat.class).size() > 0) {
                    realm.close();
                    return;
                }
                realm.beginTransaction();
                User user = realm.createObject(User.class);
                user.set_id("dummy id");
                user.setName("Amin");

                for(int i=0; i<15; i++){
                    Chat chat = realm.createObject(Chat.class);
                    chat.setLastActiveTime(new Date());
                    chat.setSummary("test chat " + i);
                    chat.setPeer(user);
                }
                realm.commitTransaction();
                realm.close();
            }
        }).start();
    }
}
