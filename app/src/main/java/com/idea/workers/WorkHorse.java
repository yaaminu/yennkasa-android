package com.idea.workers;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;

import com.idea.util.Config;
import com.idea.util.PLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class WorkHorse extends IntentService {

    static final String FILE_META_OPERATION = "FileMetaOperation";
    private static final String TAG = WorkHorse.class.getSimpleName();
    private static final String LINKS = WorkHorse.class.getName() + ".links";
    public static final String FILE_META_OPERATION_MULTI = "FILE_META_OPERATION_multi";
    public static final String BODY = "body";
    AttachmentsApi api;

    public WorkHorse() {
        super(TAG);
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.getFilesMetaDataApiUrl())
                .setLog(new AndroidLog(TAG))
                .setLogLevel(com.idea.net.file_service.BuildConfig.DEBUG ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade requestFacade) {
                        requestFacade.addHeader("Authorization", "kiibodaS3crite");
                    }
                }).build();
        api = adapter.create(AttachmentsApi.class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (FILE_META_OPERATION.equals(action)) {
                handleAction(intent.getStringExtra(BODY));
                checkForFailedOperations();
            } else if (FILE_META_OPERATION_MULTI.equals(action)) {
                List<String> data = intent.getStringArrayListExtra(BODY);
                if (data != null) {
                    try {
                        List<JSONObject> array = new ArrayList<>();
                        for (String item : data) {
                            array.add(new JSONObject(item));
                        }
                        api.markMultipleForDeletion(array);
                    } catch (JSONException e) {
                        throw new RuntimeException(e.getCause());
                    }
                }
            }
        }
    }

    private void checkForFailedOperations() {
        SharedPreferences sharedPreferences = getSharedPreferences(LINKS, MODE_PRIVATE);
        Map<String, ?> all = sharedPreferences.getAll();
        if (all != null) {
            ArrayList<String> data = new ArrayList<>(all.keySet());
            Intent intent = new Intent(this, WorkHorse.class);
            intent.setAction(FILE_META_OPERATION_MULTI);
            intent.putStringArrayListExtra(BODY, data);
            startService(intent);
            sharedPreferences.edit().clear().apply();
        }
    }

    private void handleAction(String data) {
        try {
            JSONObject obj = new JSONObject(data);
            Iterator<String> it = obj.keys();
            Map<String, String> map = new HashMap<>();
            while (it.hasNext()) {
                String key = it.next();
                map.put(key, obj.getString(key));
            }
            try {
                api.markForDeletion(map);
            } catch (RetrofitError err) {

                SharedPreferences prefs = getSharedPreferences(LINKS, MODE_PRIVATE);
                prefs.edit().putString(data, "dummyValue").apply();
                PLog.d(TAG, "error while reporting file upload");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
