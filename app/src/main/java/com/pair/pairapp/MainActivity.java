package com.pair.pairapp;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.MessageJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.MessageDispatcher;
import com.pair.net.Dispatcher;
import com.pair.util.UserManager;

import io.realm.Realm;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private BaseJsonAdapter<Message> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView tv = (TextView) findViewById(R.id.textView1);

        cleanUpRealm();

        User user = new User();
        user.setName("android user");
        user.setPassword("password");
        user.set_id("0266349205");
        user.setGcmRegId("gcmregidlakaal");


        UserManager.getInstance(getApplication()).signUp(user, new UserJsonAdapter(), new UserManager.signUpCallback() {
            @Override
            public void done(Exception e) {
                if (e == null) {
                    Toast.makeText(MainActivity.this, "scucess", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "failed to sign up: error" + e.getMessage());
                    Toast.makeText(MainActivity.this, "failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
        adapter = new MessageJsonAdapter();
        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simulateDispatch(adapter, new Monitor(tv));
            }
        });
    }

    private void cleanUpRealm() {
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        realm.clear(Message.class);
        realm.commitTransaction();
        realm.close();
    }

    private void simulateDispatch(BaseJsonAdapter<Message> adapter, Dispatcher.DispatcherMonitor monitor) {
        MessageDispatcher dispatcher = MessageDispatcher.getInstance(adapter, monitor, 10);
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        long id = realm.allObjects(Message.class).size();
        Message message = realm.createObject(Message.class);
        message.setDateComposed(new java.util.Date());
        message.setId(id + 1);
        message.setMessageBody("this is the message body");
        message.setFrom("sender of message");
        message.setTo("new user id");
        message.setState(Message.PENDING);
        realm.commitTransaction();
        dispatcher.dispatch(message);
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
