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

import android.telephony.ims.ImsException;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.PublishResponseCallback;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;

/**
 * Receiving the result callback of the publish request.
 */
public class PublishRequestResponse implements PublishResponseCallback {

    private final long mTaskId;
    private volatile boolean mNeedRetry;
    private volatile PublishControllerCallback mPublishCtrlCallback;

    private int mCmdErrorCode;
    private int mNetworkRespSipCode;
    private String mNetworkRespReason;

    public PublishRequestResponse(PublishControllerCallback publishCtrlCallback, long taskId) {
        mTaskId = taskId;
        mPublishCtrlCallback = publishCtrlCallback;
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

    public void onDestroy() {
        mPublishCtrlCallback = null;
    }

    @Override
    public void onCommandError(int errorCode) throws ImsException {
        mCmdErrorCode = errorCode;
        updateRetryFlagByCommandError();

        PublishControllerCallback ctrlCallback = mPublishCtrlCallback;
        if (ctrlCallback != null) {
            ctrlCallback.onRequestCommandError(this);
        }
    }

    @Override
    public void onNetworkResponse(int sipCode, String reason) {
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
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_INSUFFICIENT_MEMORY:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_LOST_NETWORK_CONNECTION:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_SERVICE_UNAVAILABLE:
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
        if (RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT == mCmdErrorCode) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Convert the network sip code to the publish state
     */
    public int getPublishStateByNetworkResponse() {
        if (NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT == mNetworkRespSipCode) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Get the information of the publish request response.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("taskId=").append(mTaskId)
                .append(", mCmdErrorCode=").append(mCmdErrorCode)
                .append(", onNetworkResponse=").append(mNetworkRespSipCode)
                .append(", mNetworkResponseReason=").append(mNetworkRespReason)
                .append(", isRequestSuccess=").append(isRequestSuccess())
                .append(", needRetry=").append(mNeedRetry);
        return builder.toString();
    }
}
