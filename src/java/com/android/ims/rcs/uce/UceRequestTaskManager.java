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

import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.util.List;

/**
 * The interface of managing the capability request and the availability request.
 */
public interface UceRequestTaskManager {
    /**
     * Trigger the capability request task.
     */
    void triggerCapabilityRequestTask(UceControllerCallback controller, List<Uri> uriList,
            RcsUceAdapter.CapabilitiesCallback callback);
    /**
     * Trigger the availability request task.
     */
    void triggerAvailabilityRequestTask(UceControllerCallback controller, Uri uri,
            RcsUceAdapter.CapabilitiesCallback callback);

    /**
     * Notify the task manager to destroy.
     */
    void onDestroy();
}
