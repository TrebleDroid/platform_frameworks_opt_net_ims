/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.telephony.ims.RcsContactPresenceTuple;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DeviceCapabilityInfoTest extends ImsTestBase {

    int mSubId = 1;
    @Mock PublishServiceDescTracker mPublishServiceDescTracker;

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
    public void testGetPresenceCapabilityForSameDescription() throws Exception {
        DeviceCapabilityInfo deviceCapInfo = createDeviceCapabilityInfo();

        Set<ServiceDescription> mTestCapability = new ArraySet<>();
        mTestCapability.add(getChatDescription());
        mTestCapability.add(getMmtelDescription());
        mTestCapability.add(getUndefinedDescription());

        deviceCapInfo.addLastSuccessfulServiceDescription(getMmtelDescription());
        deviceCapInfo.addLastSuccessfulServiceDescription(getChatDescription());
        deviceCapInfo.addLastSuccessfulServiceDescription(getUndefinedDescription());
        assertFalse(deviceCapInfo.isPresenceCapabilityChanged(mTestCapability));
    }

    @Test
    @SmallTest
    public void testGetPresenceCapabilityForSameSizeOfDescription() throws Exception {
        DeviceCapabilityInfo deviceCapInfo = createDeviceCapabilityInfo();

        Set<ServiceDescription> mTestCapability = new ArraySet<>();
        mTestCapability.add(getChatDescription());
        mTestCapability.add(getMmtelDescription());
        mTestCapability.add(getUndefinedDescription());

        deviceCapInfo.addLastSuccessfulServiceDescription(getMmtelDescription());
        deviceCapInfo.addLastSuccessfulServiceDescription(getChatDescription());
        deviceCapInfo.addLastSuccessfulServiceDescription(getUndefined2Description());

        assertTrue(deviceCapInfo.isPresenceCapabilityChanged(mTestCapability));
    }
    private DeviceCapabilityInfo createDeviceCapabilityInfo() {
        DeviceCapabilityInfo deviceCapInfo = new DeviceCapabilityInfo(mSubId, null);
        return deviceCapInfo;
    }

    private ServiceDescription getChatDescription() {
        ServiceDescription SERVICE_DESCRIPTION_CHAT_SESSION =
                new ServiceDescription(
                        RcsContactPresenceTuple.SERVICE_ID_CHAT_V2,
                        "2.0" /*version*/,
                        null /*description*/
                );
        return SERVICE_DESCRIPTION_CHAT_SESSION;
    }

    private ServiceDescription getMmtelDescription() {
        ServiceDescription SERVICE_DESCRIPTION_MMTEL_VOICE = new ServiceDescription(
                RcsContactPresenceTuple.SERVICE_ID_MMTEL,
                "1.0" /*version*/,
                "Voice Service" /*description*/
        );
        return SERVICE_DESCRIPTION_MMTEL_VOICE;
    }

    private ServiceDescription getUndefinedDescription() {
        ServiceDescription SERVICE_DESCRIPTION_TEST = new ServiceDescription(
                "test",
                "1.0" /*version*/,
                "Test_Service" /*description*/
        );
        return SERVICE_DESCRIPTION_TEST;
    }

    private ServiceDescription getUndefined2Description() {
        ServiceDescription SERVICE_DESCRIPTION_TEST2 = new ServiceDescription(
                "test1",
                "1.0" /*version*/,
                "Test_Service" /*description*/
        );
        return SERVICE_DESCRIPTION_TEST2;
    }
}
