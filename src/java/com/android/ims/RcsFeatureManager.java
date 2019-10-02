/*
 * Copyright (c) 2019 The Android Open Source Project
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

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

public class RcsFeatureManager {
    private static final String TAG = "RcsFeatureManager";

    private final int mSlotId;
    private final Context mContext;
    private RcsFeatureConnection mRcsFeatureConnection;

    public RcsFeatureManager(Context context, int slotId) {
        Log.d(TAG, "RcsFeatureManager slotId: " + slotId);
        mContext = context;
        mSlotId = slotId;

        mRcsFeatureConnection = RcsFeatureConnection.create(mContext, mSlotId);
    }

    /**
     * Testing interface used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        /**
         * Mock-able interface for {@link SubscriptionManager#getSubId(int)} used for testing.
         */
        int getSubId(int slotId);
    }

    private static SubscriptionManagerProxy sSubscriptionManagerProxy
            = new SubscriptionManagerProxy() {
                @Override
                public int getSubId(int slotId) {
                    int[] subIds = SubscriptionManager.getSubId(slotId);
                    if (subIds != null) {
                        Log.i(TAG, "getSubId : " + subIds[0]);
                        return subIds[0];
                    }
                    return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                }
            };

    /**
     * Testing function used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public static void setSubscriptionManager(SubscriptionManagerProxy proxy) {
        sSubscriptionManagerProxy = proxy;
    }

    /**
     * Check if RCS UCE feature is supported by carrier.
     */
    public static boolean isRcsUceSupportedByCarrier(Context context, int slotId) {
        int subId = sSubscriptionManagerProxy.getSubId(slotId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.e(TAG, "Getting subIds is failure! slotId: " + slotId);
            return false;
        }

        boolean isPresenceSupported = false;
        boolean isSipOptionsSupported = false;
        CarrierConfigManager configManager =
            (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForSubId(subId);
            if (b != null) {
                isPresenceSupported =
                    b.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, false);
                isSipOptionsSupported =
                    b.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false);
            }
        }
        Log.d(TAG, "isRcsUceSupportedByCarrier subId: " + subId
                + ", presence= " + isPresenceSupported
                + ", sip options=" + isSipOptionsSupported);
        return isPresenceSupported|isSipOptionsSupported;
    }
}
