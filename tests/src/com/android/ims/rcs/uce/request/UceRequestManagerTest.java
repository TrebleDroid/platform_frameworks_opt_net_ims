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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.request.UceRequestManager.UceUtilsProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class UceRequestManagerTest extends ImsTestBase {

    @Mock UceRequest mUceRequest;
    @Mock UceControllerCallback mCallback;
    @Mock Map<Long, UceRequest> mRequestCollection;
    @Mock IRcsUceControllerCallback mCapabilitiesCallback;

    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mUceRequest).when(mRequestCollection).get(anyLong());
        doReturn(mUceRequest).when(mRequestCollection).remove(anyLong());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSendCapabilityRequest() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, false));

        List<Uri> uriList = new ArrayList<>();
        requestManager.sendCapabilityRequest(uriList, false, mCapabilitiesCallback);

        Handler handler = requestManager.getUceRequestHandler();
        waitForHandlerAction(handler, 500L);

        verify(mUceRequest).executeRequest();
    }

    @Test
    @SmallTest
    public void testSendAvailabilityRequest() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, false));

        Uri uri = Uri.fromParts("sip", "test", null);
        requestManager.sendAvailabilityRequest(uri, mCapabilitiesCallback);

        Handler handler = requestManager.getUceRequestHandler();
        waitForHandlerAction(handler, 500L);

        verify(mUceRequest).executeRequest();
    }

    @Test
    @SmallTest
    public void testRequestDestroyed() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, true));

        requestManager.onDestroy();

        List<Uri> uriList = new ArrayList<>();
        requestManager.sendCapabilityRequest(uriList, false, mCapabilitiesCallback);

        Handler handler = requestManager.getUceRequestHandler();
        waitForHandlerAction(handler, 500L);

        verify(mUceRequest, never()).executeRequest();
        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_GENERIC_FAILURE, 0L);
    }

    @Test
    @SmallTest
    public void testCapabilitiesAccessFromCache() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, true));
        RequestManagerCallback requestMgrCallback = requestManager.getRequestManagerCallback();

        Uri contact = Uri.fromParts("sip", "test", null);
        List<Uri> uriList = new ArrayList<>();
        uriList.add(contact);

        requestMgrCallback.getCapabilitiesFromCache(uriList);
        verify(mCallback).getCapabilitiesFromCache(uriList);

        requestMgrCallback.getAvailabilityFromCache(contact);
        verify(mCallback).getAvailabilityFromCache(contact);

        List<RcsContactUceCapability> capabilityList = new ArrayList<>();
        requestMgrCallback.saveCapabilities(capabilityList);
        verify(mCallback).saveCapabilities(capabilityList);
    }

    @Test
    @SmallTest
    public void testRequestUpdate() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, true));
        Handler handler = requestManager.getUceRequestHandler();
        RequestManagerCallback requestMgrCallback = requestManager.getRequestManagerCallback();

        requestMgrCallback.onRequestFinished(1L);
        waitForHandlerAction(handler, 500L);
        verify(mUceRequest, times(1)).onFinish();

        requestMgrCallback.onRequestFailed(1L);
        waitForHandlerAction(handler, 500L);
        verify(mUceRequest).handleRequestFailed(false);
        verify(mUceRequest, times(2)).onFinish();

        requestMgrCallback.onRequestSuccess(1L);
        waitForHandlerAction(handler, 500L);
        verify(mUceRequest).handleRequestCompleted(false);
        verify(mUceRequest, times(3)).onFinish();

        requestMgrCallback.onResourceTerminated(1L);
        waitForHandlerAction(handler, 500L);
        verify(mUceRequest).handleResourceTerminated();

        requestMgrCallback.onCapabilityUpdate(1L);
        waitForHandlerAction(handler, 500L);
        verify(mUceRequest).handleCapabilitiesUpdated();
    }

    @Test
    @SmallTest
    public void testRequestForbidden() throws Exception {
        UceRequestManager requestManager = getUceRequestManager();
        requestManager.setsUceUtilsProxy(getUceUtilsProxy(true, true, true));
        RequestManagerCallback requestMgrCallback = requestManager.getRequestManagerCallback();

        requestMgrCallback.isRequestForbidden();
        verify(mCallback).isRequestForbiddenByNetwork();

        requestMgrCallback.getRetryAfterMillis();
        verify(mCallback).getRetryAfterMillis();

        boolean isForbidden = true;
        long retryAfter = 3000L;
        Integer errorCode = RcsUceAdapter.ERROR_NOT_AUTHORIZED;
        requestMgrCallback.onRequestForbidden(isForbidden, errorCode, retryAfter);
        verify(mCallback).updateRequestForbidden(isForbidden, errorCode, retryAfter);
    }

    private UceRequestManager getUceRequestManager() {
        UceRequestManager manager = new UceRequestManager(mContext, mSubId, Looper.getMainLooper(),
                mCallback, mRequestCollection);
        return manager;
    }

    private UceUtilsProxy getUceUtilsProxy(boolean presenceCapEnabled, boolean supportPresence,
            boolean supportOptions) {
        return new UceUtilsProxy() {
            @Override
            public boolean isPresenceCapExchangeEnabled(Context context, int subId) {
                return presenceCapEnabled;
            }

            @Override
            public boolean isPresenceSupported(Context context, int subId) {
                return supportPresence;
            }

            @Override
            public boolean isSipOptionsSupported(Context context, int subId) {
                return supportOptions;
            }
        };
    }


}
