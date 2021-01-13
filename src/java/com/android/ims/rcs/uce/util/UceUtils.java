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

package com.android.ims.rcs.uce.util;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;

public class UceUtils {

    private static final String LOG_PREFIX = "RcsUce.";

    private static final String LOG_TAG = "UceUtils";

    private static long TASK_ID = 0L;

    /**
     * Get the log prefix of RCS UCE
     */
    public static String getLogPrefix() {
        return LOG_PREFIX;
    }

    /**
     * Generate the unique UCE request task id.
     */
    public static synchronized long generateTaskId() {
        return ++TASK_ID;
    }

    public static boolean isEabProvisioned(Context context, int subId) {
        boolean isProvisioned = false;
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(LOG_TAG, "isEabProvisioned: invalid subscriptionId " + subId);
            return false;
        }
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle config = configManager.getConfigForSubId(subId);
            if (config != null && !config.getBoolean(
                    CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL)) {
                return true;
            }
        }
        try {
            ProvisioningManager manager = ProvisioningManager.createForSubscriptionId(subId);
            isProvisioned = manager.getProvisioningIntValue(
                    ProvisioningManager.KEY_EAB_PROVISIONING_STATUS)
                    == ProvisioningManager.PROVISIONING_VALUE_ENABLED;
        } catch (Exception e) {
            Log.w(LOG_TAG, "isEabProvisioned: exception=" + e.getMessage());
        }
        return isProvisioned;
    }

    /**
     * Check if Presence is supported by the carrier.
     */
    public static boolean isPresenceSupported(Context context, int subId) {
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL);
    }

    /**
     * Check if SIP OPTIONS is supported by the carrier.
     */
    public static boolean isSipOptionsSupported(Context context, int subId) {
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL);
    }
}
