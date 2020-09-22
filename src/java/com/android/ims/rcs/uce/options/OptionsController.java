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

package com.android.ims.rcs.uce.options;

import android.annotation.NonNull;
import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;

import com.android.ims.rcs.uce.ControllerBase;
import com.android.ims.rcs.uce.CapabilityExchangeListenerAdapter;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter;

import java.util.List;

/**
 * The interface to define the operations of the SIP OPTIONS
 */
public interface OptionsController extends ControllerBase {
    /**
     * Request the capabilities for the requested contact.
     */
    void sendCapabilitiesRequest(@NonNull Uri contactUri,
            @NonNull RcsContactUceCapability ownCapabilities,
            @NonNull RcsCapabilityExchangeImplAdapter.OptionsResponseCallback c);

    /**
     * Retrieve the device's capabilities. This request is from the ImsService to send the
     * capabilities to the remote side.
     */
    void retrieveCapabilitiesForRemote(@NonNull Uri contactUri,
            @NonNull List<String> remoteCapabilities,
            @NonNull CapabilityExchangeListenerAdapter.OptionsRequestCallback c);
}
