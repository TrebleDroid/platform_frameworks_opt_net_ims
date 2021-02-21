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

import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE;

import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.text.TextUtils;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParser;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishTriggerType;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.time.Instant;

/**
 * Send the publish request and handle the response of the publish request result.
 */
public class PublishProcessor {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "PublishProcessor";

    // The length of time waiting for the response callback.
    private static final long RESPONSE_CALLBACK_WAITING_TIME = 60000L;

    private final int mSubId;
    private final Context mContext;
    private volatile boolean mIsDestroyed;
    private volatile RcsFeatureManager mRcsFeatureManager;

    // Manage the state of the publish processor.
    private PublishProcessorState mProcessorState;

    // The information of the device's capabilities.
    private DeviceCapabilityInfo mDeviceCapabilities;

    // The callback of the PublishController
    private PublishControllerCallback mPublishCtrlCallback;

    private final LocalLog mLocalLog = new LocalLog(UceUtils.LOG_SIZE);

    public PublishProcessor(Context context, int subId, DeviceCapabilityInfo capabilityInfo,
            PublishControllerCallback publishCtrlCallback) {
        mSubId = subId;
        mContext = context;
        mDeviceCapabilities = capabilityInfo;
        mPublishCtrlCallback = publishCtrlCallback;
        mProcessorState = new PublishProcessorState();
    }

    /**
     * The RcsFeature has been connected to the framework.
     */
    public void onRcsConnected(RcsFeatureManager featureManager) {
        mLocalLog.log("onRcsConnected");
        logi("onRcsConnected");
        mRcsFeatureManager = featureManager;
    }

    /**
     * The framework has lost the binding to the RcsFeature.
     */
    public void onRcsDisconnected() {
        mLocalLog.log("onRcsDisconnected");
        logi("onRcsDisconnected");
        mRcsFeatureManager = null;
    }

    /**
     * Set the destroy flag
     */
    public void onDestroy() {
        mLocalLog.log("onDestroy");
        logi("onDestroy");
        mIsDestroyed = true;
    }

    /**
     * Execute the publish request. This method is called by the handler of the PublishController.
     * @param triggerType The type of triggering the publish request.
     */
    public void doPublish(@PublishTriggerType int triggerType) {
        if (mIsDestroyed) return;

        mLocalLog.log("doPublish: trigger type=" + triggerType);
        logi("doPublish: trigger type=" + triggerType);

        // Check if it should reset the retry count.
        if (isResetRetryNeeded(triggerType)) {
            mProcessorState.resetRetryCount();
        }

        // Return if this request is not allowed to execute.
        if (!isRequestAllowed()) {
            mLocalLog.log("doPublish: The request is not allowed");
            return;
        }

        // Get the latest device's capabilities.
        RcsContactUceCapability deviceCapability =
                mDeviceCapabilities.getDeviceCapabilities(CAPABILITY_MECHANISM_PRESENCE, mContext);
        if (deviceCapability == null) {
            logw("doPublish: device capability is null");
            return;
        }

        // Convert the device's capabilities to pidf format.
        String pidfXml = PidfParser.convertToPidf(deviceCapability);
        if (TextUtils.isEmpty(pidfXml)) {
            logw("doPublish: pidfXml is empty");
            return;
        }

        // Set the pending request and return if RCS is not connected.
        RcsFeatureManager featureManager = mRcsFeatureManager;
        if (featureManager == null) {
            logw("doPublish: NOT connected");
            setPendingRequest(true);
            return;
        }

        // Publish to the Presence server.
        publishCapabilities(featureManager, pidfXml);
    }

    // Check if the giving trigger type should reset the retry count.
    private boolean isResetRetryNeeded(@PublishTriggerType int triggerType) {
        // Do no reset the retry count if the request is triggered by the previous failed retry.
        if (triggerType == PublishController.PUBLISH_TRIGGER_RETRY) {
            return false;
        }
        return true;
    }

    // Check if the publish request is allowed to execute.
    private boolean isRequestAllowed() {
        // Check if the instance is destroyed.
        if (mIsDestroyed) {
            logd("isPublishAllowed: This instance is already destroyed");
            return false;
        }

        // Check if it has provisioned. When the provisioning changes, a new publish request will
        // be triggered.
        if (!UceUtils.isEabProvisioned(mContext, mSubId)) {
            logd("isPublishAllowed: NOT provisioned");
            return false;
        }

        // Do not request publish if the IMS is not registered. When the IMS is registered
        // afterward, a new publish request will be triggered.
        if (!mDeviceCapabilities.isImsRegistered()) {
            logd("isPublishAllowed: IMS is not registered");
            return false;
        }

        // Set the pending flag if there's already a request running now.
        if (mProcessorState.isPublishingNow()) {
            logd("isPublishAllowed: There is already a request running now");
            setPendingRequest(true);
            return false;
        }

        // Skip this request and re-send the request with the delay time if the publish request
        // executes too frequently.
        if (!mProcessorState.isCurrentTimeAllowed()) {
            logd("isPublishAllowed: Current time is not allowed, resend this request");
            long delayTime = mProcessorState.getDelayTimeToAllowPublish();
            mPublishCtrlCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
            return false;
        }
        return true;
    }

    // Publish the device capabilities with the given pidf
    private void publishCapabilities(@NonNull RcsFeatureManager featureManager,
            @NonNull String pidfXml) {
        PublishRequestResponse requestResponse = null;
        try {
            // Set publishing flag
            mProcessorState.setPublishingFlag(true);

            // Clear the pending request flag since we're publishing the latest device's capability
            setPendingRequest(false);

            // Generate a unique taskId to track this request.
            long taskId = mProcessorState.generatePublishTaskId();
            requestResponse = new PublishRequestResponse(mPublishCtrlCallback, taskId);

            mLocalLog.log("publish capabilities: taskId=" + taskId);
            logi("publishCapabilities: taskId=" + taskId);

            // request publication
            featureManager.requestPublication(pidfXml, requestResponse.getResponseCallback());

            // Send a request canceled timer to avoid waiting too long for the response callback.
            mPublishCtrlCallback.setupRequestCanceledTimer(taskId, RESPONSE_CALLBACK_WAITING_TIME);

        } catch (RemoteException e) {
            mLocalLog.log("publish capability exception: " + e.getMessage());
            logw("publishCapabilities: exception=" + e.getMessage());
            // Exception occurred, end this request.
            setRequestEnded(requestResponse);
            checkAndSendPendingRequest();
        }
   }

    /**
     * Handle the command error callback of the publish request. This method is called by the
     * handler of the PublishController.
     */
    public void onCommandError(PublishRequestResponse requestResponse) {
        if (!checkRequestRespValid(requestResponse)) {
            mLocalLog.log("Command error callback is invalid");
            logw("onCommandError: request response is invalid");
            setRequestEnded(requestResponse);
            checkAndSendPendingRequest();
            return;
        }

        mLocalLog.log("Receive command error code=" + requestResponse.getCmdErrorCode());
        logd("onCommandError: " + requestResponse.toString());

        if (!mProcessorState.isReachMaximumRetries() && requestResponse.needRetry()) {
            // Increase the retry count
            mProcessorState.increaseRetryCount();

            // Reset the pending flag since it is going to resend a publish request.
            setPendingRequest(false);

            // Resend a publish request
            long delayTime = mProcessorState.getDelayTimeToAllowPublish();
            mPublishCtrlCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
        } else {
            // Update the publish state if the request is failed and doesn't need to retry.
            int publishState = requestResponse.getPublishStateByCmdErrorCode();
            Instant responseTimestamp = requestResponse.getResponseTimestamp();
            mPublishCtrlCallback.updatePublishRequestResult(publishState, responseTimestamp);

            // Check if there is a pending request
            checkAndSendPendingRequest();
        }

        // End this request
        setRequestEnded(requestResponse);
    }

    /**
     * Handle the network response callback of the publish request. This method is called by the
     * handler of the PublishController.
     */
    public void onNetworkResponse(PublishRequestResponse requestResponse) {
        if (!checkRequestRespValid(requestResponse)) {
            mLocalLog.log("Network response callback is invalid");
            logw("onNetworkResponse: request response is invalid");
            setRequestEnded(requestResponse);
            checkAndSendPendingRequest();
            return;
        }

        mLocalLog.log("Receive network response code=" + requestResponse.getNetworkRespSipCode());
        logd("onNetworkResponse: " + requestResponse.toString());

        if (!mProcessorState.isReachMaximumRetries() && requestResponse.needRetry()) {
            // Increase the retry count
            mProcessorState.increaseRetryCount();

            // Reset the pending flag since it is going to resend a publish request.
            setPendingRequest(false);

            // Resend a publish request
            long delayTime = mProcessorState.getDelayTimeToAllowPublish();
            mPublishCtrlCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
        } else {
            // Reset the retry count if the publish is success.
            if (requestResponse.isRequestSuccess()) {
                mProcessorState.resetRetryCount();
            }
            // Update the publish state if the request doesn't need to retry.
            int publishResult = requestResponse.getPublishStateByNetworkResponse();
            Instant responseTimestamp = requestResponse.getResponseTimestamp();
            mPublishCtrlCallback.updatePublishRequestResult(publishResult, responseTimestamp);

            // Check if there is a pending request
            checkAndSendPendingRequest();
        }

        // End this request
        setRequestEnded(requestResponse);
    }

    // Check if the request response callback is valid.
    private boolean checkRequestRespValid(PublishRequestResponse requestResponse) {
        if (requestResponse == null) {
            logd("checkRequestRespValid: request response is null");
            return false;
        }

        if (!mProcessorState.isPublishingNow()) {
            logd("checkRequestRespValid: the request is finished");
            return false;
        }

        // Abandon this response callback if the current taskId is different to the response
        // callback taskId. This response callback is obsoleted.
        long taskId = mProcessorState.getCurrentTaskId();
        long responseTaskId = requestResponse.getTaskId();
        if (taskId != responseTaskId) {
            logd("checkRequestRespValid: invalid taskId! current taskId=" + taskId
                    + ", response callback taskId=" + responseTaskId);
            return false;
        }

        if (mIsDestroyed) {
            logd("checkRequestRespValid: is already destroyed! taskId=" + taskId);
            return false;
        }
        return true;
    }

    /**
     * Cancel the publishing request since it has token too long for waiting the response callback.
     * This method is called by the handler of the PublishController.
     */
    public void cancelPublishRequest(long taskId) {
        mLocalLog.log("cancel publish request: taskId=" + taskId);
        logd("cancelPublishRequest: taskId=" + taskId);
        setRequestEnded(null);
        checkAndSendPendingRequest();
    }

    private void setRequestEnded(PublishRequestResponse requestResponse) {
        long taskId = -1L;
        if (requestResponse != null) {
            requestResponse.onDestroy();
            taskId = requestResponse.getTaskId();
        }
        mProcessorState.setPublishingFlag(false);
        mPublishCtrlCallback.clearRequestCanceledTimer();

        mLocalLog.log("Set request ended: taskId=" + taskId);
        logd("setRequestEnded: taskId=" + taskId);
    }

    public void setPendingRequest(boolean pendingRequest) {
        mProcessorState.setPendingRequest(pendingRequest);
    }

    public void checkAndSendPendingRequest() {
        if (mIsDestroyed) return;
        if (mProcessorState.hasPendingRequest()) {
            logd("checkAndSendPendingRequest: send pending request");
            mProcessorState.setPublishingFlag(false);

            long delayTime = mProcessorState.getDelayTimeToAllowPublish();
            mPublishCtrlCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
        }
    }

    @VisibleForTesting
    public void setProcessorState(PublishProcessorState processorState) {
        mProcessorState = processorState;
    }

    private void logd(String log) {
       Log.d(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logi(String log) {
       Log.i(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logw(String log) {
        Log.w(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId);
        builder.append("] ");
        return builder;
    }

    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("PublishProcessor" + "[subId: " + mSubId + "]:");
        pw.increaseIndent();

        pw.print("ProcessorState: isPublishing=");
        pw.print(mProcessorState.isPublishingNow());
        pw.print(", hasReachedMaxRetries=");
        pw.print(mProcessorState.isReachMaximumRetries());
        pw.print(", delayTimeToAllowPublish=");
        pw.println(mProcessorState.getDelayTimeToAllowPublish());

        pw.println("Log:");
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();
        pw.println("---");

        pw.decreaseIndent();
    }
}
