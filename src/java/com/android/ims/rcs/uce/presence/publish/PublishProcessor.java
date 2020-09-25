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

import android.content.Context;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishTriggerType;

public class PublishProcessor {

    public PublishProcessor(Context context, int subId, DeviceCapabilityInfo capabilityInfo,
            PublishControllerCallback callback) {
    }

    public void onRcsConnected(RcsFeatureManager featureManager) {
        // TODO: Implement this method
    }

    public void onRcsDisconnected() {
        // TODO: Implement this method
    }

    public void onDestroy() {
        // TODO: Implement this method
    }

    /**
     * Execute the publish request. This method is called by the handler of the PublishController.
     * @param triggerType The type of triggering the publish request.
     */
    public void doPublish(@PublishTriggerType int triggerType) {
        // TODO: Implement this method
    }
}
