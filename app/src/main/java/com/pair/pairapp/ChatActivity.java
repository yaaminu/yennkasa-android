package com.pair.pairapp;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.pair.adapter.MessageJsonAdapter;
import com.pair.adapter.MessagesAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.MessageDispatcher;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmResults;


public class ChatActivity extends ActionBarActivity implements View.OnClickListener {
    public static final String TAG = ChatActivity.class.getSimpleName();
    public static final String PEER_NAME = "peer name";
    public static final String PEER_ID = "peer id";
    private User peer;
    private Conversation currConversation;
    private Realm realm;
    private ListView messagesListView;
    private EditText editText;
    private Button sendButton;
    private MessageDispatcher dispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = realm.getInstance(this);
        setContentView(R.layout.activity_chat);
        editText = ((EditText) findViewById(R.id.et_inputMsg));
        sendButton = ((Button) findViewById(R.id.btn_send));
        dispatcher = MessageDispatcher.getInstance(new MessageJsonAdapter(), null, 10);
        sendButton.setOnClickListener(this);
        Bundle bundle = getIntent().getExtras();
        String peerName = bundle.getString(PEER_NAME);
        getSupportActionBar().setTitle(peerName);
        String peerId = bundle.getString(PEER_ID);
        peer = realm.where(User.class).equalTo("_id", peerId).findFirst();
        currConversation = realm.where(Conversation.class).equalTo("peerId",peerId).findFirst();
        RealmResults<Message> messages = realm.where(Message.class).equalTo("from", peer.get_id()).or().equalTo("to", peer.get_id()).findAllSorted("dateComposed", true);
        MessagesAdapter adapter = new MessagesAdapter(this, messages, true);
        messagesListView = ((ListView) findViewById(R.id.lv_messages));
        messagesListView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.chat_menu, menu);
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
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        String content = UiHelpers.getFieldContent(editText);
        editText.setText(""); //clear the text field
        //TODO use a regular expression to validate the message body
        if (!content.isEmpty()) {
            realm.beginTransaction();
            Message message = realm.createObject(Message.class);
            message.setMessageBody(content);
            message.setTo(peer.get_id());
            message.setFrom(getCurrentUser().get_id());
            message.setDateComposed(new Date());
            //generate a unique id
            long messageCount = realm.where(Message.class).count() + 1;
            message.setId(messageCount+ "@" + getCurrentUser().get_id() + "@" +System.currentTimeMillis());
            message.setState(Message.PENDING);
            currConversation.setLastMessage(message);
            currConversation.setLastActiveTime(message.getDateComposed());
            realm.commitTransaction();
            dispatcher.dispatch(message);
        }
    }

    private User getCurrentUser() {
        return UserManager.getInstance(getApplication()).getCurrentUser();
    }
}
