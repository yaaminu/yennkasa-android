package com.idea.net;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;

import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.idea.Errors.PairappException;
import com.idea.data.BuildConfig;
import com.idea.data.ContactsManager;
import com.idea.data.Message;
import com.idea.data.R;
import com.idea.data.User;
import com.idea.util.Config;
import com.idea.util.L;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.parse.Parse;
import com.parse.ParseACL;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParseObject;
import com.parse.ParsePush;
import com.parse.ParseQuery;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.Path;
import retrofit.mime.TypedFile;

import static com.idea.net.PARSE_CONSTANTS.FIELD_ACCOUNT_CREATED;
import static com.idea.net.PARSE_CONSTANTS.FIELD_ADMIN;
import static com.idea.net.PARSE_CONSTANTS.FIELD_COUNTRY;
import static com.idea.net.PARSE_CONSTANTS.FIELD_DP;
import static com.idea.net.PARSE_CONSTANTS.FIELD_HAS_CALL;
import static com.idea.net.PARSE_CONSTANTS.FIELD_ID;
import static com.idea.net.PARSE_CONSTANTS.FIELD_LAST_ACTIVITY;
import static com.idea.net.PARSE_CONSTANTS.FIELD_MEMBERS;
import static com.idea.net.PARSE_CONSTANTS.FIELD_NAME;
import static com.idea.net.PARSE_CONSTANTS.FIELD_STATUS;
import static com.idea.net.PARSE_CONSTANTS.FIELD_TOKEN;
import static com.idea.net.PARSE_CONSTANTS.FIELD_VERIFIED;
import static com.idea.net.PARSE_CONSTANTS.GROUP_CLASS_NAME;
import static com.idea.net.PARSE_CONSTANTS.USER_CLASS_NAME;


/**
 * @author Null-Pointer on 8/27/2015.
 */
public class ParseClient implements UserApiV2 {

    private static final String TAG = ParseClient.class.getSimpleName();
    public static final String PENDING_DP = "pendingDp";
    private static ParseClient INSTANCE;
    private DisplayPictureFileClient displayPictureFileClient;
    private final Preprocessor preProcessor;

    private ParseClient(Preprocessor preProcessor) {
        init(Config.getApplication());
        Map<String, String> credentials;//= UserManager.getInstance().getUserCredentials();
        credentials = new HashMap<>();
        //////////////////////////////////////////////////////////////////////////////
        credentials.put("key", "doTbKQlpZyNZohX7KPYGNQXIghATCx");
        credentials.put("password", "Dq8FLrF7HjeiyJBFGv9acNvOLV1Jqm");
        /////////////////////////////////////////////////////////////////////////////////////
        displayPictureFileClient = DisplayPictureFileClient.createInstance(credentials);
        this.preProcessor = preProcessor == null ? dummyProcessor : preProcessor;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final Preprocessor dummyProcessor = new Preprocessor() {
        @Override
        public void process(User user) {

        }

        @Override
        public void process(Collection<User> users) {

        }
    };

    private void init(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application is null!");
        }
        ParseACL defaultAcl = new ParseACL();
        defaultAcl.setPublicReadAccess(true);
        defaultAcl.setPublicWriteAccess(true);
        ParseACL.setDefaultACL(defaultAcl, true);
        Parse.setLogLevel(Parse.LOG_LEVEL_VERBOSE);
        /***************************************KEYS***************************************************************************/

        Parse.initialize(application, application.getString(R.string.parse_application_id), application.getString(R.string.parse_client_key));

        /******************************************************************************************************************/
    }

    public static ParseClient getInstance(Preprocessor processor) {
        synchronized (ParseClient.class) {
            if (INSTANCE == null) {
                INSTANCE = new ParseClient(processor);
            }
        }
        return INSTANCE;
    }

    private static String genVerificationToken() {
        SecureRandom random = new SecureRandom();
        //maximum of 10000 and minimum of 99999
        int num = (int) Math.abs(random.nextDouble() * (99999 - 10000) + 10000);
        //we need an unsigned (+ve) number
        num = Math.abs(num);

        String token = String.valueOf(num);
        PLog.d(TAG, token); //fixme remove this
        return token;
    }

    @Override
    public void registerUser(@Body final User user, final Callback<User> callback) {
        PLog.d(TAG, "register user: user info " + user.getName() + ":" + user.getUserId());
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                doRegisterUser(user, callback);
            }
        });
    }

    private final Object tokenLock = new Object();

    private void doRegisterUser(User user, final Callback<User> callback) {
        String _id = user.getUserId(),
                name = user.getName(),
                password = user.getPassword(),
                country = user.getCountry();
        synchronized (tokenLock) {
            token = genVerificationToken();
        }
//         if (true) {
//             user.setDP("avartarempty");
//             notifyCallback(callback, null, user);
//             return;
//         }
        try {
            ParseObject existing = makeParseQuery(USER_CLASS_NAME).whereEqualTo(FIELD_ID, _id).getFirst();
            existing.put(FIELD_HAS_CALL, Config.supportsCalling());
            cleanExistingInstallation(_id);

            existing.put(FIELD_TOKEN, token);
            existing.save();
            user = parseObjectToUser(existing);
            registerForPushes(_id);
            notifyCallback(callback, null, user);
            return; //important
        } catch (ParseException e) {
            if (e.getCode() != ParseException.OBJECT_NOT_FOUND) {
                PLog.d(TAG, "encountered error while registering user, message: " + e.getMessage());
                notifyCallback(callback, prepareErrorReport(e), null);
                return;
            }
            PLog.d(TAG, "no account associated with " + _id + " in proceeding to create new account");
            //continue
        }
        try {
            ensureFieldsFilled(_id, name, password, country);

            //should we hash passwords?
            final ParseObject object = ParseObject.create(USER_CLASS_NAME);
            ParseACL acl = makeReadWriteACL();
            object.setACL(acl);
            object.put(FIELD_ID, _id);
            object.put(FIELD_NAME, "@" + name);
            object.put(FIELD_COUNTRY, country);
            object.put(FIELD_ACCOUNT_CREATED, new Date());
            object.put(FIELD_STATUS, "offline");
            object.put(FIELD_LAST_ACTIVITY, new Date());
            object.put(FIELD_TOKEN, token);
            object.put(FIELD_VERIFIED, false);
            object.put(FIELD_DP, "avatar_empty");
            object.put(PARSE_CONSTANTS.FIELD_HAS_CALL, Config.supportsCalling());
            object.save();
            //register user for pushes
            registerForPushes(user.getUserId());
            user = parseObjectToUser(object);
            notifyCallback(callback, null, user);
        } catch (RequiredFieldsError error) {
            notifyCallback(callback, error, null);
        } catch (ParseException e) {
            notifyCallback(callback, prepareErrorReport(e), null);
        }
    }

    private void sendToken(String userId, String verificationToken) {

        SmsManager.getDefault().
                sendTextMessage("+" + userId,
                        null, verificationToken,
                        null, null);
        deleteMessage("+" + userId, verificationToken);
    }

    private void cleanExistingInstallation(String _id) throws ParseException {
        //call a cloud code or something of that sort
//        ParseInstallation installation = ParseInstallation.getQuery().whereEqualTo(FIELD_ID, _id).getFirst();
//        installation.delete();
    }

    @NonNull
    private ParseACL makeReadWriteACL() {
        ParseACL acl = new ParseACL();
        acl.setPublicWriteAccess(true);
        acl.setPublicReadAccess(true);
        return acl;
    }

    @Override
    public void logIn(final User user, final Callback<User> callback) {
        L.d(TAG, "logging in user: " + user.getUserId());
//         if (true) {
//             User copy = User.copy(user);
//             copy.setName("@unnamed");
//             copy.setType(User.TYPE_NORMAL_USER);
//             copy.setDP("avarta_empty");
//             notifyCallback(callback, null, copy);
//             return;
//         }
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                doLogIn(user, callback);
            }
        });
    }

    private void doLogIn(User user, Callback<User> callback) {
        ParseQuery<ParseObject> query = makeParseQuery();
        synchronized (tokenLock) {
            token = genVerificationToken();
        }
        try {
            ParseObject object = query.whereEqualTo(FIELD_ID, user.getUserId()).getFirst();
            object.put(PARSE_CONSTANTS.FIELD_HAS_CALL, Config.supportsCalling());
            object.put(FIELD_TOKEN, token);
            object.put(FIELD_VERIFIED, false);
            cleanExistingInstallation(user.getUserId());
            //push
            registerForPushes(user.getUserId());
            object.save();
            user = parseObjectToUser(object);
            notifyCallback(callback, null, user);
        } catch (ParseException e) {
            String message;
            if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                //noinspection ThrowableInstanceNeverThrown
                message = Config.getApplicationContext().getString(R.string.login_error_message);
            } else if (e.getCode() == ParseException.CONNECTION_FAILED) {
                message = Config.getApplicationContext().getString(R.string.st_unable_to_connect);
            } else {
                message = Config.getApplicationContext().getString(R.string.an_error_occurred);
            }
            notifyCallback(callback, new Exception(message), null);
        }
    }

    private void registerForPushes(String userId) throws ParseException {
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        installation.put(FIELD_ID, userId);
        installation.save();
    }

    @Override
    public void syncContacts(@Body final List<String> userIds, final Callback<List<User>> callback) {
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                doSyncContacts(userIds, callback);
            }
        });
    }

    public void doSyncContacts(List<String> userIds, Callback<List<User>> callback) {
        ParseQuery<ParseObject> query = makeParseQuery();
        try {
            List<ParseObject> objects = query.whereContainedIn(FIELD_ID, userIds).find();
            List<User> users = new ArrayList<>(objects.size());
            for (ParseObject object : objects) {
                users.add(parseObjectToUser(object));
            }
            preProcessor.process(users);
            notifyCallback(callback, null, users);
        } catch (ParseException e) {
            notifyCallback(callback, e, null);
        }
    }

    @Override
    public void getUser(@Path(Message.FIELD_ID) final String id, final Callback<User> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetUser(id, response);
            }
        });
    }

    public void doGetUser(@Path(Message.FIELD_ID) String id, Callback<User> response) {
        try {
            ParseObject object = makeParseQuery(USER_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            User user = parseObjectToUser(object);
            preProcessor.process(user);
            notifyCallback(response, null, user);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroups(@Path(Message.FIELD_ID) final String id, final Callback<List<User>> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetGroups(id, response);
            }
        });
    }

    private void doGetGroups(@Path(Message.FIELD_ID) String id, Callback<List<User>> response) {
        try {
            List<ParseObject> objects = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_MEMBERS, id).find();
            List<User> groups = new ArrayList<>(objects.size());
            for (ParseObject object : objects) {
                groups.add(parseObjectToGroup(object));
            }
            preProcessor.process(groups);
            List<User> admins = new ArrayList<>(groups.size());
            for (User group : groups) {
                admins.add(group.getAdmin());
            }
            preProcessor.process(admins);
            notifyCallback(response, null, groups);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void changeDp(@Path("placeHolder") final String userOrGroup, @Path(Message.FIELD_ID) final String id, @Body final TypedFile file, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doChangeDp(userOrGroup, id, file, response);
            }
        });
    }

    private void doChangeDp(@Path("placeHolder") final String userOrGroup, @Path(Message.FIELD_ID) final String id, @Body TypedFile file, final Callback<HttpResponse> response) {
        if (!userOrGroup.equals("users") && !userOrGroup.equals("groups")) {
            throw new IllegalArgumentException("unknown placeholder");
        }

        displayPictureFileClient.changeDp(id, new File(file.file().getAbsolutePath()), new FileApi.FileSaveCallback() {
            @Override
            public void done(FileClientException e, String url) {
                if (e == null) {
                    PLog.d(TAG, "dp: " + url);
                    notifyCallback(response, null, new HttpResponse(200, url));
                    try {
                        dpLock.acquire();
                    } catch (InterruptedException e1) {
                        dpLock.release();
                        PLog.d(TAG, "thread: %s interrupted while waiting to acquire semaphore", "" + Thread.currentThread().getId());
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        ParseQuery<ParseObject> userOrGroupQuery = makeParseQuery(userOrGroup.equals("users") ? USER_CLASS_NAME : GROUP_CLASS_NAME);
                        ParseObject object = userOrGroupQuery.whereEqualTo(FIELD_ID, id).getFirst();
                        String dp = object.getString(FIELD_DP);
                        if (dp != null && dp.equals(url)) {
                            PLog.d(TAG, "dp already changed... nothing will be done");
                        } else {
                            object.put(FIELD_DP, url);
                            object.save();
                        }
                    } catch (ParseException e2) {
                        PLog.d(TAG, "error while changing dp: " + e2.getMessage(), e2.getCause());
                        SharedPreferences preferences = getPendingDpChanges();
                        preferences.edit().putString(id + PENDING_DP, url).apply();
                    } finally {
                        dpLock.release();
                    }
                } else {
                    notifyCallback(response, e, new HttpResponse(400, "error during dp change"));
                }
            }
        }, new FileApi.ProgressListener() {
            @Override
            public void onProgress(long expected, long transferred) {
                PLog.i(TAG, "dp change progress %s", ((transferred * 100) / expected));
            }
        });
    }

    @Override
    public void createGroup(@Field("createdBy") final String by, @Field("name") final String name, @Field("starters") final Collection<String> members, final Callback<User> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doCreateGroup(by, name, members, response);
            }
        });
    }

    private void doCreateGroup(@Field("createdBy") String by, @Field("name") String name, @Field("starters") Collection<String> members, Callback<User> response) {
        try {
            if (members.size() < 3) {
                notifyCallback(response, new Exception("A group must start with at least 3 or more members"), null);
                return;
            }
            //ensure group does not exist
            try {
                ParseObject group = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, name + "@" + by).getFirst();
                if (group != null) {
                    notifyCallback(response, new Exception(Config.getApplicationContext().getString(R.string.group_already_exist)), null);
                    return;
                }
            } catch (ParseException e) {
                PLog.d(TAG, "group does not exist lets continue");
            }
            final ParseObject admin = makeParseQuery(USER_CLASS_NAME).whereEqualTo(FIELD_ID, by).getFirst();
            admin.fetchIfNeeded();
            ParseObject object = new ParseObject(GROUP_CLASS_NAME);
            object.setACL(makeReadWriteACL());
            object.put(FIELD_ID, name + "@" + by);
            object.put(FIELD_DP, "avartar_empty");
            object.put(FIELD_NAME, name);
            object.put(FIELD_ACCOUNT_CREATED, new Date());
            object.put(FIELD_ADMIN, admin);
            object.addAllUnique(FIELD_MEMBERS, members);
            object.save();
            User freshGroup = parseObjectToGroup(object);
            notifyCallback(response, null, freshGroup);
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroup(@Path(Message.FIELD_ID) final String id, final Callback<User> callback) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetGroup(id, callback);
            }
        });
    }

    private void doGetGroup(@Path(Message.FIELD_ID) String id, Callback<User> callback) {
        try {
            ParseObject object = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            User group = parseObjectToGroup(object);
            final ParseObject adminObj = object.getParseObject(FIELD_ADMIN).fetchIfNeeded();
            group.setAdmin(parseObjectToUser(adminObj));
            preProcessor.process(group);
            notifyCallback(callback, null, group);
        } catch (ParseException e) {
            notifyCallback(callback, prepareErrorReport(e), null);
        }
    }

    @Override
    public void getGroupMembers(@Path(Message.FIELD_ID) final String id, final Callback<List<User>> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doGetGroupMembers(id, response);
            }
        });
    }

    private void doGetGroupMembers(@Path(Message.FIELD_ID) String id, Callback<List<User>> response) {
        try {

            List<String> membersId = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst().getList(FIELD_MEMBERS);
            final List<ParseObject> groupMembers = makeParseQuery(USER_CLASS_NAME).whereContainedIn(FIELD_ID, membersId).find();
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
    //we store user ids and return them so that we do a quick hit test to map
    // the names to our parse objects(we will not persist those names on the backend)
    private void processParseObject(final ParseObject object) {
        ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
            @Override
            public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
                final String userId = object.getString(PARSE_CONSTANTS.FIELD_ID);
                if (contact.numberInIEE_Format.equals(userId)) {
                    object.put(PARSE_CONSTANTS.FIELD_NAME, contact.name);//we will not save this change
                    throw new AbortOperation("done"); //contact manager will stop processing contacts
                }
                return false; //we don't care about return values
            }
        }, null);
    }

    private void processParseObjects(final List<ParseObject> objects) {
        ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
            Set<String> processed = new HashSet<>(objects.size());

            @Override
            public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
                if (processed.size() == objects.size()) {
                    throw new AbortOperation("done");
                }
                if (!processed.add(contact.numberInIEE_Format)) {
                    return false; //we don't care about return values
                }
                for (ParseObject groupMember : objects) {
                    final String userId = groupMember.getString(PARSE_CONSTANTS.FIELD_ID);
                    if (contact.numberInIEE_Format.equals(userId)) {
                        groupMember.put(PARSE_CONSTANTS.FIELD_NAME, contact.name);//we will not save this change
                        break;
                    }
                }
                return false; //we don't care about return values
            }
        }, null);
    }

    @Override
    public void addMembersToGroup(@Path(Message.FIELD_ID) final String id, @Field("by") final String by, @Field(User.FIELD_MEMBERS) final Collection<String> members, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doAddMembersToGroup(id, members, response);
            }
        });
    }

    private void doAddMembersToGroup(@Path(Message.FIELD_ID) String id, @Field(User.FIELD_MEMBERS) Collection<String> members, Callback<HttpResponse> response) {
        if (members.size() <= 0) {
            notifyCallback(response, new Exception("at least one member is required"), new HttpResponse(400, " bad request"));
            return;
        }
        try {
            ParseObject group = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            group.addAllUnique(FIELD_MEMBERS, members);
            group.save();
            notifyCallback(response, null, new HttpResponse(200, "successfully added " + members.size() + " new members"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void removeMembersFromGroup(@Path(Message.FIELD_ID) final String id, @Field("by") final String by, @Field(User.FIELD_MEMBERS) final List<String> members, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doRemoveMembersFromGroup(id, members, response);
            }
        });
    }

    private void doRemoveMembersFromGroup(@Path(Message.FIELD_ID) String id, @Field(User.FIELD_MEMBERS) List<String> members, Callback<HttpResponse> response) {
        if (members.size() > 0) {
            notifyCallback(response, new Exception("at least one member is required"), new HttpResponse(400, " bad request"));
            return;
        }
        try {
            ParseObject group = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            group.removeAll(FIELD_MEMBERS, members);
            group.save();
            notifyCallback(response, null, new HttpResponse(200, "successfully removed " + members.size() + "  members"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void leaveGroup(@Path("id") final String id, @Field("leaver") final String userId, @Field("password") final String password, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doLeaveGroup(id, userId, response);
            }
        });
    }

    private void doLeaveGroup(@Path("id") String id, @Field("leaver") String userId, Callback<HttpResponse> response) {

        try {
            ParseObject group = makeParseQuery(GROUP_CLASS_NAME).whereEqualTo(FIELD_ID, id).getFirst();
            List<String> oneMember = new ArrayList<>(1);
            oneMember.add(userId);
            group.removeAll(FIELD_ID, oneMember);
            group.save();
            notifyCallback(response, null, new HttpResponse(200, "successfully left group"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public void verifyUser(@Path("id") final String userId, @Field("token") final String token, final Callback<SessionData> callback) {
//         if (true) {
//             notifyCallback(callback, null, new SessionData("accessToken", userId));
//             return;
//         }
        TaskManager.execute(new Runnable() {
            public void run() {
                doVerifyUser(userId, token, callback);
            }
        });
    }

    private void doVerifyUser(@Path("id") String userId, @Field("token") String token, Callback<SessionData> callback) {
        try {
            ParseObject object = makeParseQuery(USER_CLASS_NAME).whereEqualTo(FIELD_ID, userId).whereEqualTo(FIELD_TOKEN, token).getFirst();
            object.put(FIELD_VERIFIED, true);
            final String accessToken = ParseInstallation.getCurrentInstallation().getObjectId();
            object.put(PARSE_CONSTANTS.FIELD_ACCESS_TOKEN, accessToken);
            object.save();

            // STOPSHIP: 10/3/2015 get the right key for device token in parse installation
            notifyCallback(callback, null, new SessionData(accessToken, userId));
        } catch (ParseException e) {
            notifyCallback(callback, prepareErrorReport(e), null);
        }
    }

    @Override
    public void resendToken(@Path("id") final String userId, @Field("password") final String password, final Callback<HttpResponse> response) {
        TaskManager.execute(new Runnable() {
            public void run() {
                doResendToken(userId, password, response);
            }
        });
    }

    private void doResendToken(@Path("id") String userId, @Field("password") String password, Callback<HttpResponse> response) {
        synchronized (tokenLock) {
            token = genVerificationToken();
        }
        try {
            ParseObject object = makeParseQuery(USER_CLASS_NAME).whereEqualTo(FIELD_ID, userId).getFirst();
            object.put(FIELD_TOKEN, token);
            object.save();
            notifyCallback(response, null, new HttpResponse(200, "successfully reset token"));
        } catch (ParseException e) {
            notifyCallback(response, prepareErrorReport(e), null);
        }
    }

    @Override
    public HttpResponse resetUnverifiedAccount(@Path("id") String userId) {
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

    @Override
    public void registerUser(@Body JsonObject user, Callback<User> callback) {
        oops("use the registerUser(User,Callback) overload instead");
    }

    @Override
    public void logIn(@Body JsonObject object, Callback<User> callback) {
        oops("use the registerUser(User,Callback) overload instead");
    }

//    public void saveFileToBackend(File file, final FileApi.FileSaveCallback callback, final FileApi.ProgressListener listener) {
//        final ParseFile parseFile;
//        try {
//            final byte[] data = FileUtils.readFileToByteArray(file);
//            parseFile = new ParseFile(file.getName(), data);
//        } catch (IOException e) {
//            callback.done(e, null);
//            return;
//        }
//    }

    private Exception prepareErrorReport(ParseException e) {
        String message;
        if (e.getCode() == ParseException.CONNECTION_FAILED) {
            message = Config.getApplicationContext().getString(R.string.st_unable_to_connect);
        } else {
            message = Config.getApplicationContext().getString(R.string.an_error_occurred);
        }
        return new Exception(message, e.getCause());
    }

    private void ensureFieldsFilled(String... fields) throws RequiredFieldsError {
        for (String field : fields) {
            if (field == null) {
                throw new RequiredFieldsError("some required fields are missing");
            }
        }
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
        user.setHasCall(object.getBoolean(FIELD_HAS_CALL));
        user.setType(User.TYPE_NORMAL_USER);
        return user;
    }

    private final Semaphore dpLock = new Semaphore(1, true);

    private String resolveDp(ParseObject object) {
        String userId = object.getString(FIELD_ID),
                userDp = object.getString(FIELD_DP);
        String pendingDp = userDp;
        PLog.d(TAG, "user dp");
        try {
            dpLock.acquire();

            SharedPreferences preferences = getPendingDpChanges();
            pendingDp = preferences.getString(userId + PENDING_DP, userDp);
            if (!pendingDp.equals(userDp)) {
                try {
                    object.save();
                    preferences.edit().remove(userId + PENDING_DP).apply();
                    PLog.v(TAG, "user with id: " + userId + " changed dp to " + pendingDp);
                } catch (ParseException ignored) {

                }
            }
        } catch (InterruptedException ignore) {
            PLog.d(TAG, "thread: %s interrupted while waiting to acquire semaphore", Thread.currentThread().getId());
            Thread.currentThread().interrupt();
        } finally {
            dpLock.release();
        }
        return pendingDp;
    }

    private SharedPreferences getPendingDpChanges() {
        String prefs = ParseInstallation.getCurrentInstallation().getString(FIELD_ID);
        return Config.getApplicationContext()
                .getSharedPreferences(prefs, Context.MODE_PRIVATE);
    }

    @NonNull
    private User parseObjectToGroup(ParseObject object) throws ParseException {
        PLog.v(TAG, "processing: " + object.toString());
        User user = new User();
        user.setName(object.getString(FIELD_NAME));
        user.setUserId(object.getString(FIELD_ID));
        user.setDP(resolveDp(object));
        final ParseObject parseObject = object.getParseObject(FIELD_ADMIN);
        parseObject.fetchIfNeeded();
        user.setType(User.TYPE_GROUP);
        user.setAdmin(parseObjectToUser(parseObject));
        return user;
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
    private ParseQuery<ParseObject> makeParseQuery() {
        return makeParseQuery(USER_CLASS_NAME);
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
            PLog.d(TAG, "error while deleting message " + e.getMessage(), e.getCause());
            // if(BuildConfig.DEBUG){

            // }
            // throw new RuntimeException(); //we cannot handle this
        }
    }

    //todo send as a mail
    public static void sendFeedBack(JSONObject report) {
        ParseObject object = new ParseObject(PARSE_CONSTANTS.FEEDBACK_CLASS_NAME);
        try {
            Iterator keys = report.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                object.put(key, report.get(key));
            }
        } catch (JSONException | ClassCastException e) {
            throw new RuntimeException(e.getCause());
        }
        object.saveEventually();
    }

    private String token = "";

    @Override
    public synchronized void sendVerificationToken(String userId, Callback<HttpResponse> callback) {
        if (token == null || token.trim().length() == 0) {
            ParseQuery<ParseObject> query = makeParseQuery();
            query.whereEqualTo(PARSE_CONSTANTS.FIELD_ID, userId);
            try {
                ParseObject object = query.getFirst();
                token = object.getString(PARSE_CONSTANTS.FIELD_TOKEN);
                if (token == null || token.length() < 5) {
                    throw new IllegalStateException();
                }
            } catch (ParseException e) {
                notifyCallback(callback, e, new HttpResponse(400, "failed"));
            }
        }
        sendToken(userId, token);
        notifyCallback(callback, null, new HttpResponse(200, "sent token"));
    }

    @Override
    public void removeGroup(String adminId, String groupId, Callback<HttpResponse> callback) {
        ParseQuery<ParseObject> query = makeParseQuery(GROUP_CLASS_NAME);
        Context applicationContext = Config.getApplicationContext();
        try {
            ParseObject object = query.whereEqualTo(FIELD_ID, groupId).getFirst(),
                    admin = object.getParseObject(FIELD_ADMIN).fetchIfNeeded();
            if (!admin.getString(FIELD_ID).equals(adminId)) {
                notifyCallback(callback, new PairappException(applicationContext.getString(R.string.not_permitted_group), "notPermitted"), new HttpResponse(401, "Unauthorised"));
                return;
            }
            object.delete();
            notifyCallback(callback, null, new HttpResponse(200, "sucess"));
        } catch (ParseException e) {
            // TODO: 10/25/2015 handle error
            String message;
            if (e.getCode() == ParseException.CONNECTION_FAILED) {
                message = applicationContext.getString(R.string.st_unable_to_connect);
            } else if (e.getCode() == ParseException.OPERATION_FORBIDDEN) {
                message = applicationContext.getString(R.string.not_permitted_group);
            } else if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                message = applicationContext.getString(R.string.group_not_found);
            } else {
                message = applicationContext.getString(R.string.an_error_occurred);
            }
            notifyCallback(callback, new Exception(message), new HttpResponse(-1, "error occured"));
        }
    }

    private class RequiredFieldsError extends Exception {
        public RequiredFieldsError(String message) {
            super(message);
        }
    }

}
