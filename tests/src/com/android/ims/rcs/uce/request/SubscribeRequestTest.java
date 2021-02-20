/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ims.rcs.uce.request;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.ISubscribeResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class SubscribeRequestTest extends ImsTestBase {

    @Mock SubscribeController mSubscribeController;
    @Mock CapabilityRequestResponse mRequestResponse;
    @Mock RequestManagerCallback mRequestManagerCallback;

    private int mSubId = 1;
    private int mRequestType = UceRequest.REQUEST_TYPE_CAPABILITY;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();

        List<Uri> uriList = new ArrayList<>();
        subscribeRequest.requestCapabilities(uriList);

        verify(mSubscribeController).requestCapabilities(eq(uriList), any());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWhenDestroy() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        subscribeRequest.onFinish();

        List<Uri> uriList = new ArrayList<>();
        subscribeRequest.requestCapabilities(uriList);

        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestResponse).triggerErrorCallback();
        verify(mRequestManagerCallback).onRequestFinished(anyLong());
        verify(mSubscribeController, never()).requestCapabilities(any(), any());
    }

    @Test
    @SmallTest
    public void testCommandErrorCallback() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        callback.onCommandError(RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED);

        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestManagerCallback).onRequestFailed(anyLong());
    }

    @Test
    @SmallTest
    public void testNetworkResponseWithSuccess() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        doReturn(true).when(mRequestResponse).isNetworkResponseOK();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        // Respond the SIP CODE 200 OK
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_OK, NetworkSipCode.SIP_OK);

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_OK,
                NetworkSipCode.SIP_OK);
        verify(mRequestResponse, never()).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback, never()).onRequestFailed(anyLong());

        // Respond the SIP CODE 202 ACCEPTED
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_ACCEPTED, NetworkSipCode.SIP_ACCEPTED);

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_ACCEPTED,
                NetworkSipCode.SIP_ACCEPTED);
        verify(mRequestResponse, never()).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback, never()).onRequestFailed(anyLong());
    }

    @Test
    @SmallTest
    public void testNetworkResponseWithNotSuccess() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        doReturn(RcsUceAdapter.ERROR_FORBIDDEN).when(mRequestResponse)
                .getCapabilityErrorFromSipError();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_FORBIDDEN, "");

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_FORBIDDEN, "");
        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback).onRequestFailed(anyLong());
    }

    @Test
    @SmallTest
    public void testResourceTerminated() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        Uri contact = Uri.fromParts("sip", "test", null);
        List<RcsContactTerminatedReason> list = new ArrayList<>();
        list.add(new RcsContactTerminatedReason(contact, "terminated"));
        callback.onResourceTerminated(list);

        verify(mRequestResponse).addTerminatedResource(any());
        verify(mRequestManagerCallback).onResourceTerminated(anyLong());
    }

    @Test
    @SmallTest
    public void testCapabilitiesUpdate() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        List<String> pidfXml = new ArrayList<>();
        pidfXml.add(getPidfData());
        callback.onNotifyCapabilitiesUpdate(pidfXml);

        verify(mRequestResponse).addUpdatedCapabilities(any());
        verify(mRequestManagerCallback).onCapabilityUpdate(anyLong());
    }

    @Test
    @SmallTest
    public void testTerminatedCallback() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        doReturn(true).when(mRequestResponse).isNetworkResponseOK();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        // Respond SIP CODE 200 OK
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_OK, NetworkSipCode.SIP_OK);
        callback.onTerminated(null, 0L);

        verify(mRequestManagerCallback).onRequestSuccess(anyLong());

        // Respond SIP CODE 202 ACCEPTED
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_ACCEPTED, NetworkSipCode.SIP_ACCEPTED);
        callback.onTerminated(null, 0L);

        verify(mRequestManagerCallback, times(2)).onRequestSuccess(anyLong());
    }

    @Test
    @SmallTest
    public void testTerminatedCallbackWithRetry() throws Exception {
        SubscribeRequest subscribeRequest = getSubscribeRequest();
        doReturn(RcsUceAdapter.ERROR_GENERIC_FAILURE).when(mRequestResponse)
                .getCapabilityErrorFromSipError();
        ISubscribeResponseCallback callback = subscribeRequest.getResponseCallback();

        String reason = "Server is busy";
        long retryAfterMilliseconds = 10000L;
        callback.onTerminated(reason, retryAfterMilliseconds);

        verify(mRequestResponse).setRequestTerminated(reason, retryAfterMilliseconds);
        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestManagerCallback).onRequestFailed(anyLong());
    }

    private SubscribeRequest getSubscribeRequest() {
        return new SubscribeRequest(mSubId, mRequestType, mRequestManagerCallback,
                mSubscribeController, mRequestResponse);
    }

    private String getPidfData() {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"sip:test\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                // tuple 1
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // support video
                .append("<caps:video>true</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("</tuple>");

        return pidfBuilder.toString();
    }
}
