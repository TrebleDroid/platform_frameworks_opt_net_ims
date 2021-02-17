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

import android.annotation.NonNull;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IOptionsResponseCallback;

import com.android.ims.rcs.uce.options.OptionsController;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/**
 * The UceRequest to request the capabilities when the OPTIONS mechanism is supported by the
 * network.
 */
public class OptionsRequest extends UceRequest {

    // The result callback of the capabilities request from the IMS service.
    private IOptionsResponseCallback mResponseCallback = new IOptionsResponseCallback.Stub() {
        @Override
        public void onCommandError(int code) {
            OptionsRequest.this.onCommandError(code);
        }

        @Override
        public void onNetworkResponse(int sipCode, String reason, List<String> remoteCaps) {
            OptionsRequest.this.onNetworkResponse(sipCode, reason, remoteCaps);
        }
    };

    private Uri mContactUri;
    private OptionsController mOptionsController;

    public OptionsRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback, OptionsController optionsController) {
        super(subId, requestType, taskMgrCallback);
        mOptionsController = optionsController;
        logd("OptionsRequest created");
    }

    @VisibleForTesting
    public OptionsRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback, OptionsController optionsController,
            CapabilityRequestResponse requestResponse) {
        super(subId, requestType, taskMgrCallback, requestResponse);
        mOptionsController = optionsController;
        logd("OptionsRequest created");
    }

    @Override
    public void onFinish() {
        mOptionsController = null;
        super.onFinish();
        logd("OptionsRequest finish");
    }

    @Override
    public void requestCapabilities(@NonNull List<Uri> requestCapUris) {
        OptionsController optionsController = mOptionsController;
        if (optionsController == null) {
            logw("requestCapabilities: request is finished");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
            return;
        }

        // Get the device's capabilities to send to the remote client.
        RcsContactUceCapability deviceCap = mRequestManagerCallback.getDeviceCapabilities(
                RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS);
        if (deviceCap == null) {
            logw("requestCapabilities: Cannot get device capabilities");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
            return;
        }

        mContactUri = requestCapUris.get(0);
        List<String> featureTags = deviceCap.getOptionsFeatureTags();

        logi("requestCapabilities: featureTag size=" + featureTags.size());
        try {
            optionsController.sendCapabilitiesRequest(mContactUri, featureTags, mResponseCallback);
        } catch (RemoteException e) {
            logw("requestCapabilities exception: " + e);
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
        }
    }

    @VisibleForTesting
    public IOptionsResponseCallback getResponseCallback() {
        return mResponseCallback;
    }

    // Handle the command error callback.
    private void onCommandError(int cmdError) {
        logd("onCommandError: error code=" + cmdError);
        if (mIsFinished) {
            return;
        }
        mRequestResponse.setCommandError(cmdError);
        int capError = CapabilityRequestResponse.convertCommandErrorToCapabilityError(cmdError);
        mRequestResponse.setErrorCode(capError);
        mRequestManagerCallback.onRequestFailed(mTaskId);
    }

    // Handle the network response callback.
    private void onNetworkResponse(int sipCode, String reason, List<String> remoteCaps) {
        logd("onNetworkResponse: sipCode=" + sipCode + ", reason=" + reason
                + ", remoteCap size=" + ((remoteCaps == null) ? "null" : remoteCaps.size()));
        if (mIsFinished) {
            return;
        }

        // Set the network response code and the remote contact capabilities
        mRequestResponse.setNetworkResponseCode(sipCode, reason);
        mRequestResponse.setRemoteCapabilities(mContactUri, remoteCaps);

        // Notify the request result
        if (mRequestResponse.isNetworkResponseOK()) {
            mRequestManagerCallback.onCapabilityUpdate(mTaskId);
            mRequestManagerCallback.onRequestSuccess(mTaskId);
        } else {
            int capErrorCode = mRequestResponse.getCapabilityErrorFromSipError();
            mRequestResponse.setErrorCode(capErrorCode);
            mRequestManagerCallback.onRequestFailed(mTaskId);
        }
    }
}
