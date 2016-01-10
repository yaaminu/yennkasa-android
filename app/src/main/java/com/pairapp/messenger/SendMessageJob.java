package com.pairapp.messenger;

import com.pairapp.util.Task;

/**
 * by Null-Pointer on 11/28/2015.
 */
abstract class SendMessageJob extends Task {
//
//    private static final String TAG = SendMessageJob.class.getName();
//    static final Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
//        final Map<String, Pair<String, Integer>> progressMap = new ConcurrentHashMap<>();
//
//        @Override
//        public void onDispatchFailed(String reason, String messageId) {
//            PLog.d(PairAppClient.TAG, "message with id : %s dispatch failed with reason: " + reason, messageId);
//            LiveCenter.releaseProgressTag(messageId);
//            progressMap.remove(messageId);
//            cancelNotification(messageId);
//            Realm realm = Message.REALM(Config.getApplicationContext());
//            try {
//                Message realmMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
//                realm.beginTransaction();
//                if (realmMessage != null && realmMessage.isValid()) {
//                    if (realmMessage.getState() == Message.STATE_PENDING) {
//                        realmMessage.setState(Message.STATE_SEND_FAILED);
//                    }
//                }
//                realm.commitTransaction();
//            } finally {
//                realm.close();
//            }
//        }
//
//        @Override
//        public void onDispatchSucceeded(String messageId) {
//            PLog.d(PairAppClient.TAG, "message with id : %s dispatched successfully", messageId);
//            LiveCenter.releaseProgressTag(messageId);
//            progressMap.remove(messageId);
//            cancelNotification(messageId);
//        }
//
//        private void cancelNotification(String messageId) {
//            NotificationManagerCompat manager = NotificationManagerCompat.from(Config.getApplicationContext());// get
//            manager.cancel(messageId, PairAppClient.not_id);
//        }
//
//        @Override
//        public void onProgress(String id, int progress, int max) {
//            Context context = Config.getApplicationContext();
//            LiveCenter.updateProgress(id, progress);
//            Pair<String, Integer> previousProgress = progressMap.get(id);
//            if (previousProgress != null && previousProgress.first != null && previousProgress.second != null) {
//                if (previousProgress.second >= progress) {
//                    PLog.d(PairAppClient.TAG, "late progress report");
//                    return;
//                }
//            } else {
//                Realm messageRealm = Message.REALM(context);
//                Message message = messageRealm.where(Message.class).equalTo(Message.FIELD_ID, id).findFirst();
//                if (message == null) {
//                    return;
//                }
//                previousProgress = new Pair<>(message.getTo(), progress);
//                progressMap.put(id, previousProgress);
//                messageRealm.close();
//            }
//
//            Intent intent = new Intent(context, ChatActivity.class);
//            intent.putExtra(ChatActivity.EXTRA_PEER_ID, previousProgress.first);
//            Notification notification = new NotificationCompat.Builder(context)
//                    .setContentTitle(context.getString(R.string.upload_progress))
//                    .setProgress(100, 1, true)
//                    .setContentIntent(PendingIntent.getActivity(context, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
//                    .setContentText(context.getString(R.string.loading))
//                    .setSmallIcon(R.drawable.ic_stat_icon).build();
//            NotificationManagerCompat manager = NotificationManagerCompat.from(context);// getSystemService(NOTIFICATION_SERVICE));
//            manager.notify(id, PairAppClient.not_id, notification);
//        }
//
//        @Override
//        public void onAllDispatched() {
//
//        }
//    };
//    private final Message message;
//    private static final String PRIORITY = "priority",
//            MESSAGE = "message";
//
//    private SendMessageJob(Params params, Message message) {
//        super(params);
//        this.message = message;
//    }
//
//    //required no-arg constructor
//    @SuppressWarnings("unused")
//    public SendMessageJob() {
//        this.message = null;
//    }
//
//    @Override
//    protected void onCancel() {
//
//    }
//
//    @Override
//    public void onAdded() {
//
//    }
//
//    @Override
//    public void onRun() throws Throwable {
//        if (this.message == null) {
//            throw new IllegalStateException("message cannot be null");
//        }
//        PairAppClient.doDispatch(this.message);
//    }
//
//    @Override
//    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
//        if (throwable instanceof ParseException) {
//            ParseException e = (ParseException) throwable;
//            int code = e.getCode();
//            if (code == ParseException.EXCEEDED_QUOTA) {
//                // fail silently now
//                return RetryConstraint.CANCEL;
//            } else if (code == ParseException.REQUEST_LIMIT_EXCEEDED) {
//                return RetryConstraint.createExponentialBackoff(runCount,/*one minute*/ 1000 * 60);
//            }
//        } else if (throwable instanceof RuntimeException) { //we wont eat up this error
//            if (BuildConfig.DEBUG) {
//                throw new RuntimeException(throwable);
//            }
//            PLog.d(TAG, "onRun  raised a runtime exception", throwable);
//            return RetryConstraint.CANCEL;
//        } else {
//            PLog.d(TAG, "onRun raised unknown checked exception!", throwable);
//            if (BuildConfig.DEBUG) {
//                throw new RuntimeException(throwable); //crash and burn
//            }
//        }
//        return RetryConstraint.createExponentialBackoff(runCount, 1000);
//    }
//
//    static SendMessageJob makeNew(Message message) {
//        return makeNew(1, message);
//    }
//
//    private static SendMessageJob makeNew(int priority, Message message) {
//
//        if (message == null) {
//            throw new IllegalArgumentException("message == null");
//        }
//        if (message.getType() == Message.TYPE_DATE_MESSAGE || message.getType() == Message.TYPE_TYPING_MESSAGE) {
//            throw new IllegalArgumentException("invalid message, type is " + message.getType());
//        }
//        //defensive copy
//        message = Message.copy(message, true);
//        Params params = new Params(priority);
//        params.setPersistent(true);
//        params.setGroupId("messageStatusGroup");
//        params.setRequiresNetwork(true);
//        params.addTags("messageStatus");
//        return new SendMessageJob(params, message);
//    }
//
//    @Override
//    protected JSONObject toJSON() {
//        JSONObject object = new JSONObject();
//        try {
//            object.put(PRIORITY, getPriority());
//            object.put(MESSAGE, Message.toJSON(message));
//            return object;
//        } catch (JSONException e) {
//            throw new RuntimeException(e.getCause());
//        }
//    }
//
//    @Override
//    protected Task fromJSON(JSONObject json) {
//        try {
//            Message message = Message.fromJSON(json.getString(MESSAGE));
//            int priority = json.getInt(PRIORITY);
//            return makeNew(priority, message);
//        } catch (JSONException e) {
//            throw new RuntimeException(e.getCause());
//        }
//    }
}
