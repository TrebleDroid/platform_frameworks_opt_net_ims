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

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IOptionsRequestCallback;
import android.telephony.ims.aidl.IOptionsResponseCallback;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.util.List;

/**
 * The implementation of OptionsController.
 */
public class OptionsControllerImpl implements OptionsController {

    private final Context mContext;
    private final int mSubId;
    private final UceControllerCallback mCallback;
    private final Looper mLooper;

    public OptionsControllerImpl(Context context, int subId, UceControllerCallback c,
            Looper looper) {
        mContext = context;
        mSubId = subId;
        mCallback = c;
        mLooper = looper;
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        // TODO: Implement this method
    }

    @Override
    public void onRcsDisconnected() {
        // TODO: Implement this method
    }

    @Override
    public void onDestroy() {
        // TODO: Implement this method
    }

    @Override
    public void sendCapabilitiesRequest(Uri contactUri, RcsContactUceCapability ownCapabilities,
            IOptionsResponseCallback c) {
        // TODO: Implement this method
    }

    @Override
    public void retrieveCapabilitiesForRemote(Uri contactUri, List<String> remoteCapabilities,
            IOptionsRequestCallback c) {
        // TODO: Implement this method
    }
}
