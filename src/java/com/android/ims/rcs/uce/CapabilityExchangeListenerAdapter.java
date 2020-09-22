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

import android.annotation.NonNull;
import android.telephony.ims.RcsContactUceCapability;

import java.util.List;

/**
 * The adapter class of the class CapabilityExchangeEventListener.
 */
public class CapabilityExchangeListenerAdapter {
    /**
     * Interface used by the framework to respond to OPTIONS requests.
     */
    public interface OptionsRequestCallback {
        /**
         * Respond to a remote capability request from the contact specified with the capabilities
         * of this device.
         */
        void respondToCapabilityRequest(@NonNull RcsContactUceCapability ownCapabilities);
        /**
         * Respond to a remote capability request from the contact specified with the specified
         * error.
         */
        void respondToCapabilityRequestWithError(int code, @NonNull String reason);
    }
}
