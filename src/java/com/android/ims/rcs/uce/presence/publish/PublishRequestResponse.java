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

import android.annotation.Nullable;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IPublishResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;

import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.time.Instant;
import java.util.Optional;

/**
 * Receiving the result callback of the publish request.
 */
public class PublishRequestResponse {

    private final long mTaskId;
    private volatile boolean mNeedRetry;
    private volatile PublishControllerCallback mPublishCtrlCallback;

    private Optional<Integer> mCmdErrorCode;
    private Optional<Integer> mNetworkRespSipCode;
    private Optional<String> mReasonPhrase;
    private Optional<Integer> mReasonHeaderCause;
    private Optional<String> mReasonHeaderText;

    // The timestamp when receive the response from the network.
    private Instant mResponseTimestamp;

    public PublishRequestResponse(PublishControllerCallback publishCtrlCallback, long taskId) {
        mTaskId = taskId;
        mPublishCtrlCallback = publishCtrlCallback;
        mCmdErrorCode = Optional.empty();
        mNetworkRespSipCode = Optional.empty();
        mReasonPhrase = Optional.empty();
        mReasonHeaderCause = Optional.empty();
        mReasonHeaderText = Optional.empty();
    }

    // The result callback of the publish capability request.
    private IPublishResponseCallback mResponseCallback = new IPublishResponseCallback.Stub() {
        @Override
        public void onCommandError(int code) {
            PublishRequestResponse.this.onCommandError(code);
        }

        @Override
        public void onNetworkResponse(int code, String reason) {
            PublishRequestResponse.this.onNetworkResponse(code, reason);
        }

        @Override
        public void onNetworkRespHeader(int code, String reasonPhrase, int reasonHeaderCause,
                String reasonHeaderText) {
            PublishRequestResponse.this.onNetworkResponse(code, reasonPhrase, reasonHeaderCause,
                    reasonHeaderText);
        }
    };

    public IPublishResponseCallback getResponseCallback() {
        return mResponseCallback;
    }

    public long getTaskId() {
        return mTaskId;
    }

    /**
     * Retrieve the command error code which received from the network.
     */
    public Optional<Integer> getCmdErrorCode() {
        return mCmdErrorCode;
    }

    /**
     * Retrieve the network response sip code which received from the network.
     */
    public Optional<Integer> getNetworkRespSipCode() {
        return mNetworkRespSipCode;
    }

    /**
     * Retrieve the reason phrase of the network response which received from the network.
     */
    public Optional<String> getReasonPhrase() {
        return mReasonPhrase;
    }

    /**
     * Retrieve the reason header from the network response.
     */
    public Optional<Integer> getReasonHeaderCause() {
        return mReasonHeaderCause;
    }

    /**
     * Retrieve the description of the reason header.
     */
    public Optional<String> getReasonHeaderText() {
        return mReasonHeaderText;
    }

    /**
     * Get the timestamp of receiving the network response callback.
     */
    public @Nullable Instant getResponseTimestamp() {
        return mResponseTimestamp;
    }

    public void onDestroy() {
        mPublishCtrlCallback = null;
    }

    private void onCommandError(int errorCode) {
        mResponseTimestamp = Instant.now();
        mCmdErrorCode = Optional.of(errorCode);
        updateRetryFlagByCommandError();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestCommandError(this);
        }
    }

    private void onNetworkResponse(int sipCode, String reason) {
        mResponseTimestamp = Instant.now();
        mNetworkRespSipCode = Optional.of(sipCode);
        mReasonPhrase = Optional.ofNullable(reason);
        updateRetryFlagByNetworkResponse();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestNetworkResp(this);
        }
    }

    private void onNetworkResponse(int sipCode, String reasonPhrase, int reasonHeaderCause,
            String reasonHeaderText) {
        mResponseTimestamp = Instant.now();
        mNetworkRespSipCode = Optional.of(sipCode);
        mReasonPhrase = Optional.ofNullable(reasonPhrase);
        mReasonHeaderCause = Optional.of(reasonHeaderCause);
        mReasonHeaderText = Optional.ofNullable(reasonHeaderText);
        updateRetryFlagByNetworkResponse();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestNetworkResp(this);
        }
    }

    private void updateRetryFlagByCommandError() {
        switch(getCmdErrorCode().orElse(-1)) {
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE:
                mNeedRetry = true;
                break;
        }
    }

    private void updateRetryFlagByNetworkResponse() {
        int networkRespSipCode = getReasonHeaderCause().orElseGet(
                () -> getNetworkRespSipCode().orElse(-1));
        switch (networkRespSipCode) {
            case NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT:
            case NetworkSipCode.SIP_CODE_INTERVAL_TOO_BRIEF:
            case NetworkSipCode.SIP_CODE_TEMPORARILY_UNAVAILABLE:
            case NetworkSipCode.SIP_CODE_BUSY:
            case NetworkSipCode.SIP_CODE_SERVER_INTERNAL_ERROR:
            case NetworkSipCode.SIP_CODE_SERVICE_UNAVAILABLE:
            case NetworkSipCode.SIP_CODE_SERVER_TIMEOUT:
            case NetworkSipCode.SIP_CODE_BUSY_EVERYWHERE:
            case NetworkSipCode.SIP_CODE_DECLINE:
                mNeedRetry = true;
                break;
        }
    }

    /*
     * Check whether the publishing request is successful.
     */
    public boolean isRequestSuccess() {
        final int sipCodeOk = NetworkSipCode.SIP_CODE_OK;
        if (getNetworkRespSipCode().filter(c -> c == sipCodeOk).isPresent() &&
                (!getReasonHeaderCause().isPresent()
                        || getReasonHeaderCause().filter(c -> c == sipCodeOk).isPresent())) {
            return true;
        }
        return false;
    }

    /**
     * Check whether the publishing request needs to be retried.
     */
    public boolean needRetry() {
        return mNeedRetry;
    }

    /**
     * Convert the command error code to the publish state
     */
    public int getPublishStateByCmdErrorCode() {
        if (getCmdErrorCode().orElse(-1) ==
                RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Convert the network sip code to the publish state
     */
    public int getPublishStateByNetworkResponse() {
        int respSipCode;
        if (getReasonHeaderCause().isPresent()) {
            respSipCode = getReasonHeaderCause().get();
        } else {
            respSipCode = getNetworkRespSipCode().orElse(-1);
        }

        switch (respSipCode) {
            case NetworkSipCode.SIP_CODE_OK:
                return RcsUceAdapter.PUBLISH_STATE_OK;
            case NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT:
                return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
            default:
                return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
        }
    }

    /**
     * Get the information of the publish request response.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("taskId=").append(mTaskId)
                .append(", CmdErrorCode=").append(getCmdErrorCode().orElse(-1))
                .append(", NetworkRespSipCode=").append(getNetworkRespSipCode().orElse(-1))
                .append(", ReasonPhrase=").append(getReasonPhrase().orElse(""))
                .append(", ReasonHeaderCause=").append(getReasonHeaderCause().orElse(-1))
                .append(", ReasonHeaderText=").append(getReasonHeaderText().orElse(""))
                .append(", ResponseTimestamp=").append(mResponseTimestamp)
                .append(", isRequestSuccess=").append(isRequestSuccess())
                .append(", needRetry=").append(mNeedRetry);
        return builder.toString();
    }
}
