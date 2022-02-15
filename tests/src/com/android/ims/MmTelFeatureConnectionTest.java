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

package com.android.ims;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MmTelFeatureConnectionTest extends ImsTestBase {

    private class TestCallback extends Binder implements IInterface {

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    private class CallbackManagerTest extends
            ImsCallbackAdapterManager<TestCallback> {

        List<TestCallback> mCallbacks = new ArrayList<>();

        CallbackManagerTest(Context context, Object lock) {
            super(context, lock, 0 /*slotId*/, 1 /*subId*/);
        }

        // A callback has been registered. Register that callback with the MmTelFeature.
        @Override
        public void registerCallback(TestCallback localCallback) {
            mCallbacks.add(localCallback);
        }

        // A callback has been removed, unregister that callback with the MmTelFeature.
        @Override
        public void unregisterCallback(TestCallback localCallback) {
            mCallbacks.remove(localCallback);
        }

        public boolean doesCallbackExist(TestCallback callback) {
            return mCallbacks.contains(callback);
        }
    }
    private CallbackManagerTest mCallbackManagerUT;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCallbackManagerUT = new CallbackManagerTest(mContext, this);
    }

    @After
    public void tearDown() throws Exception {
        mCallbackManagerUT = null;
        super.tearDown();
    }

    /**
     * Basic test of deprecated functionality, ensure that adding the callback directly triggers the
     * appropriate registerCallback and unregisterCallback calls.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_addAndRemoveCallback() throws Exception {
        TestCallback testCallback = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback));

        mCallbackManagerUT.removeCallback(testCallback);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback));
    }

    /**
     * Ensure that adding the callback and linking subId triggers the appropriate registerCallback
     * and unregisterCallback calls.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_addCallbackForSubAndRemove() throws Exception {
        TestCallback testCallback = new TestCallback();
        int testSub = 1;
        mCallbackManagerUT.addCallbackForSubscription(testCallback, testSub);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback));

        mCallbackManagerUT.removeCallback(testCallback);
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback));
    }

    /**
     * The close() method has been called, so all callbacks should be cleaned up and notified
     * that they have been removed.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_closeSub() throws Exception {
        TestCallback testCallback1 = new TestCallback();
        int testSub1 = 1;

        mCallbackManagerUT.addCallbackForSubscription(testCallback1, testSub1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));

        // Close the manager, ensure subscription callback are removed
        mCallbackManagerUT.close();
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));
    }

    /**
     * The close() method has been called, so all callbacks should be cleaned up.
     */
    @Test
    @SmallTest
    public void testCallbackAdapter_closeSlotBasedCallbacks() throws Exception {
        TestCallback testCallback1 = new TestCallback();
        TestCallback testCallback2 = new TestCallback();
        mCallbackManagerUT.addCallback(testCallback1);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback1));
        mCallbackManagerUT.addCallback(testCallback2);
        assertTrue(mCallbackManagerUT.doesCallbackExist(testCallback2));

        // Close the manager, ensure all subscription callbacks are removed
        mCallbackManagerUT.close();
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback1));
        assertFalse(mCallbackManagerUT.doesCallbackExist(testCallback2));
    }
}
