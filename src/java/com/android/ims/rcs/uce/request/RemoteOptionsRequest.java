/*
 * Copyright (c) 2021 The Android Open Source Project
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

package com.android.ims.rcs.uce.request;

import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS;

import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IOptionsRequestCallback;

import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.util.FeatureTags;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handle the OPTIONS request from the network.
 */
public class RemoteOptionsRequest extends UceRequest {

    private final List<String> mRemoteFeatureTags;
    private final IOptionsRequestCallback mRequestCallback;
    private boolean mIsRemoteNumberBlocked = false;

    public RemoteOptionsRequest(int subId, @UceRequestType int type,
            RequestManagerCallback requestMgrCallback, IOptionsRequestCallback requestCallback) {
        super(subId, type, requestMgrCallback);
        mRequestCallback = requestCallback;
        mRemoteFeatureTags = new ArrayList<>();
        logd("RemoteOptionsRequest: created");
    }

    public void setRemoteFeatureTags(List<String> remoteFeatureTags) {
        remoteFeatureTags.forEach(featureTag -> mRemoteFeatureTags.add(featureTag));
    }

    public void setIsRemoteNumberBlocked(boolean isBlocked) {
        mIsRemoteNumberBlocked = isBlocked;
    }

    @Override
    public void executeRequest() {
        logd("RemoteOptionsRequest: executeRequest");
        try {
            executeRequestInternal();
        } catch (RemoteException e) {
            logw("RemoteOptionsRequest exception: " + e);
        } finally {
            // Remove the remote options request from the UceRequestManager.
            mRequestManagerCallback.onRequestFinished(mTaskId);
        }
    }

    private void executeRequestInternal() throws RemoteException {
        if (mUriList == null || mUriList.isEmpty()) {
            logw("RemoteOptionsRequest: uri is empty");
            triggerOptionsRequestWithErrorCallback(NetworkSipCode.SIP_CODE_BAD_REQUEST,
                    NetworkSipCode.SIP_BAD_REQUEST);
            return;
        }

        if (mIsFinished) {
            logw("RemoteOptionsRequest: This request is finished");
            triggerOptionsRequestWithErrorCallback(NetworkSipCode.SIP_CODE_SERVICE_UNAVAILABLE,
                    NetworkSipCode.SIP_SERVICE_UNAVAILABLE);
            return;
        }

        // Store the remote capabilities
        Uri contactUri = mUriList.get(0);
        RcsContactUceCapability remoteCaps = FeatureTags.getContactCapability(contactUri,
                mRemoteFeatureTags);
        mRequestManagerCallback.saveCapabilities(Collections.singletonList(remoteCaps));

        // Get the device's capabilities and trigger the request callback
        RcsContactUceCapability deviceCaps = mRequestManagerCallback.getDeviceCapabilities(
                CAPABILITY_MECHANISM_OPTIONS);
        if (deviceCaps == null) {
            logw("RemoteOptionsRequest: The device's capabilities is empty");
            triggerOptionsRequestWithErrorCallback(NetworkSipCode.SIP_CODE_SERVER_INTERNAL_ERROR,
                    NetworkSipCode.SIP_INTERNAL_SERVER_ERROR);
        } else {
            logd("RemoteOptionsRequest: Respond to capability request, blocked="
                    + mIsRemoteNumberBlocked);
            triggerOptionsRequestCallback(deviceCaps, mIsRemoteNumberBlocked);
        }
    }

    private void triggerOptionsRequestWithErrorCallback(int errorCode, String reason)
            throws RemoteException {
        mRequestCallback.respondToCapabilityRequestWithError(errorCode, reason);
    }

    private void triggerOptionsRequestCallback(RcsContactUceCapability deviceCaps,
            boolean isRemoteNumberBlocked)
            throws RemoteException {
        mRequestCallback.respondToCapabilityRequest(deviceCaps, isRemoteNumberBlocked);
    }

    @Override
    protected void requestCapabilities(List<Uri> requestCapUris) {
        // The request is triggered by the network. It doesn't need to request capabilities.
    }
}
