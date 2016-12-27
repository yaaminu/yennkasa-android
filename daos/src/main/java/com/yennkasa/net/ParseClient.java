package com.yennkasa.net;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.telephony.SmsManager;
import android.text.TextUtils;

import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.data.BuildConfig;
import com.yennkasa.data.R;
import com.yennkasa.data.User;
import com.yennkasa.util.Config;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.MediaUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.parse.Parse;
import com.parse.ParseACL;
import com.parse.ParseCloud;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseInstallation;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_ACCOUNT_CREATED;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_CITY;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_COUNTRY;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_DP;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_ID;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_LAST_ACTIVITY;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_MEMBERS;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_NAME;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_TOKEN;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_VERIFIED;
import static com.yennkasa.net.PARSE_CONSTANTS.FIELD_VERSION;
import static com.yennkasa.net.PARSE_CONSTANTS.GROUP_CLASS_NAME;
import static com.yennkasa.net.PARSE_CONSTANTS.SEARCHABLE;
import static com.parse.ParseCloud.callFunction;


/**
 * @author Null-Pointer on 8/27/2015.
 */
public class ParseClient implements UserApiV2 {
    private static final String TAG = ParseClient.class.getSimpleName();
    public static final String PENDING_DP = "pendingDp";
    public static final String VERIFICATION_CODE_RECEIVED = "code.verification.recived";
    public static final String PUSH_ID = "pushId";
    public static final String FIELD_ADMIN_JSON = "adminJson";
    private static ParseClient INSTANCE;
    private DisplayPictureFileClient displayPictureFileClient;
    private final Preprocessor preProcessor;


    @SuppressWarnings("FieldCanBeLocal")
    private final Preprocessor dummyProcessor = new Preprocessor() {
        @Override
        public void process(User user) {

        }

        @Override
        public void process(Collection<User> users) {

        }
    };
    private int appVersion;


    public static ParseClient getInstance(Preprocessor processor, int appVersion) {
        synchronized (ParseClient.class) {
            if (INSTANCE == null) {
                INSTANCE = new ParseClient(processor, appVersion);
            }
        }
        return INSTANCE;
    }

    private static int genVerificationToken() {
        SecureRandom random = new SecureRandom();
        //maximum of 10000 and minimum of 99999
        int num = (int) Math.abs(random.nextDouble() * (99999 - 10000) + 10000);
        //we need an unsigned (+ve) number
        num = Math.abs(num);

        PLog.d(TAG, num + ""); //fixme remove this
        return num;
    }

    @Override
    public void registerUser(final User user, final Callback<User> callback) {
        PLog.i(TAG, "register user: user info " + user.getName() + ":" + user.getUserId());
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                doRegisterUser(user, callback);
            }
        }, true);
    }


    private synchronized void doRegisterUser(User user, final Callback<User> callback) {

        GenericUtils.ensureNotEmpty(user.getUserId(), user.getName(), user.getCountry());
        GenericUtils.ensureConditionTrue(!user.getName().startsWith("@"), "Name must start wit @");
        String _id = user.getUserId(),
                name = "@" + user.getName(),
                country = user.getCountry();
        user.setName(name);
        user.setVersion(appVersion);
        try {
            ParseUser parseUser = new ParseUser();
            parseUser.setPassword(makePass(user));
            parseUser.setUsername(name);
            parseUser.put(FIELD_ID, _id);
            parseUser.put(FIELD_NAME, name);
            parseUser.put(FIELD_COUNTRY, country);
            parseUser.put(FIELD_ACCOUNT_CREATED, new Date());
            parseUser.put(FIELD_VERSION, this.appVersion);
            parseUser.put(FIELD_LAST_ACTIVITY, new Date());
            parseUser.put(FIELD_VERIFIED, false);
            parseUser.put(FIELD_DP, "avatar_empty");
            parseUser.put(SEARCHABLE, true);
            parseUser.put(FIELD_CITY, user.getCityName());
            parseUser.put(FIELD_TOKEN, genVerificationToken() + "");
            parseUser.signUp();
            parseUser = ParseUser.getCurrentUser();
            user = parseObjectToUser(parseUser);
            notifyCallback(callback, null, user);
        } catch (ParseException e) {
            PLog.d(TAG, e.getMessage(), e);
            int code = e.getCode();
            if (code == ParseException.OBJECT_NOT_FOUND || code == ParseException.USERNAME_TAKEN) {
                PLog.d(TAG, "user already signed, logging in user instead");
                doLogIn(user, callback);
            } else if (code == ParseException.CONNECTION_FAILED) {
                notifyCallback(callback, new Exception(Config.getApplicationContext().getString(R.string.st_unable_to_connect)), null);
            } else {
                notifyCallback(callback, new Exception(Config.getApplicationContext().getString(R.string.an_error_occurred)), null);
            }
        }
    }

    private void sendToken(String userId, int verificationToken) {
        final String destinationAddress = "+" + userId;
        String message = Config.getApplicationContext().getString(R.string.verification_code) + "  " + verificationToken;
        // TODO: 12/27/16 make a cloud function call
        SmsManager.getDefault()
                .sendTextMessage(destinationAddress, null, message, null, null);
    }


    @Override
    public void logIn(final User user, final Callback<User> callback) {
        PLog.d(TAG, "logging in user: " + user.getUserId());
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                doLogIn(user, callback);
            }
        }, true);
    }


    private synchronized void doLogIn(User user, Callback<User> callback) {
        try {
            ParseUser parseUser = ParseUser.getCurrentUser();
            if (parseUser != null) {
                ParseUser.logOut();
            }
            parseUser = ParseUser.logIn(user.getName(), makePass(user));
            parseUser.put(FIELD_VERIFIED, false);
            parseUser.put(FIELD_TOKEN, "" + genVerificationToken());
            parseUser.put(FIELD_CITY, user.getCityName());
            user = parseObjectToUser(parseUser);
            parseUser.save();
            notifyCallback(callback, null, user);
        } catch (ParseException e) {
            String message;
            final int errorCode = e.getCode();
            if (errorCode == ParseException.OBJECT_NOT_FOUND) {
                message = GenericUtils.getString(R.string.user_name_taken, user.getName());
            } else if (errorCode == ParseException.CONNECTION_FAILED) {
                message = Config.getApplicationContext().getString(R.string.st_unable_to_connect);
            } else {
                message = Config.getApplicationContext().getString(R.string.an_error_occurred);
            }
            notifyCallback(callback, new Exception(message), null);
        }
    }


    private String makePass(User user) {
        return FileUtils.hash(user.getUserId().getBytes());
    }

    @SuppressLint("HardwareIds")
    private void registerForPushes(String userId, String pushID, String publicKey) throws ParseException {
        callFunction("setPublicKeyForUser", Collections.singletonMap("publicKey", publicKey));
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        installation.put(FIELD_ID, userId);
        //required by the server for verification in installation related queries
        installation.put(PUSH_ID, pushID);
        installation.save();
//        installation.put("saved", true);
        requestForToken(pushID, false);
    }


    @NonNull
    private String requestForToken(String pushID, boolean changingPushId) throws ParseException {
        Map<String, String> params = new HashMap<>();
        params.put(PUSH_ID, pushID);
        if (changingPushId) {
            params.put("newPushId", "true");
        }
        String token = callFunction("genToken", params);
        PLog.d(TAG, "request new token");
        PLog.d(TAG, token);
        GenericUtils.ensureNotEmpty(token);
        ParseObject object = new ParseObject("tokens");
        object.put(PARSE_CONSTANTS.FIELD_AUTH_TOKEN, token);
        object.pin();
        return token;
    }

    @Override
    public void getUser(final String id, final Callback<User> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetUser(id, response);
            }
        }, true);
    }

    public void doGetUser(String id, Callback<User> response) {
        try {
            ParseObject object = makeUserParseQuery().whereEqualTo(FIELD_ID, id).getFirst();
            User user = parseObjectToUser(object);
            preProcessor.process(user);
            notifyCallback(response, null, user);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroups(final String id, final Callback<List<User>> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetGroups(id, response);
            }
        }, true);
    }

    private void doGetGroups(String id, Callback<List<User>> response) {
        try {
            List<ParseObject> objects = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_MEMBERS, id).find();
            List<User> groups = new ArrayList<>(objects.size());
            for (ParseObject object : objects) {
                if (!object.has(FIELD_ADMIN_JSON)) {
                    object.delete();
                    continue;
                }
                groups.add(parseObjectToGroup(object));
            }
            preProcessor.process(groups);
            notifyCallback(response, null, groups);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void changeDp(final String userOrGroup, final String id, final File file, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doChangeDp(userOrGroup, id, file, response);
            }
        }, true);
    }

    private void doChangeDp(final String userOrGroup, final String id, final File file, final Callback<HttpResponse> response) {
        if (!userOrGroup.equals("users") && !userOrGroup.equals("groups")) {
            throw new IllegalArgumentException("unknown placeholder");
        }
        displayPictureFileClient.changeDp(id, file, new FileApi.FileSaveCallback() {
            @Override
            public void done(FileClientException e, String url) {
                if (e == null) {
                    try {
                        dpLock.acquire();
                        PLog.d(TAG, "dp: " + url);
                        SharedPreferences preferences = getPendingDpChanges();
                        //a refresh of our data set will pick up this change
                        try {
                            ParseObject object;
                            if (userOrGroup.equals("groups")) {
                                object = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(PARSE_CONSTANTS.FIELD_ID, id).getFirst();
                            } else {
                                object = ParseUser.getCurrentUser();
                            }
                            object.put(FIELD_DP, url);
                            object.save();

                        } catch (ParseException e2) {
                            PLog.e(TAG, e2.getMessage(), e2.getCause());
                            preferences.edit().putString(id + PENDING_DP, url)
                                    .putString(FileUtils.hash(url.getBytes()), file.getAbsolutePath())
                                    .apply();
                        }
                        notifyCallback(response, null, new HttpResponse(200, url));
                    } catch (InterruptedException e1) {
                        PLog.d(TAG, "thread: %s interrupted while waiting to acquire semaphore", "" + Thread.currentThread().getId());
                    } finally {
                        dpLock.release();
                    }
                } else {
                    PLog.e(TAG, e.getMessage(), e.getCause());
                    notifyCallback(response, e, new HttpResponse(400, "error during dp change"));
                }
            }
        }, new FileApi.ProgressListener() {
            @Override
            public void onProgress(long expected, long transferred) {
                PLog.i(TAG, "transferred: %s, expected: %s", transferred, expected);
            }
        });
    }

    @Override
    public void createGroup(final String by, final String name, final Collection<String> members, final Callback<User> response) {
//        TaskManager.execute(new Runnable() {
//            public void run() {
//                doCreateGroup(by, name, members, response);
//            }
//        }, true);
        throw new UnsupportedOperationException();
    }

    private void doCreateGroup(String by, String name, Collection<String> members, Callback<User> response) {
        try {
            if (members.size() < 3) {
                notifyCallback(response, new Exception("A group must start with at least 3 or more members"), null);
                return;
            }

            Map<String, String> params = new HashMap<>(5);
            params.put(FIELD_DP, "avatar_empty");
            params.put(FIELD_NAME, name);
            params.put(FIELD_MEMBERS, TextUtils.join(",", members));
            ParseObject results = callFunction("createGroup", params);
            User freshGroup = parseObjectToGroup(results);
            preProcessor.process(freshGroup);
            notifyCallback(response, null, freshGroup);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroup(final String id, final Callback<User> callback) {
//        TaskManager.execute(new Runnable() {
//            public void run() {
//                doGetGroup(id, callback);
//            }
//        }, true);
        throw new UnsupportedOperationException();
    }

    private void doGetGroup(String id, Callback<User> callback) {
        try {
            ParseObject object = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            User group = parseObjectToGroup(object);

            preProcessor.process(group);
            notifyCallback(callback, null, group);
        } catch (ParseException e) {
            notifyCallback(callback, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroupMembers(final String id, final Callback<List<User>> response) {
//        TaskManager.execute(new Runnable() {
//            public void run() {
//                doGetGroupMembers(id, response);
//            }
//        }, true);
        throw new UnsupportedOperationException();
    }

    private void doGetGroupMembers(String id, Callback<List<User>> response) {
        try {

            List<String> membersId = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst().getList(FIELD_MEMBERS);
            final List<ParseUser> groupMembers = makeUserParseQuery().whereContainedIn(FIELD_ID, membersId).find();
            Set<User> members = new HashSet<>(groupMembers.size());
            for (ParseObject groupMember : groupMembers) {
                members.add(parseObjectToUser(groupMember));
            }
            preProcessor.process(members);
            notifyCallback(response, null, new ArrayList<>(members));
        } catch (ParseException e) {
            if (BuildConfig.DEBUG) {
                PLog.e(TAG, e.getMessage(), e.getCause());
            } else {
                PLog.e(TAG, e.getMessage());
            }
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    //good solution will be to query for specific numbers but that is not possible
    //as we don't know how the user has stored the numbers in the people(contact) app
    //for eg +233 20 444 1069 could be stored in multiple unpredictable ways.
    //the contact manager can retrieve all the contacts standardised them to how
    private ParseClient(Preprocessor preProcessor, int appVersion) {
        init(Config.getApplication(), appVersion);
        displayPictureFileClient = DisplayPictureFileClient.createInstance(ParseFileClient.getInstance());
        this.preProcessor = preProcessor == null ? dummyProcessor : preProcessor;
    }

//    //we store user ids and return them so that we do a quick hit test to map
//    // the names to our parse objects(we will not persist those names on the backend)
//    private void processParseObject(final ParseObject object) {
//        ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
//            @Override
//            public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
//                final String userId = object.getString(PARSE_CONSTANTS.FIELD_ID);
//                if (contact.numberInIEE_Format.equals(userId)) {
//                    object.put(PARSE_CONSTANTS.FIELD_NAME, contact.name);//we will not save this change
//                    throw new AbortOperation("done"); //contact manager will stop processing contacts
//                }
//                return false; //we don't care about return values
//            }
//        }, null);
//    }
//
//    private void processParseObjects(final List<ParseObject> objects) {
//        ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
//            Set<String> processed = new HashSet<>(objects.size());
//
//            @Override
//            public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
//                if (processed.size() == objects.size()) {
//                    throw new AbortOperation("done");
//                }
//                if (!processed.add(contact.numberInIEE_Format)) {
//                    return false; //we don't care about return values
//                }
//                for (ParseObject groupMember : objects) {
//                    final String userId = groupMember.getString(PARSE_CONSTANTS.FIELD_ID);
//                    if (contact.numberInIEE_Format.equals(userId)) {
//                        groupMember.put(PARSE_CONSTANTS.FIELD_NAME, contact.name);//we will not save this change
//                        break;
//                    }
//                }
//                return false; //we don't care about return values
//            }
//        }, null);
//    }

    @Override
    public void addMembersToGroup(final String id, final String by, final Collection<String> members, final Callback<HttpResponse> response) {
//        TaskManager.execute(new Runnable() {
//            public void run() {
//                doAddMembersToGroup(id, members, response);
//            }
//        }, true);
        throw new UnsupportedOperationException();
    }

    private void doAddMembersToGroup(String id, Collection<String> members, Callback<HttpResponse> response) {
        if (members.size() <= 0) {
            notifyCallback(response, new Exception("at least one member is required"), new HttpResponse(400, " bad request"));
            return;
        }
        try {
            Map<String, String> params = new HashMap<>(2);
            params.put("members", TextUtils.join(",", members));
            params.put("userId", id);
            callFunction("addMembers", params);
            notifyCallback(response, null, new HttpResponse(200, "successfully added " + members.size() + " new members"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void removeMembersFromGroup(final String id, final String by, final List<String> members, final Callback<HttpResponse> response) {
//        TaskManager.execute(new Runnable() {
//            public void run() {
//                doRemoveMembersFromGroup(id, members, response);
//            }
//        }, true);
        throw new UnsupportedOperationException();
    }

    private void doRemoveMembersFromGroup(String id, List<String> members, Callback<HttpResponse> response) {
        if (members.size() <= 0) {
            notifyCallback(response, new Exception("at least one member is required"), new HttpResponse(400, " bad request"));
            return;
        }
        try {
            Map<String, String> params = new HashMap<>(2);
            params.put("members", TextUtils.join(",", members));
            params.put("userId", id);
            callFunction("removeMembers", params);
            notifyCallback(response, null, new HttpResponse(200, "successfully removed " + members.size() + "  members"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void leaveGroup(final String id, final String userId, final String password, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doLeaveGroup(id, userId, response);
            }
        }, true);
    }

    private void doLeaveGroup(String id, String userId, Callback<HttpResponse> response) {
        doRemoveMembersFromGroup(id, Collections.singletonList(userId), response);
    }

    @Override
    public void verifyUser(final String userId, final String token, final String pushID, final String publicKey, final Callback<SessionData> callback) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doVerifyUser(userId, token, pushID, publicKey, callback);
            }
        }, true);
    }

    private void doVerifyUser(String userId, String token, String pushId, String publicKey, Callback<SessionData> callback) {
        GenericUtils.ensureNotEmpty(userId, token, pushId, publicKey);
        // FIXME: 12/21/16 make a cloud function call
        try {
            ParseObject object = ParseUser.getCurrentUser();
            if (object == null) {
                throw new IllegalStateException("user cannot be null");
            }
            if (!object.has(FIELD_TOKEN)) {
                throw new IllegalStateException("token not sent");
            }

            String token1 = object.getString(FIELD_TOKEN);
            if (token1 == null || token.isEmpty()) {
                throw new IllegalStateException("token malformed");
            }
            if (token1.equals(token)) {
                object.put(FIELD_VERIFIED, true);
                object.save();
                registerForPushes(userId, pushId, publicKey);
                final String accessToken = ParseInstallation.getCurrentInstallation().getInstallationId();
                notifyCallback(callback, null, new SessionData(FileUtils.hash(accessToken.getBytes()), userId));
            } else {
                String errorMessage = Config.getApplicationContext().getString(R.string.invalid_verification_code);
                notifyCallback(callback, new Exception(errorMessage), null);
            }
        } catch (ParseException e) {
            int errorCode = e.getCode();
            int errorMessageRes;
            switch (errorCode) {
                case ParseException.CONNECTION_FAILED:
                    errorMessageRes = R.string.st_unable_to_connect;
                    break;
                case ParseException.OBJECT_NOT_FOUND:
                    errorMessageRes = R.string.invalid_verification_code;
                    break;
                default:
                    errorMessageRes = R.string.an_error_occurred;
                    break;
            }
            String errorMessage = Config.getApplicationContext().getString(errorMessageRes);
            notifyCallback(callback, new Exception(errorMessage), null);
        }
    }

    @Override
    public void resendToken(final String userId, final String password, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doResendToken(userId, password, response);
            }
        }, true);
    }

    private synchronized void doResendToken(String userId, @SuppressWarnings("UnusedParameters") String password, Callback<HttpResponse> response) {
        try {
            int token = genVerificationToken();
            ParseObject object = ParseUser.getCurrentUser();
            if (object == null) {
                throw new IllegalStateException("user cannot be null");
            }
            object.put(FIELD_TOKEN, "" + token);
            object.save();
            sendToken(userId, token);
            notifyCallback(response, null, new HttpResponse(200, "successfully reset token"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public HttpResponse resetUnverifiedAccount(String userId) {
        oops("not supported");
        return null;
    }

    private void oops(String message) {
        throw new UnsupportedOperationException(message);
    }

    @Override
    public HttpResponse requestPasswordReset(String number) {
        oops("not supported");
        return null;
    }

    private Exception prepareErrorReport(ParseException e) {
        String message;
        if (e.getCode() == ParseException.CONNECTION_FAILED) {
            message = Config.getApplicationContext().getString(R.string.st_unable_to_connect);
        } else {
            message = Config.getApplicationContext().getString(R.string.an_error_occurred);
        }
        return new Exception(message, e.getCause());
    }

    @NonNull
    private User parseObjectToUser(ParseObject object) {
        PLog.v(TAG, "processing: " + object.toString());
        User user = new User();
        user.setName(object.getString(FIELD_NAME));
        user.setUserId(object.getString(FIELD_ID));
        user.setDP(resolveDp(object));
        user.setLastActivity(object.getDate(FIELD_LAST_ACTIVITY).getTime());
        user.setCountry(object.getString(FIELD_COUNTRY));
        user.setType(User.TYPE_NORMAL_USER);
        user.setInContacts(false);
        user.setVersion(object.getInt(FIELD_VERSION));
        user.setCity(object.getString(PARSE_CONSTANTS.FIELD_CITY));
        return user;
    }

    private final Semaphore dpLock = new Semaphore(1, true);

    private String resolveDp(ParseObject object) {
        String userId = object.getString(FIELD_ID),
                userDp = object.getString(FIELD_DP);
        PLog.d(TAG, "user dp %s", userDp);
        try {
            SharedPreferences preferences = getPendingDpChanges();
            dpLock.acquire();

            String pendingDp = preferences.getString(userId + PENDING_DP, null);
            if (pendingDp != null && !pendingDp.equals(userDp)) {
                try {
                    object.put(FIELD_DP, pendingDp);
                    object.save();
                    preferences.edit().remove(userId + PENDING_DP).commit();
                    PLog.v(TAG, "user with id: " + userId + " changed dp from " + userDp + " to " + pendingDp);
                    preferences.edit().remove(FileUtils.hash(userDp.getBytes())).commit();
                    String mappedDp = preferences.getString(FileUtils.hash(pendingDp.getBytes()), "");
                    final File file = new File(mappedDp);
                    if (file.exists()) {
                        pendingDp = file.getAbsolutePath();
                    }
                    return pendingDp;
                } catch (ParseException ignored) {
                }
            }
            String mappedDp = preferences.getString(FileUtils.hash(userDp.getBytes()), "");
            final File file = new File(mappedDp);
            if (file.exists()) {
                userDp = file.getAbsolutePath();
            }
        } catch (InterruptedException ignore) {
            PLog.d(TAG, "thread: %s interrupted while waiting to acquire semaphore", Thread.currentThread().getId());
            Thread.currentThread().interrupt();
        } finally {
            dpLock.release();
        }
        return userDp;
    }

    private SharedPreferences getPendingDpChanges() {
        String prefs = ParseInstallation.getCurrentInstallation().getString(FIELD_ID);
        return Config.getPreferences(prefs);
    }

    @NonNull
    private User parseObjectToGroup(ParseObject object) throws ParseException {
        PLog.v(TAG, "processing: " + object.toString());
        try {
            User user = new User();
            user.setName(object.getString(FIELD_NAME));
            user.setUserId(object.getString(FIELD_ID));
            user.setDP(resolveDp(object));
            String adminJSON = object.getString(FIELD_ADMIN_JSON);
            user.setType(User.TYPE_GROUP);
            JSONObject jsonObject = new JSONObject(adminJSON);
            User admin = new User();
            admin.setCity(jsonObject.optString(FIELD_CITY, ""));
            admin.setVersion(jsonObject.getInt(FIELD_VERSION));
            admin.setName(jsonObject.getString(FIELD_NAME));
            admin.setCountry(jsonObject.getString(FIELD_COUNTRY));
            admin.setDP(jsonObject.getString(FIELD_DP));
            admin.setUserId(jsonObject.getString(FIELD_ID));
            admin.setType(User.TYPE_NORMAL_USER);
            user.setAdmin(admin);
            return user;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyCallback(Callback<User> callback, Exception error, User user) {
        callback.done(error, user);
    }

    private void notifyCallback(Callback<List<User>> callback, Exception error, List<User> users) {
        callback.done(error, users);
    }

    private void notifyCallback(Callback<HttpResponse> response, Exception e, HttpResponse httpResponse) {
        response.done(e, httpResponse);
    }

    private void notifyCallback(Callback<SessionData> response, Exception e, SessionData sessionData) {
        response.done(e, sessionData);
    }

    @NonNull
    private ParseQuery<ParseUser> makeUserParseQuery() {
        return ParseUser.getQuery();
    }

    private ParseQuery<ParseObject> makeParseQuery(String className) {
        return ParseQuery.getQuery(className);
    }

    private void deleteMessage(String recipient, String messageBody) {
        try {
            Config.getApplicationContext().getContentResolver().delete(Uri.parse("content://sms/sent"), "address = ? and body = ?",
                    new String[]{recipient, messageBody});

            Config.getApplicationContext().getContentResolver().delete(Uri.parse("content://sms/outbox"), "address = ? and body = ?",
                    new String[]{recipient, messageBody});
        } catch (Exception e) {
            PLog.d(TAG, "error while deleting sms message from inbox " + e.getMessage(), e.getCause());
            // if(BuildConfig.DEBUG){

            // }
            // throw new RuntimeException(); //we cannot handle this
        }
    }

    //todo send as a mail
    public static void sendFeedBack(JSONObject report, List<String> files) {
        ParseObject object = new ParseObject(PARSE_CONSTANTS.FEEDBACK_CLASS_NAME);
        object.put("message", report.toString());
        for (int i = 0; i < files.size(); i++) {
            File file = new File(files.get(i));
            if (file.exists() && MediaUtils.isImage(file.getAbsolutePath())) {
                try {
                    ParseFile parseFile = new ParseFile(file.getName(), IOUtils.toByteArray(new FileInputStream(file)));
                    parseFile.save();
                    object.put("screenShot" + (i + 1), parseFile);
                } catch (IOException e) {
                    PLog.d(TAG, e.getMessage(), e.getCause());
                } catch (ParseException e) { // FIXME: 11/25/2015 handle this error well
                    PLog.d(TAG, "send feedback: unknown error while saving parseFile");
                }
            }
        }
        object.saveEventually();
    }

    private void init(Application application, int appVersion) {
        if (application == null) {
            throw new IllegalArgumentException("application is null!");
        }
        this.appVersion = appVersion;
        ParseACL defaultAcl = new ParseACL();
        defaultAcl.setPublicReadAccess(true);
        defaultAcl.setPublicWriteAccess(true);
        ParseACL.setDefaultACL(defaultAcl, true);
        Parse.setLogLevel(BuildConfig.DEBUG ? Parse.LOG_LEVEL_VERBOSE : Parse.LOG_LEVEL_NONE);
        Parse.initialize(new Parse.Configuration.Builder(application)
                .enableLocalDataStore()
                .server(Config.getDataServer())
                .applicationId("RcCxnXwO1mpkSNrU9u4zMtxQac4uabLNIFa662ZY")
                .clientKey("f1ad1Vfjisr7mVBDSeoFO1DobD6OaLkggHvT2Nk4")
                .build());
    }

    @Override
    public synchronized void sendVerificationToken(final String userId, final Callback<HttpResponse> callback) {

        TaskManager.execute(new Runnable() {
            public void run() {
                ParseObject object = ParseUser.getCurrentUser();
                if (object == null) {
                    throw new IllegalStateException("user cannot be null");
                }
                String token = object.getString(PARSE_CONSTANTS.FIELD_TOKEN);
                sendToken(userId, Integer.parseInt(token));
                notifyCallback(callback, null, new HttpResponse(200, "token sent"));
            }
        }, true);
    }

    @Override
    public boolean isUserAuthenticated() {
        return ParseUser.getCurrentUser() != null;
    }

    @Override
    public String getAuthToken() {
        if (!isUserAuthenticated()) return "";
        try {
            return ParseQuery.getQuery("tokens").fromPin().getFirst().getString(PARSE_CONSTANTS.FIELD_AUTH_TOKEN);
        } catch (ParseException e) {
            PLog.e(TAG, e.getMessage(), e);
            return "";
        }
    }

    @NonNull
    @Override
    public String newAuthToken() throws YennkasaException {
        String pushID = ParseInstallation.getCurrentInstallation().getString(PUSH_ID);
        GenericUtils.ensureNotEmpty(pushID);
        try {
            return requestForToken(pushID, false);
        } catch (ParseException e) {
            PLog.d(TAG, e.getMessage(), e);
            throw new YennkasaException(e.getMessage(), e.getCode() + "");
        }
    }

    @Override
    public void updatePushID(String newPushID) throws YennkasaException {
        ParseUser user = ParseUser.getCurrentUser();
        boolean userVerified = user.getBoolean(FIELD_VERIFIED);
        if (userVerified) {
            try {
                requestForToken(newPushID, true);
            } catch (ParseException e) {
                throw new YennkasaException(e.getMessage(), "unknown");
            }
        } else {
            PLog.d(TAG, "no user logged in, cannot update push id");
        }
    }

    @Nullable
    @Override
    public Pair<String, Long> getSinchToken() {
        ParseUser user = ParseUser.getCurrentUser();
        boolean userVerified = user.getBoolean(FIELD_VERIFIED);
        if (userVerified) {
            try {
                Map<String, ?> params = new HashMap<>(0);
                Map<String, Object> results = callFunction("genSinchToken", params);
                return Pair.create((String) results.get("token"), (long) ((Integer) results.get("sequence")));
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void enableSearch(final boolean enableSearch, final Callback<Boolean> callback) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                synchronized (ParseClient.class) {
                    try {
                        SystemClock.sleep(3000);
                        ParseUser currentUser = ParseUser.getCurrentUser();
                        boolean existingValue = currentUser.getBoolean(SEARCHABLE);
                        if (existingValue != enableSearch) {
                            currentUser.put(SEARCHABLE, enableSearch);
                            currentUser.save();
                        }
                        callback.done(null, true);
                    } catch (ParseException e) {
                        callback.done(prepareErrorReport(e), false);
                    }
                }
            }
        }, true);
    }

    @Override
    public void search(final String query, final Callback<List<User>> callback) {
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(5000);
                doSearch(query, callback);
            }
        }, true);
    }

    private void doSearch(String query, Callback<List<User>> callback) {
        try {
            List<ParseObject> results =
                    ParseCloud.callFunction("search", Collections.singletonMap("query", query));

            if (results.isEmpty()) {
                callback.done(new Exception("Your search return no results"), null);
            }
            List<User> users = new ArrayList<>(results.size());
            for (ParseObject result : results) {
                users.add(parseObjectToUser(result));
            }
            callback.done(null, users);
        } catch (ParseException e) {
            callback.done(prepareErrorReport(e), null);
        }
    }

    @Override
    public void getPublicKeyForUser(String userId, Callback<String> callback) {
        GenericUtils.ensureNotEmpty(userId);
        try {
            String publicKey = callFunction("getPublicKeyForUser", Collections.singletonMap(PARSE_CONSTANTS.FIELD_ID, userId));
            callback.done(null, publicKey);
        } catch (ParseException e) {
            PLog.e(TAG, e.getMessage(), e);
            callback.done(prepareErrorReport(e), null);
        }
    }

    @Nullable
    @Override
    public String getPublicKeyForUserSync(String userId) {
        GenericUtils.ensureNotEmpty(userId);
        try {
            String publicKeyForUser = callFunction("getPublicKeyForUser", Collections.singletonMap(PARSE_CONSTANTS.FIELD_ID, userId));
            PLog.d(TAG, "public key for %s is: %s", userId, publicKeyForUser);
            return publicKeyForUser;
        } catch (ParseException e) {
            PLog.e(TAG, e.getMessage(), e);
            return null;
        }
    }
}
