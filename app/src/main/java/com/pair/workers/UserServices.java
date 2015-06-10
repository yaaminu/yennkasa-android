package com.pair.workers;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.pair.data.User;
import com.pair.pairapp.BuildConfig;
import com.pair.util.UserManager;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/9/2015.
 */
public class UserServices extends IntentService {
    public static final String TAG = UserServices.class.getSimpleName();
    public static final String ACTION_FETCH_FRIENDS = "fetchFriends";
    public static final String ACTION = "action";

    /**
     * Creates an IntentService.  Invoked by subclass' constructor.
     */
    public UserServices() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getStringExtra(ACTION).equals(ACTION_FETCH_FRIENDS)) {
            //do work here
            UserManager manager = UserManager.getInstance(this.getApplication());
            try {
                String[] projections = {
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                };
                Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projections, null, null, null);
                //FIXME change this to an sql query
                //for now i am just looping over the returned list and removing those who are already fetched.
                List<String> numbers = cursorToList(cursor, projections[0]);
                cursor.close();
                Realm realm = Realm.getInstance(this);
                RealmResults results = realm.where(User.class).findAll();
                Iterator<User> iterator = results.iterator();
                while (iterator.hasNext()){
                    User user = iterator.next();
                    if (numbers.contains(user.get_id())) {
                        Log.i(TAG,"found: " + user.get_id());
                        numbers.remove(user.get_id());
                    }
                }
                //close realm;
                realm.close();
                Log.i(TAG, numbers.toString());
                if (numbers.isEmpty()) { //all contacts fetched.. this should rarely happen
                    Log.i(TAG, "all friends synced");
                    return;
                }
                manager.fetchFriends(numbers, new UserManager.FriendsFetchCallback() {
                    @Override
                    public void done(Exception e) {
                        if (e == null) {
                            Log.i(TAG, "finished fetching friends");
                        } else {
                            Log.e(TAG, "error while fetching friends");
                        }
                    }
                });
            } catch (JSONException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private List<String> cursorToList(Cursor cursor, String columnName) throws JSONException {
        List<String> array = new ArrayList<>();
        cursor.moveToFirst();
        while (cursor.moveToNext()) {
            //TODO make sure we add only digits probably with the help of a regex
            String number = cursor.getString(cursor.getColumnIndexOrThrow(columnName));
            number = number.replace(")", "").replace("(", "").replace(" ", "").replace("-", ""); //normalize numbers
            array.add(number);
        }

        Log.i(TAG, array.toString());
        return array;
        //caller must close the cursor
    }


}
