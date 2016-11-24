package com.pairapp.util;

import android.content.Context;

import com.pairapp.data.User;
import com.pairapp.data.UserManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/25/2015.
 */
public class UserManagerTest {

    Context context;
    Realm realm;

    @Before
    public void setUp() throws Exception {
        context = Mockito.mock(Context.class);
        Mockito.when(context.getFilesDir()).thenReturn(new File("C:\\Users\\Null-Pointer\\Desktop\\realm"));
        realm = Realm.getInstance(context);
    }

    @After
    public void tearDown() throws Exception {
        realm.close();
    }

    @Test
    public void testGetMainUser() throws Exception {
        User user = UserManager.getInstance().getCurrentUser(realm);
        Assert.assertNull(user);

    }

    @Test
    public void testIsMainUser() throws Exception {

    }

    @Test
    public void testRefreshUserDetails() throws Exception {

    }

    @Test
    public void testChangeDp() throws Exception {

    }

    @Test
    public void testGetUserDp() throws Exception {

    }

    @Test
    public void testCLogIn() throws Exception {

    }

    @Test
    public void testSignUp() throws Exception {

    }

    @Test
    public void testCLogOut() throws Exception {

    }

    @Test
    public void testFetchFriends() throws Exception {

    }
}
