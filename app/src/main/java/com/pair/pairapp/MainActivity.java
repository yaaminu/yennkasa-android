package com.pair.pairapp;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.messenger.MessageDispatcher;
import com.pair.net.Dispatcher;

import java.util.Collection;

import io.realm.Realm;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final TextView tv = (TextView) findViewById(R.id.textView1);
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        realm.clear(Message.class);
        realm.commitTransaction();
        realm.close();
        final BaseJsonAdapter<Message> adapter = new BaseJsonAdapter<Message>() {
            @Override
            public JsonObject toJson(Message message) {
                JsonObject obj = new JsonObject();

                obj.addProperty("from", message.getFrom());
                obj.addProperty("to", message.getTo());
                obj.addProperty("state", message.getState());
                obj.addProperty("id", message.getId());
                obj.addProperty("messageBody", message.getMessageBody());
                obj.addProperty("dateComposed", message.getDateComposed().toString());
                return obj;
            }

            @Override
            public JsonArray toJson(Collection<Message> messages) {
                JsonArray array = new JsonArray();
                for (Message message : messages) {
                    array.add(toJson(message));
                }
                return array;
            }
        };

        final Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
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
        };
        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simulateDispatch(adapter, monitor);
            }
        });
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
}
