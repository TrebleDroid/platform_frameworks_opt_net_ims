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

/**
 * Receiving the result callback of the publish request.
 */
public class PublishRequestResponse {

    private final long mTaskId;
    private volatile boolean mNeedRetry;
    private volatile PublishControllerCallback mPublishCtrlCallback;

    private int mCmdErrorCode;
    private int mNetworkRespSipCode;
    private String mNetworkRespReason;

    // The timestamp when receive the response from the network.
    private Instant mResponseTimestamp;

    public PublishRequestResponse(PublishControllerCallback publishCtrlCallback, long taskId) {
        mTaskId = taskId;
        mPublishCtrlCallback = publishCtrlCallback;
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
    };

    public IPublishResponseCallback getResponseCallback() {
        return mResponseCallback;
    }

    public long getTaskId() {
        return mTaskId;
    }

    public int getCmdErrorCode() {
        return mCmdErrorCode;
    }

    public int getNetworkRespSipCode() {
        return mNetworkRespSipCode;
    }

    public @Nullable Instant getResponseTimestamp() {
        return mResponseTimestamp;
    }

    public void onDestroy() {
        mPublishCtrlCallback = null;
    }

    private void onCommandError(int errorCode) {
        mResponseTimestamp = Instant.now();
        mCmdErrorCode = errorCode;
        updateRetryFlagByCommandError();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestCommandError(this);
        }
    }

    private void onNetworkResponse(int sipCode, String reason) {
        mResponseTimestamp = Instant.now();
        mNetworkRespSipCode = sipCode;
        mNetworkRespReason = reason;
        updateRetryFlagByNetworkResponse();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestNetworkResp(this);
        }
    }

    private void updateRetryFlagByCommandError() {
        switch(mCmdErrorCode) {
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE:
                mNeedRetry = true;
                break;
        }
    }

    private void updateRetryFlagByNetworkResponse() {
        switch (mNetworkRespSipCode) {
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
        return (mNetworkRespSipCode == NetworkSipCode.SIP_CODE_OK) ? true : false;
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
        if (RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT == mCmdErrorCode) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Convert the network sip code to the publish state
     */
    public int getPublishStateByNetworkResponse() {
        switch (mNetworkRespSipCode) {
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
                .append(", CmdErrorCode=").append(mCmdErrorCode)
                .append(", NetworkResponse=").append(mNetworkRespSipCode)
                .append(", NetworkResponseReason=").append(mNetworkRespReason)
                .append(", ResponseTimestamp=").append(mResponseTimestamp)
                .append(", isRequestSuccess=").append(isRequestSuccess())
                .append(", needRetry=").append(mNeedRetry);
        return builder.toString();
    }
}
