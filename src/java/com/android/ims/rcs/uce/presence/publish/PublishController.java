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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter.PublishState;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;

import com.android.ims.rcs.uce.ControllerBase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The interface related to the PUBLISH request.
 */
public interface PublishController extends ControllerBase {

    /** Publish is triggered by the ImsService */
    int PUBLISH_TRIGGER_SERVICE = 1;

    /** Publish trigger type: retry */
    int PUBLISH_TRIGGER_RETRY = 2;

    /** Publish trigger type: TTY preferred changes */
    int PUBLISH_TRIGGER_TTY_PREFERRED_CHANGE = 3;

    /** Publish trigger type: Airplane mode changes */
    int PUBLISH_TRIGGER_AIRPLANE_MODE_CHANGE = 4;

    /** Publish trigger type: Mobile data changes */
    int PUBLISH_TRIGGER_MOBILE_DATA_CHANGE = 5;

    /** Publish trigger type: VT setting changes */
    int PUBLISH_TRIGGER_VT_SETTING_CHANGE = 6;

    /** Publish trigger type: MMTEL registered */
    int PUBLISH_TRIGGER_MMTEL_REGISTERED = 7;

    /** Publish trigger type: MMTEL unregistered */
    int PUBLISH_TRIGGER_MMTEL_UNREGISTERED = 8;

    /** Publish trigger type: MMTEL capability changes */
    int PUBLISH_TRIGGER_MMTEL_CAPABILITY_CHANGE = 9;

    /** Publish trigger type: RCS registered */
    int PUBLISH_TRIGGER_RCS_REGISTERED = 10;

    /** Publish trigger type: RCS unregistered */
    int PUBLISH_TRIGGER_RCS_UNREGISTERED = 11;

    /** Publish trigger type: provisioning changes */
    int PUBLISH_TRIGGER_PROVISIONING_CHANGE = 12;

    @IntDef(value = {
            PUBLISH_TRIGGER_SERVICE,
            PUBLISH_TRIGGER_RETRY,
            PUBLISH_TRIGGER_TTY_PREFERRED_CHANGE,
            PUBLISH_TRIGGER_AIRPLANE_MODE_CHANGE,
            PUBLISH_TRIGGER_MOBILE_DATA_CHANGE,
            PUBLISH_TRIGGER_VT_SETTING_CHANGE,
            PUBLISH_TRIGGER_MMTEL_REGISTERED,
            PUBLISH_TRIGGER_MMTEL_UNREGISTERED,
            PUBLISH_TRIGGER_MMTEL_CAPABILITY_CHANGE,
            PUBLISH_TRIGGER_RCS_REGISTERED,
            PUBLISH_TRIGGER_RCS_UNREGISTERED,
            PUBLISH_TRIGGER_PROVISIONING_CHANGE
    }, prefix="PUBLISH_TRIGGER_")
    @Retention(RetentionPolicy.SOURCE)
    @interface PublishTriggerType {}

    /**
     * Receive the callback from the sub-components which interact with PublishController.
     */
    interface PublishControllerCallback {
        /**
         * Request publish from local.
         */
        void requestPublishFromInternal(@PublishTriggerType int type, long delay);

        /**
         * Update the publish request result.
         */
        void updatePublishRequestResult(int publishState);
    }

    /**
     * Retrieve the RCS UCE Publish state.
     */
    @PublishState int getUcePublishState();

    /**
     * Notify that the device's capabilities have been unpublished from the network.
     */
    void onUnpublish();

    /**
     * Retrieve the device's capabilities.
     */
    RcsContactUceCapability getDeviceCapabilities();

    /**
     * Publish the device's capabilities to the Presence server.
     */
    void requestPublishCapabilitiesFromService();

    /**
     * Register a {@link PublishStateCallback} to listen to the published state changed.
     */
    void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);

    /**
     * Removes an existing {@link PublishStateCallback}.
     */
    void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);
}
