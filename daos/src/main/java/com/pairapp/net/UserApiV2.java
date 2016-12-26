package com.pairapp.net;

import android.support.v4.util.Pair;

import com.pairapp.Errors.PairappException;
import com.pairapp.data.User;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * by Null-Pointer on 5/27/2015.
 */
public interface UserApiV2 {

    String getAuthToken();

    void sendVerificationToken(String userId, Callback<HttpResponse> callback);

    void registerUser(User user, Callback<User> callback);

    void logIn(User object, Callback<User> callback);

    void getUser(String id, Callback<User> response);

    void getGroups(String id, Callback<List<User>> response);

    void changeDp(String userOrGroup, String id, File file, Callback<HttpResponse> response);

    void createGroup(String by, String name, Collection<String> members, Callback<User> response);

    void getGroup(String id, Callback<User> group);

    void getGroupMembers(String id, Callback<List<User>> response);

    void addMembersToGroup(String id
            , String by
            , Collection<String> members
            , Callback<HttpResponse> response);

    void removeMembersFromGroup(String id
            , String by
            , List<String> members
            , Callback<HttpResponse> response);

    // FIXME: 7/19/2015 change this when our backend start using sessions
    void leaveGroup(String id, String userId, String password, Callback<HttpResponse> response);


    void verifyUser(String userId, String token, String pushID, String publicKey, Callback<SessionData> callback);

    void getPublicKeyForUser(String userId, Callback<String> callback);

    String getPublicKeyForUserSync(String userId);

    void resendToken(String userId, String password, Callback<HttpResponse> response);

    HttpResponse resetUnverifiedAccount(String userId);

    HttpResponse requestPasswordReset(String number);


    boolean isUserAuthenticated();


    String newAuthToken() throws PairappException;

    void updatePushID(String newPushID) throws PairappException;


    Pair<String, Long> getSinchToken();

    void search(String query, Callback<List<User>> callback);

    void enableSearch(boolean enableSearch, Callback<Boolean> callback);

    interface Callback<T> {
        void done(Exception e, T t);
    }

    interface Preprocessor {
        void process(User user);

        void process(Collection<User> users);
    }

    class SessionData {
        public final String accessToken, userId;

        public SessionData(String accessToken, String ownerId) {
            this.accessToken = accessToken;
            this.userId = ownerId;
        }
    }
}
