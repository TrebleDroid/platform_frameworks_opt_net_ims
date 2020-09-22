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

package com.android.ims.rcs.uce.eab;

import android.annotation.NonNull;
import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;

import java.util.List;

/**
 * The result class of retrieving capabilities from cache.
 */
public class EabCapabilityResult {
    private final List<RcsContactUceCapability> mContactCapabilities;
    private final List<Uri> mExpiredContacts;

    public EabCapabilityResult(List<RcsContactUceCapability> capabilities,
            List<Uri> expiredContacts) {
        mContactCapabilities = capabilities;
        mExpiredContacts = expiredContacts;
    }

    /**
     * Return the contacts capabilities which are cached in the EAB database and
     * are not expired.
     */
    public @NonNull List<RcsContactUceCapability> getContactCapabilities() {
        return mContactCapabilities;
    }

    /**
     * Return the expired contacts which are required to refresh the capabilities
     * from the carrier network.
     */
    public @NonNull List<Uri> getExpiredContacts() {
        return mExpiredContacts;
    }
}
