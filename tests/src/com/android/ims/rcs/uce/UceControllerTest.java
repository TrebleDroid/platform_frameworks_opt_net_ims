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

package com.android.ims.rcs.uce;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.fail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RcsUceAdapter.CapabilitiesCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.CapabilityExchangeListenerAdapter.OptionsRequestCallback;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.eab.EabController;
import com.android.ims.rcs.uce.options.OptionsController;
import com.android.ims.rcs.uce.presence.publish.PublishController;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class UceControllerTest extends ImsTestBase {

    @Mock EabController mEabController;
    @Mock PublishController mPublishController;
    @Mock SubscribeController mSubscribeController;
    @Mock OptionsController mOptionsController;
    @Mock UceController.ControllerFactory mControllerFactory;

    @Mock UceRequestTaskManager mTaskManager;
    @Mock UceController.RequestTaskManagerFactory mTaskManagerFactory;

    @Mock RcsFeatureManager mFeatureManager;
    @Mock UceController.UceControllerCallback mCallback;
    @Mock CapabilitiesCallback mCapabilitiesCallback;
    @Mock OptionsRequestCallback mOptionsRequestCallback;

    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mEabController).when(mControllerFactory).createEabController(any(), eq(mSubId),
                any(), any());
        doReturn(mPublishController).when(mControllerFactory).createPublishController(any(),
                eq(mSubId), any(), any());
        doReturn(mSubscribeController).when(mControllerFactory).createSubscribeController(any(),
                eq(mSubId), any(), any());
        doReturn(mOptionsController).when(mControllerFactory).createOptionsController(any(),
                eq(mSubId), any(), any());
        doReturn(mTaskManager).when(mTaskManagerFactory).createTaskManager(any(), eq(mSubId), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRcsConnected() throws Exception {
        UceController uceController = createUceController();

        uceController.onRcsConnected(mFeatureManager);

        verify(mEabController).onRcsConnected(mFeatureManager);
        verify(mPublishController).onRcsConnected(mFeatureManager);
        verify(mSubscribeController).onRcsConnected(mFeatureManager);
        verify(mOptionsController).onRcsConnected(mFeatureManager);
    }

    @Test
    @SmallTest
    public void testRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);

        uceController.onRcsDisconnected();

        verify(mEabController).onRcsDisconnected();
        verify(mPublishController).onRcsDisconnected();
        verify(mSubscribeController).onRcsDisconnected();
        verify(mOptionsController).onRcsDisconnected();
    }

    @Test
    @SmallTest
    public void testOnDestroyed() throws Exception {
        UceController uceController = createUceController();

        uceController.onDestroy();

        verify(mTaskManager).onDestroy();
        verify(mEabController).onDestroy();
        verify(mPublishController).onDestroy();
        verify(mSubscribeController).onDestroy();
        verify(mOptionsController).onDestroy();
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWithRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsDisconnected();

        List<Uri> uriList = new ArrayList<>();
        uceController.requestCapabilities(uriList, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mTaskManager, never()).triggerCapabilityRequestTask(any(), any(), any());
    }

    @Test
    @SmallTest
    public void testRequestCapabilitiesWithRcsConnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);

        List<Uri> uriList = new ArrayList<>();
        uceController.requestCapabilities(uriList, mCapabilitiesCallback);

        verify(mTaskManager).triggerCapabilityRequestTask(mCallback, uriList,
                mCapabilitiesCallback);
    }

    @Test
    @SmallTest
    public void testRequestAvailabilityWithRcsDisconnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsDisconnected();

        Uri contact = Uri.fromParts("sip", "test", null);
        uceController.requestAvailability(contact, mCapabilitiesCallback);

        verify(mCapabilitiesCallback).onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
        verify(mTaskManager, never()).triggerAvailabilityRequestTask(any(), any(), any());
    }

    @Test
    @SmallTest
    public void testRequestAvailabilityWithRcsConnected() throws Exception {
        UceController uceController = createUceController();
        uceController.onRcsConnected(mFeatureManager);

        Uri contact = Uri.fromParts("sip", "test", null);
        uceController.requestAvailability(contact, mCapabilitiesCallback);

        verify(mTaskManager).triggerAvailabilityRequestTask(mCallback, contact,
                mCapabilitiesCallback);
    }

    @Test
    @SmallTest
    public void TestRequestPublishCapabilitiesFromService() throws Exception {
        UceController uceController = createUceController();

        uceController.onRequestPublishCapabilitiesFromService(anyInt());

        verify(mPublishController).publishCapabilities(anyInt());
    }

    @Test
    @SmallTest
    public void testUnpublish() throws Exception {
        UceController uceController = createUceController();

        uceController.onUnpublish();

        verify(mPublishController).onUnpublish();
    }

    @Test
    @SmallTest
    public void testRegisterPublishStateCallback() {
        UceController uceController = createUceController();

        uceController.registerPublishStateCallback(any());

        verify(mPublishController).registerPublishStateCallback(any());
    }

    @Test
    @SmallTest
    public void unregisterPublishStateCallback() {
        UceController uceController = createUceController();

        uceController.unregisterPublishStateCallback(any());

        verify(mPublishController).unregisterPublishStateCallback(any());
    }

    @Test
    @SmallTest
    public void testGetUcePublishState() {
        UceController uceController = createUceController();

        uceController.getUcePublishState();

        verify(mPublishController).getUcePublishState();
    }

    @Test
    @SmallTest
    public void testRetrieveOptionsCapabilities() {
        UceController uceController = createUceController();

        Uri contact = Uri.fromParts("sip", "test", null);
        List<String> capabilities = new ArrayList<>();
        uceController.retrieveOptionsCapabilitiesForRemote(contact, capabilities,
                mOptionsRequestCallback);

        verify(mOptionsController).retrieveCapabilitiesForRemote(contact, capabilities,
                mOptionsRequestCallback);
    }

    private UceController createUceController() {
        UceController uceController = new UceController(mContext, mSubId, mControllerFactory,
                mTaskManagerFactory);
        uceController.setUceControllerCallback(mCallback);
        return uceController;
    }
}
