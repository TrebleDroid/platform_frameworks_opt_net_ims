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

import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_CACHED;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_NETWORK;

import static com.android.ims.rcs.uce.eab.EabCapabilityResult.EAB_QUERY_SUCCESSFUL;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class UceRequestTest extends ImsTestBase {

    @Mock IRcsUceControllerCallback mCallback;
    @Mock RequestManagerCallback mRequestMgrCallback;
    @Mock CapabilityRequestResponse mRequestResponse;

    private int mSubId = 1;
    private boolean mRequestExecuted;

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
    public void testCachedCapabilityCallback() throws Exception {
        UceRequest request = getUceRequest();

        Uri contact1 = Uri.fromParts("sip", "test1", null);
        Uri contact2 = Uri.fromParts("sip", "test2", null);
        RcsContactUceCapability.PresenceBuilder builder1 =
                new RcsContactUceCapability.PresenceBuilder(contact1,
                        SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND);
        RcsContactUceCapability.PresenceBuilder builder2 =
                new RcsContactUceCapability.PresenceBuilder(contact2,
                        SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND);

        List<EabCapabilityResult> eabResultList = new ArrayList<>();
        EabCapabilityResult eabResult1 =
                new EabCapabilityResult(contact1, EAB_QUERY_SUCCESSFUL, builder1.build());
        EabCapabilityResult eabResult2 =
                new EabCapabilityResult(contact2, EAB_QUERY_SUCCESSFUL, builder2.build());
        eabResultList.add(eabResult1);
        eabResultList.add(eabResult2);

        List<RcsContactUceCapability> capabilityList = new ArrayList<>();
        capabilityList.add(builder1.build());
        capabilityList.add(builder2.build());

        doReturn(eabResultList).when(mRequestMgrCallback).getCapabilitiesFromCache(any());
        doReturn(true).when(mRequestResponse).triggerCachedCapabilitiesCallback(capabilityList);

        request.executeRequest();

        verify(mRequestResponse).triggerCachedCapabilitiesCallback(capabilityList);
        verify(mRequestResponse).triggerCompletedCallback();
        verify(mRequestMgrCallback).onRequestFinished(anyLong());
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        UceRequest request = getUceRequest();

        Uri contact = Uri.fromParts("sip", "test", null);
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(contact,
                        SOURCE_TYPE_CACHED, REQUEST_RESULT_FOUND);

        List<EabCapabilityResult> eabResultList = new ArrayList<>();
        EabCapabilityResult eabResult =
                new EabCapabilityResult(contact, EAB_QUERY_SUCCESSFUL, builder.build());
        eabResultList.add(eabResult);

        List<RcsContactUceCapability> capabilityList = new ArrayList<>();
        capabilityList.add(builder.build());
        doReturn(eabResultList).when(mRequestMgrCallback).getCapabilitiesFromCache(any());
        doReturn(true).when(mRequestResponse).triggerCachedCapabilitiesCallback(capabilityList);

        request.executeRequest();

        verify(mRequestResponse).triggerCachedCapabilitiesCallback(capabilityList);
        assertTrue(mRequestExecuted);
    }

    @Test
    @SmallTest
    public void testRequestFinish() throws Exception {
        UceRequest request = getUceRequest();
        request.onFinish();

        request.executeRequest();

        verify(mRequestResponse).triggerErrorCallback();
        verify(mRequestMgrCallback).onRequestFinished(anyLong());
    }

    @Test
    @SmallTest
    public void testHandleCapabilitiesUpdated() throws Exception {
        UceRequest request = getUceRequest();
        List<RcsContactUceCapability> capList = getRcsContactUceCapabilityList();
        doReturn(capList).when(mRequestResponse).getUpdatedContactCapability();

        request.handleCapabilitiesUpdated();

        verify(mRequestMgrCallback).saveCapabilities(capList);
        verify(mRequestResponse).triggerCapabilitiesCallback(capList);
    }

    @Test
    @SmallTest
    public void testHandleResourceTerminated() throws Exception {
        UceRequest request = getUceRequest();
        List<RcsContactUceCapability> capList = getRcsContactUceCapabilityList();
        doReturn(capList).when(mRequestResponse).getTerminatedResources();

        request.handleResourceTerminated();

        verify(mRequestMgrCallback).saveCapabilities(capList);
        verify(mRequestResponse).triggerResourceTerminatedCallback(capList);
    }

    @Test
    @SmallTest
    public void testHandleRequestFailed() throws Exception {
        UceRequest request = getUceRequest();

        request.handleRequestFailed(true);

        verify(mRequestResponse).triggerErrorCallback();
        verify(mRequestMgrCallback).onRequestFinished(anyLong());
    }

    @Test
    @SmallTest
    public void testHandleRequestCompleted() throws Exception {
        UceRequest request = getUceRequest();

        request.handleRequestCompleted(true);

        verify(mRequestResponse).triggerCompletedCallback();
        verify(mRequestMgrCallback).onRequestFinished(anyLong());
    }

    private UceRequest getUceRequest() {
        UceRequest request = new UceRequest(mSubId, UceRequest.REQUEST_TYPE_CAPABILITY,
                mRequestMgrCallback, mRequestResponse) {
            @Override
            protected void requestCapabilities(List<Uri> requestCapUris) {
                mRequestExecuted = true;
            }
        };

        Uri contact1 = Uri.fromParts("sip", "test1", null);
        Uri contact2 = Uri.fromParts("sip", "test2", null);
        List<Uri> uriList = new ArrayList<>();
        uriList.add(contact1);
        uriList.add(contact2);
        request.setContactUri(uriList);
        request.setCapabilitiesCallback(mCallback);
        return request;
    }

    private List<RcsContactUceCapability> getRcsContactUceCapabilityList() {
        List<RcsContactUceCapability> updatedCapabilities = new ArrayList<>();
        Uri contact = Uri.fromParts("sip", "test", null);
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(contact,
                        SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND);
        updatedCapabilities.add(builder.build());
        return updatedCapabilities;
    }
}
