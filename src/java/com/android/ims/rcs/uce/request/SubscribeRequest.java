/*
 * Copyright (c) 2020 The Android Open Source Project
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
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.ISubscribeResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.CommandCode;

import com.android.ims.rcs.uce.presence.pidfparser.PidfParser;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The UceRequest to request the capabilities when the presence mechanism is supported by the
 * network.
 */
public class SubscribeRequest extends UceRequest {

    // The result callback of the capabilities request from IMS service.
    private ISubscribeResponseCallback mResponseCallback =
            new ISubscribeResponseCallback.Stub() {
                @Override
                public void onCommandError(int code) {
                    SubscribeRequest.this.onCommandError(code);
                }
                @Override
                public void onNetworkResponse(int code, String reason) {
                    SubscribeRequest.this.onNetworkResponse(code, reason);
                }
                @Override
                public void onNetworkRespHeader(int code, String reasonPhrase,
                        int reasonHeaderCause, String reasonHeaderText) {
                    SubscribeRequest.this.onNetworkResponse(code, reasonPhrase, reasonHeaderCause,
                            reasonHeaderText);
                }
                @Override
                public void onNotifyCapabilitiesUpdate(List<String> pidfXmls) {
                    SubscribeRequest.this.onCapabilitiesUpdate(pidfXmls);
                }
                @Override
                public void onResourceTerminated(List<RcsContactTerminatedReason> terminatedList) {
                    SubscribeRequest.this.onResourceTerminated(terminatedList);
                }
                @Override
                public void onTerminated(String reason, long retryAfterMillis) {
                    SubscribeRequest.this.onTerminated(reason, retryAfterMillis);
                }
            };

    private SubscribeController mSubscribeController;

    public SubscribeRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback, SubscribeController subscribeController) {
        super(subId, requestType, taskMgrCallback);
        mSubscribeController = subscribeController;
        logd("SubscribeRequest created");
    }

    @VisibleForTesting
    public SubscribeRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback, SubscribeController subscribeController,
            CapabilityRequestResponse requestResponse) {
        super(subId, requestType, taskMgrCallback, requestResponse);
        mSubscribeController = subscribeController;
        logd("SubscribeRequest created");
    }

    @Override
    public void onFinish() {
        mSubscribeController = null;
        super.onFinish();
        logd("SubscribeRequest finish");
    }

    @Override
    public void requestCapabilities(@NonNull List<Uri> requestCapUris) {
        SubscribeController subscribeController = mSubscribeController;
        if (subscribeController == null) {
            logw("requestCapabilities: request is finished");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
            return;
        }

        // TODO: Check if the network supports group subscribe or a bunch of individual subscribe.

        logi("requestCapabilities: size=" + requestCapUris.size());
        try {
            subscribeController.requestCapabilities(requestCapUris, mResponseCallback);
        } catch (RemoteException e) {
            logw("requestCapabilities exception: " + e);
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
        }
    }

    @VisibleForTesting
    public ISubscribeResponseCallback getResponseCallback() {
        return mResponseCallback;
    }

    // Handle the command error which is triggered by ISubscribeResponseCallback.
    private void onCommandError(@CommandCode int cmdError) {
        logd("onCommandError: error code=" + cmdError);
        if (mIsFinished) {
            return;
        }
        mRequestResponse.setCommandError(cmdError);
        // Set the capability error code and notify RequestManager that this request is failed.
        int capError = CapabilityRequestResponse.convertCommandErrorToCapabilityError(cmdError);
        mRequestResponse.setErrorCode(capError);
        mRequestManagerCallback.onRequestFailed(mTaskId);
    }

    // Handle the network response callback which is triggered by ISubscribeResponseCallback.
    private void onNetworkResponse(int sipCode, String reason) {
        logd("onNetworkResponse: code=" + sipCode + ", reason=" + reason);
        if (mIsFinished) {
            return;
        }
        mRequestResponse.setNetworkResponseCode(sipCode, reason);

        // Set the capability error code and notify RequestManager if the SIP code is not success.
        // Otherwise, waiting for the onNotifyCapabilitiesUpdate callback.
        if (!mRequestResponse.isNetworkResponseOK()) {
            int capErrorCode = mRequestResponse.getCapabilityErrorFromSipError();
            mRequestResponse.setErrorCode(capErrorCode);
            mRequestManagerCallback.onRequestFailed(mTaskId);
        }
    }

    // Handle the network response callback which is triggered by ISubscribeResponseCallback.
    private void onNetworkResponse(int sipCode, String reasonPhrase,
        int reasonHeaderCause, String reasonHeaderText) {
        logd("onNetworkResponse: code=" + sipCode + ", reasonPhrase=" + reasonPhrase +
                ", reasonHeaderCause=" + reasonHeaderCause +
                ", reasonHeaderText=" + reasonHeaderText);
        if (mIsFinished) {
            return;
        }
        mRequestResponse.setNetworkResponseCode(sipCode, reasonPhrase, reasonHeaderCause,
                reasonHeaderText);

        // Set the capability error code and notify RequestManager if the SIP code is not success.
        // Otherwise, waiting for the onNotifyCapabilitiesUpdate callback.
        if (!mRequestResponse.isNetworkResponseOK()) {
            int capErrorCode = mRequestResponse.getCapabilityErrorFromSipError();
            mRequestResponse.setErrorCode(capErrorCode);
            mRequestManagerCallback.onRequestFailed(mTaskId);
        }
    }

    // Handle the onResourceTerminated callback which is triggered by ISubscribeResponseCallback.
    private void onResourceTerminated(final List<RcsContactTerminatedReason> terminatedResource) {
        if (mIsFinished) {
            logw("onResourceTerminated: request is already finished");
            return;
        }
        // The parameter of the callback onResourceTerminated cannot be empty.
        if (terminatedResource == null || terminatedResource.isEmpty()) {
            logw("onResourceTerminated: parameter is empty");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            mRequestManagerCallback.onRequestFailed(mTaskId);
            return;
        }

        logd("onResourceTerminated: size=" + terminatedResource.size());

        // Add the terminated resource into the RequestResponse and notify the RequestManager
        // to process the RcsContactUceCapabilities update.
        mRequestResponse.addTerminatedResource(terminatedResource);
        mRequestManagerCallback.onResourceTerminated(mTaskId);
    }

    // Handle the CapabilitiesUpdate callback which is triggered by ISubscribeResponseCallback
    private void onCapabilitiesUpdate(final List<String> pidfXml) {
        if (mIsFinished) {
            logw("onCapabilitiesUpdate: request is already finished");
            return;
        }
        // The parameter of the callback onNotifyCapabilitiesUpdate cannot be empty.
        if (pidfXml == null || pidfXml.isEmpty()) {
            logw("onCapabilitiesUpdate: parameter is empty");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            mRequestManagerCallback.onRequestFailed(mTaskId);
            return;
        }

        // Convert from the pidf xml to the list of RcsContactUceCapability
        List<RcsContactUceCapability> capabilityList = pidfXml.stream()
                .map(pidf -> PidfParser.getRcsContactUceCapability(pidf))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        logd("onCapabilitiesUpdate: PIDF size=" + pidfXml.size()
                + ", contact capability size=" + capabilityList.size());

        // Add these updated RcsContactUceCapability into the RequestResponse and notify
        // the RequestManager to process the RcsContactUceCapabilities update.
        mRequestResponse.addUpdatedCapabilities(capabilityList);
        mRequestManagerCallback.onCapabilityUpdate(mTaskId);
    }

    // Handle the terminated callback which is triggered by ISubscribeResponseCallback.
    private void onTerminated(String reason, long retryAfterMillis) {
        logd("onTerminated: reason=" + reason + ", retryAfter=" + retryAfterMillis);
        if (mIsFinished) {
            return;
        }

        // The subscribe request is success when receive the network response code 200(OK) and
        // the parameter "retryAfter" is zero. Otherwise, this request is failed.
        if (mRequestResponse.isNetworkResponseOK() && (retryAfterMillis == 0)) {
            mRequestManagerCallback.onRequestSuccess(mTaskId);
        } else {
            // This request is failed. Store the retryAfter info and notify UceRequestManager.
            mRequestResponse.setRequestTerminated(reason, retryAfterMillis);
            int capErrorCode = mRequestResponse.getCapabilityErrorFromSipError();
            mRequestResponse.setErrorCode(capErrorCode);
            mRequestManagerCallback.onRequestFailed(mTaskId);
        }
    }
}
