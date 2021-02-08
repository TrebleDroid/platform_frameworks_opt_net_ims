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

package com.android.ims.rcs.uce.presence.publish;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController;
import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl.DeviceCapListenerFactory;
import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl.PublishProcessorFactory;
import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class PublishControllerImplTest extends ImsTestBase {

    @Mock RcsFeatureManager mFeatureManager;
    @Mock PublishProcessor mPublishProcessor;
    @Mock PublishProcessorFactory mPublishProcessorFactory;
    @Mock DeviceCapabilityListener mDeviceCapListener;
    @Mock DeviceCapListenerFactory mDeviceCapListenerFactory;
    @Mock UceController.UceControllerCallback mUceCtrlCallback;
    @Mock RemoteCallbackList<IRcsUcePublishStateCallback> mPublishStateCallbacks;

    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mPublishProcessor).when(mPublishProcessorFactory).createPublishProcessor(any(),
                eq(mSubId), any(), any());
        doReturn(mDeviceCapListener).when(mDeviceCapListenerFactory).createDeviceCapListener(any(),
                eq(mSubId), any(), any(), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testRcsConnected() throws Exception {
        PublishController publishController = createPublishController();

        publishController.onRcsConnected(mFeatureManager);

        verify(mPublishProcessor).onRcsConnected(mFeatureManager);
    }

    @Test
    @SmallTest
    public void testRcsDisconnected() throws Exception {
        PublishController publishController = createPublishController();

        publishController.onRcsDisconnected();

        verify(mPublishProcessor).onRcsDisconnected();
    }

    @Test
    @SmallTest
    public void testDestroyed() throws Exception {
        PublishController publishController = createPublishController();

        publishController.onDestroy();

        verify(mPublishProcessor).onDestroy();
        verify(mDeviceCapListener).onDestroy();
    }

    @Test
    @SmallTest
    public void testGetPublishState() throws Exception {
        PublishController publishController = createPublishController();

        int initState = publishController.getUcePublishState();
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, initState);

        publishController.onDestroy();

        int destroyedState = publishController.getUcePublishState();
        assertEquals(RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR, destroyedState);
    }

    @Test
    @SmallTest
    public void testRegisterPublishStateCallback() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.registerPublishStateCallback(any());

        verify(mPublishStateCallbacks).register(any());
    }

    @Test
    @SmallTest
    public void unregisterPublishStateCallback() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.unregisterPublishStateCallback(any());

        verify(mPublishStateCallbacks).unregister(any());
    }

    @Test
    @SmallTest
    public void testUnpublish() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.onUnpublish();

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        int publishState = publishController.getUcePublishState();
        assertEquals(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED, publishState);
    }

    @Test
    @SmallTest
    public void testRequestPublishFromService() throws Exception {
        PublishControllerImpl publishController = createPublishController();

        publishController.requestPublishCapabilitiesFromService(
                RcsUceAdapter.CAPABILITY_UPDATE_TRIGGER_MOVE_TO_IWLAN);

        Handler handler = publishController.getPublishHandler();
        waitForHandlerAction(handler, 1000);
        verify(mPublishProcessor, never()).doPublish(PublishController.PUBLISH_TRIGGER_SERVICE);

        IImsCapabilityCallback callback = publishController.getRcsCapabilitiesCallback();
        callback.onCapabilitiesStatusChanged(RcsUceAdapter.CAPABILITY_TYPE_PRESENCE_UCE);
        verify(mPublishProcessor).checkAndSendPendingRequest();
    }

    private PublishControllerImpl createPublishController() {
        PublishControllerImpl publishController = new PublishControllerImpl(mContext, mSubId,
                mUceCtrlCallback, Looper.getMainLooper(), mDeviceCapListenerFactory,
                mPublishProcessorFactory);
        publishController.setPublishStateCallback(mPublishStateCallbacks);
        return publishController;
    }
}
