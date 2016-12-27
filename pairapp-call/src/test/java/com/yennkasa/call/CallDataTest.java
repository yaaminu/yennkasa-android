package com.yennkasa.call;

import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallDetails;
import com.sinch.android.rtc.calling.CallDirection;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.calling.CallState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author aminu on 7/15/2016.
 */
public class CallDataTest {

    @Test
    public void testFrom() throws Exception {
        Call mock = mock(Call.class);
        CallDetails details = mock(CallDetails.class);


        when(details.getEndCause()).thenReturn(CallEndCause.CANCELED);
        when(details.getEndedTime()).thenReturn(10000L);
        when(details.getError()).thenReturn(null);
        when(details.getStartedTime()).thenReturn(1000L);
        when(details.getEstablishedTime()).thenReturn(1500L);

        when(mock.getCallId()).thenReturn("callId");
        when(mock.getDirection()).thenReturn(CallDirection.OUTGOING);
        when(mock.getRemoteUserId()).thenReturn("userId");
        when(mock.getDetails()).thenReturn(details);
        when(mock.getState()).thenReturn(CallState.ESTABLISHED);

        CallData data = CallData.from(mock, CallController.CALL_TYPE_VOICE);
        assertEquals(details.getStartedTime(), data.getCallDate());
        assertEquals(mock.getCallId(), data.getCallId());
        assertEquals(CallController.CALL_TYPE_VOICE, data.getCallType());
        assertEquals(mock.getRemoteUserId(), data.getPeer());
        assertTrue(data.isOutGoing());
        assertEquals(details.getEstablishedTime(), data.getEstablishedTime());
        assertEquals(CallData.ESTABLISHED, data.getCallState());

        when(mock.getState()).thenReturn(CallState.ESTABLISHED);
        data = CallData.from(mock, CallController.CALL_TYPE_VOICE);
        assertEquals(CallData.ESTABLISHED, data.getCallState());

        when(mock.getState()).thenReturn(CallState.INITIATING);
        data = CallData.from(mock, CallController.CALL_TYPE_VOICE);
        assertEquals(CallData.INITIATING, data.getCallState());

        when(mock.getState()).thenReturn(CallState.TRANSFERRING);
        data = CallData.from(mock, CallController.CALL_TYPE_VOICE);
        assertEquals(CallData.TRANSFERRING, data.getCallState());

        when(mock.getState()).thenReturn(CallState.ENDED);
        data = CallData.from(mock, CallController.CALL_TYPE_VOICE);
        assertEquals(CallData.ENDED, data.getCallState());

    }
}
