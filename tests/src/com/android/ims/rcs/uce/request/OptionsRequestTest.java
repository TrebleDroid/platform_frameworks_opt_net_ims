/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IOptionsResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.options.OptionsController;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class OptionsRequestTest extends ImsTestBase {

    private static final String FEATURE_TAG_CHAT =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String FEATURE_TAG_FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";
    private static final String FEATURE_TAG_MMTEL_AUDIO_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";
    private static final String FEATURE_TAG_MMTEL_VIDEO_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\";video";

    private int mSubId = 1;
    private int mRequestType = UceRequest.REQUEST_TYPE_CAPABILITY;
    private Uri mTestContact;
    private Set<String> mFeatureTags;
    private RcsContactUceCapability mDeviceCapability;

    @Mock OptionsController mOptionsController;
    @Mock CapabilityRequestResponse mRequestResponse;
    @Mock RequestManagerCallback mRequestManagerCallback;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestContact = Uri.fromParts("sip", "test", null);

        mFeatureTags = new HashSet<>();
        mFeatureTags.add(FEATURE_TAG_CHAT);
        mFeatureTags.add(FEATURE_TAG_FILE_TRANSFER);
        mFeatureTags.add(FEATURE_TAG_MMTEL_AUDIO_CALL);
        mFeatureTags.add(FEATURE_TAG_MMTEL_VIDEO_CALL);

        OptionsBuilder builder = new OptionsBuilder(mTestContact);
        builder.addFeatureTag(FEATURE_TAG_CHAT);
        builder.addFeatureTag(FEATURE_TAG_FILE_TRANSFER);
        builder.addFeatureTag(FEATURE_TAG_MMTEL_AUDIO_CALL);
        builder.addFeatureTag(FEATURE_TAG_MMTEL_VIDEO_CALL);
        mDeviceCapability = builder.build();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRequestCapabilities() throws Exception {
        OptionsRequest request = getOptionsRequest();
        doReturn(mDeviceCapability).when(mRequestManagerCallback).getDeviceCapabilities(anyInt());

        List<Uri> uriList = Collections.singletonList(mTestContact);
        request.requestCapabilities(uriList);

        verify(mOptionsController).sendCapabilitiesRequest(eq(mTestContact),
                eq(mFeatureTags), any());
    }

    @Test
    @SmallTest
    public void testsendCapabilitiesRequestWhenDestroy() throws Exception {
        OptionsRequest request = getOptionsRequest();
        request.onFinish();

        List<Uri> uriList = Collections.singletonList(mTestContact);
        request.requestCapabilities(uriList);

        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestResponse).triggerErrorCallback();
        verify(mRequestManagerCallback).onRequestFinished(anyLong());
        verify(mOptionsController, never()).sendCapabilitiesRequest(any(), any(), any());
    }

    @Test
    @SmallTest
    public void testCommandErrorCallback() throws Exception {
        OptionsRequest request = getOptionsRequest();
        IOptionsResponseCallback callback = request.getResponseCallback();

        callback.onCommandError(RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED);

        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mRequestManagerCallback).onRequestFailed(anyLong());
    }

    @Test
    @SmallTest
    public void testNetworkResponseWithSuccess() throws Exception {
        OptionsRequest request = getOptionsRequest();
        doReturn(true).when(mRequestResponse).isNetworkResponseOK();
        IOptionsResponseCallback callback = request.getResponseCallback();

        // Respond the SIP CODE 200 OK
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_OK, NetworkSipCode.SIP_OK,
                new ArrayList<>(mFeatureTags));

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_OK,
                NetworkSipCode.SIP_OK);
        verify(mRequestResponse).setRemoteCapabilities(any(), eq(mFeatureTags));
        verify(mRequestResponse, never()).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback, never()).onRequestFailed(anyLong());

        // Respond the SIP CODE 202 ACCEPTED
        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_ACCEPTED, NetworkSipCode.SIP_ACCEPTED,
                new ArrayList<>(mFeatureTags));

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_ACCEPTED,
                NetworkSipCode.SIP_ACCEPTED);
        verify(mRequestResponse, times(2)).setRemoteCapabilities(any(), eq(mFeatureTags));
        verify(mRequestResponse, never()).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback, never()).onRequestFailed(anyLong());
    }

    @Test
    @SmallTest
    public void testNetworkResponseWithNotSuccess() throws Exception {
        OptionsRequest request = getOptionsRequest();
        doReturn(RcsUceAdapter.ERROR_FORBIDDEN).when(mRequestResponse)
                .getCapabilityErrorFromSipError();
        IOptionsResponseCallback callback = request.getResponseCallback();

        callback.onNetworkResponse(NetworkSipCode.SIP_CODE_FORBIDDEN, "", Collections.EMPTY_LIST);

        verify(mRequestResponse).setNetworkResponseCode(NetworkSipCode.SIP_CODE_FORBIDDEN, "");
        verify(mRequestResponse).setRemoteCapabilities(any(), eq(Collections.EMPTY_SET));
        verify(mRequestResponse).setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
        verify(mRequestManagerCallback).onRequestFailed(anyLong());
    }

    private OptionsRequest getOptionsRequest() {
        return new OptionsRequest(mSubId, mRequestType, mRequestManagerCallback,
                mOptionsController, mRequestResponse);
    }
}
