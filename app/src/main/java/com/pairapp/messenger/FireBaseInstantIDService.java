package com.pairapp.messenger;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.pairapp.data.UserManager;
import com.pairapp.util.PLog;
import com.pairapp.util.Task;
import com.pairapp.util.TaskManager;
import com.path.android.jobqueue.Params;

import org.json.JSONException;
import org.json.JSONObject;

public class FireBaseInstantIDService extends FirebaseInstanceIdService {

    public FireBaseInstantIDService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onTokenRefresh() {
        synchronized (FireBaseInstantIDService.class) {
            refrehsToken();
        }
    }

    private static void refrehsToken() {
        String token = FirebaseInstanceId.getInstance().getToken();
        TaskManager.cancelJobSync(RefreshFirebaseInstanceIDJob.TOKEN_REFRESH);
        if (UserManager.getInstance().isUserVerified()) {
            TaskManager.runJob(RefreshFirebaseInstanceIDJob.create(token));
        }
    }

    public static String getInstanceID() {
        return FirebaseInstanceId.getInstance().getToken();
    }

    @SuppressWarnings("WeakerAccess")
    private static final class RefreshFirebaseInstanceIDJob extends Task {

        public static final String PUSH_ID = "pushId";
        public static final String TAG = RefreshFirebaseInstanceIDJob.class.getSimpleName();
        public static final String TOKEN_REFRESH = "tokenRefresh";
        private String newInstanceID;

        public RefreshFirebaseInstanceIDJob() {
        }

        private RefreshFirebaseInstanceIDJob(String newInstanceID, Params params) {
            super(params);
            this.newInstanceID = newInstanceID;
        }

        public static Task create(String newInstanceID) {
            Params params = new Params(1000);
            params.setPersistent(true);
            params.addTags(TOKEN_REFRESH);
            params.setRequiresNetwork(true);
            params.setGroupId("tokenGroup");
            return new RefreshFirebaseInstanceIDJob(newInstanceID, params);
        }

        @Override
        protected JSONObject toJSON() {
            try {
                JSONObject object = new JSONObject();
                object.put(PUSH_ID, newInstanceID);
                return object;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected Task fromJSON(JSONObject jsonObject) {
            try {
                return create(jsonObject.getString(PUSH_ID));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onAdded() {
            PLog.d(TAG, "job added");
        }

        @Override
        public void onRun() throws Throwable {
            UserManager instance = UserManager.getInstance();
            instance.updatePushID(newInstanceID);
        }

        @Override
        protected int getRetryLimit() {
            return 10000;
        }

        @Override
        protected void onCancel() {
            PLog.d(TAG, "cancelled");
        }
    }
}
