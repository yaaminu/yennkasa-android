package sockets;

import com.github.nkzawa.emitter.Emitter;
import com.pairapp.net.sockets.SocketIoClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author by Null-Pointer on 9/7/2015.
 */
public class SocketIoClientTest {

    public static final String END_POINT = "http://localhost:3000/live";
    private static final Emitter.Listener DUMMY_LLISTENER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            System.out.println("listener received an event");
            StringBuilder builder = new StringBuilder();
            for (Object arg : args) {
                builder.append(arg.toString());
            }
            System.out.println("data received: " + builder.toString());
        }
    };
    private SocketIoClient client;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    // @Test
    // public void testGetInstance() throws Exception {
    //     // try {
    //     //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     //     Assert.assertNotNull(client);
    //     //     //check that getInstance() attempts to create a new instance  after a call to close
    //     //     client.close();
    //     //     client = SocketIoClient.getInstance("", "user");
    //     //     fail("must throw");
    //     //     try {
    //     //         client = SocketIoClient.getInstance("", "ss");
    //     //         client.close();
    //     //     } catch (Exception e) {
    //     //         fail("getInstance() must not create new instance until reference count is 0");
    //     //     }
    //     // } catch (Exception e) {
    //     //     assertEquals(IllegalArgumentException.class, e.getClass());
    //     // }
    // }

    // @Test
    // public void testRegisterForEvent() throws Exception {
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     assertTrue(client.registerForEvent("dummy", DUMMY_LLISTENER));
    //     client.close();
    //     assertFalse("client must not register if its not yet ready", client.registerForEvent("dummy", DUMMY_LLISTENER));
    // }

    // @Test
    // public void testRegisterForEventOnce() throws Exception {
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     assertTrue(client.registerForEventOnce("dummy", DUMMY_LLISTENER));
    //     client.close();
    //     assertFalse("client must not register if its not yet ready", client.registerForEventOnce("dummy", DUMMY_LLISTENER));
    // }

    // @Test
    // public void testBroadcast() throws Exception {
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     assertTrue(client.registerForEventOnce("typing", DUMMY_LLISTENER));
    //     client.send("typing", "\"userId\":\"userId\"");
    //     client.close();
    // }

    // @Test
    // public void testSend() throws Exception {
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     assertTrue(client.registerForEventOnce("typing", DUMMY_LLISTENER));
    //     client.send("\"to\":\"userId\"");
    //     client.close();
    // }

    // @Test
    // public void testClose() throws Exception {
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     client.close();
    //     assertFalse(client.registerForEvent("foo", DUMMY_LLISTENER));
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     client = SocketIoClient.getInstance(END_POINT, "userId");
    //     client.close();
    //     assertTrue(client.registerForEvent("foo", DUMMY_LLISTENER));
    //     client.close();
    //     assertFalse(client.registerForEvent("foo", DUMMY_LLISTENER));
    // }
}
