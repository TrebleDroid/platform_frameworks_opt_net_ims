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

import android.annotation.IntDef;
import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.request.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The base class of the UCE request to request the capabilities from the carrier network.
 */
public abstract class UceRequest {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "UceRequest";

    /** The request type: CAPABILITY */
    public static final int REQUEST_TYPE_CAPABILITY = 1;

    /** The request type: AVAILABILITY */
    public static final int REQUEST_TYPE_AVAILABILITY = 2;

    /**@hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "REQUEST_TYPE_", value = {
            REQUEST_TYPE_CAPABILITY,
            REQUEST_TYPE_AVAILABILITY
    })
    public @interface UceRequestType {}

    protected final int mSubId;
    protected final long mTaskId;
    protected final @UceRequestType int mRequestType;
    protected final CapabilityRequestResponse mRequestResponse;
    protected final RequestManagerCallback mRequestManagerCallback;

    protected List<Uri> mUriList;
    protected volatile boolean mIsFinished;
    protected volatile boolean mSkipGettingFromCache;

    public UceRequest(int subId, @UceRequestType int type, RequestManagerCallback callback) {
        mSubId = subId;
        mRequestType = type;
        mRequestManagerCallback = callback;
        mRequestResponse = new CapabilityRequestResponse();
        mTaskId = UceUtils.generateTaskId();
    }

    @VisibleForTesting
    public UceRequest(int subId, @UceRequestType int type, RequestManagerCallback callback,
            CapabilityRequestResponse requestResponse) {
        mSubId = subId;
        mRequestType = type;
        mRequestManagerCallback = callback;
        mRequestResponse = requestResponse;
        mTaskId = UceUtils.generateTaskId();
    }

    /**
     * Get the task ID of this request.
     */
    public long getTaskId() {
        return mTaskId;
    }

    /**
     * Set this request is finish.
     */
    public void onFinish() {
        mIsFinished = true;
    }

    /**
     * Set the uris of the capabilities that are being requested for.
     */
    public void setContactUri(List<Uri> uris) {
        mUriList = uris;
    }

    /**
     * Set the callback to receive the contacts capabilities update.
     */
    public void setCapabilitiesCallback(IRcsUceControllerCallback callback) {
        mRequestResponse.setCapabilitiesCallback(callback);
    }

    /**
     * Set to check if this request should be getting the capabilities from the cache. The flag is
     * set when the request is triggered by the capability polling service. The contacts from the
     * capability polling service are already expired, skip checking from the cache.
     */
    public void setSkipGettingFromCache(boolean skipFromCache) {
        mSkipGettingFromCache = skipFromCache;
    }

    /**
     * Return if the capabilities request should skip getting from the cache. The flag is set when
     * the request is triggered by the capability polling service and the request doesn't need to
     * check the cache again.
     */
    private boolean isSkipGettingFromCache() {
        return mSkipGettingFromCache;
    }

    /**
     * Start executing this request.
     */
    public void executeRequest() {
        // Check whether this request is allowed to execute.
        if (!isRequestAllowed()) {
            logd("executeRequest: The request is not allowed to execute.");
            handleRequestFailed(true);
            return;
        }

        // Get the capabilities from the cache.
        final List<RcsContactUceCapability> cachedCapabilityList
                = isSkipGettingFromCache() ? Collections.EMPTY_LIST : getCapabilitiesFromCache();

        logd("executeRequest: cached capabilities size=" + cachedCapabilityList.size());

        // Trigger the callback for those capabilities from the cache.
        if (!handleCachedCapabilities(cachedCapabilityList)) {
            // Terminate this request if triggering the capabilities received callback failed.
            handleRequestFailed(true);
            return;
        }

        // Get the rest contacts which need to request capabilities from the network.
        final List<Uri> requestCapUris = getRequestingFromNetworkUris(cachedCapabilityList);

        logd("executeRequest: requestCapUris size=" + requestCapUris.size());

        // Finish this request if all the requested capabilities can be retrieved from cache.
        // Otherwise, request capabilities from the network for those contacts which cannot
        // retrieve capabilities from the cache.
        if (requestCapUris.isEmpty()) {
            handleRequestCompleted(true);
        } else {
            requestCapabilities(requestCapUris);
        }
    }

    // Check whether this request is allowed to execute.
    private boolean isRequestAllowed() {
        if (mUriList == null || mUriList.isEmpty()) {
            logw("isRequestAllowed: uri is empty");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }

        if (mIsFinished) {
            logw("isRequestAllowed: This request is finished");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }

        if (mRequestManagerCallback.isRequestForbidden()) {
            logw("isRequestAllowed: The request is forbidden");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_FORBIDDEN);
            return false;
        }
        return true;
    }

    // Get the cached capabilities by the given request type.
    private List<RcsContactUceCapability> getCapabilitiesFromCache() {
        List<EabCapabilityResult> resultList = null;
        if (mRequestType == REQUEST_TYPE_CAPABILITY) {
            resultList = mRequestManagerCallback.getCapabilitiesFromCache(mUriList);
        } else if (mRequestType == REQUEST_TYPE_AVAILABILITY) {
            // Always get the first element if the request type is availability.
            Uri uri = mUriList.get(0);
            EabCapabilityResult eabResult = mRequestManagerCallback.getAvailabilityFromCache(uri);
            resultList = new ArrayList<>();
            resultList.add(eabResult);
        }
        if (resultList == null) {
            return Collections.emptyList();
        }
        return resultList.stream()
                .filter(Objects::nonNull)
                .filter(result -> result.getStatus() == EabCapabilityResult.EAB_QUERY_SUCCESSFUL)
                .map(EabCapabilityResult::getContactCapabilities)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get the contact uris which cannot retrieve capabilities from the cache.
     * @param cachedCapabilityList The capabilities which are already stored in the cache.
     */
    private List<Uri> getRequestingFromNetworkUris(
            List<RcsContactUceCapability> cachedCapabilityList) {
        return mUriList.stream()
                .filter(uri -> cachedCapabilityList.stream()
                        .noneMatch(cap -> cap.getContactUri().equals(uri)))
                        .collect(Collectors.toList());
    }

    /*
     * Trigger the capabilities received callback for those cached capabilities. This is called
     * when the contact capabilities are retrieved from cache. Return true if triggering the
     * callback is success.
     */
    private boolean handleCachedCapabilities(List<RcsContactUceCapability> cachedCapabilities) {
        return mRequestResponse.triggerCachedCapabilitiesCallback(cachedCapabilities);
    }

    /**
     * Store the updated capabilities to the cache and trigger the capabilities received callback.
     * This is called when receive the capabilities updated from the network.
     * @return true if save to the cache and trigger the callback are success.
     */
    public boolean handleCapabilitiesUpdated() {
        List<RcsContactUceCapability> updatedCapabilities =
                mRequestResponse.getUpdatedContactCapability();
        logd("handleCapabilitiesUpdated: size=" + updatedCapabilities.size());

        if (!updatedCapabilities.isEmpty()) {
            logd("handleCapabilitiesUpdated: ready to saveCapabilities");
            // Save the updated capabilities to the cache
            mRequestManagerCallback.saveCapabilities(updatedCapabilities);

            logd("handleCapabilitiesUpdated: ready to triggerCapabilitiesCallback");
            // Trigger the capabilities received callback
            return mRequestResponse.triggerCapabilitiesCallback(updatedCapabilities);
        }
        return true;
    }

    /**
     * Store the terminated contact to the cache and trigger the capabilities received callback.
     * This is called when receive the resource terminated callback from the network.
     * @return true if save cache and trigger the callback success.
     */
    public boolean handleResourceTerminated() {
        List<RcsContactUceCapability> terminatedResources =
                mRequestResponse.getTerminatedResources();
        logd("handleResourceTerminated: size=" + terminatedResources.size());

        if (!terminatedResources.isEmpty()) {
            // Save the terminated capabilities to the cache
            mRequestManagerCallback.saveCapabilities(terminatedResources);
            // Trigger the capabilities received callback
            return mRequestResponse.triggerResourceTerminatedCallback(terminatedResources);
        }
        return true;
    }

    /**
     * Send the onError callback to end this request.
     */
    public void handleRequestFailed(boolean notifyRequestManager) {
        logd("handleRequestFailed: " + mRequestResponse + ", notify=" + notifyRequestManager);

        // Check and update the ServerState if the network response code is forbidden.
        checkRequestForbidden();

        // Trigger the onError callback
        mRequestResponse.triggerErrorCallback();

        // Notify RequestManager that the request is finished.
        if (notifyRequestManager) {
            mRequestManagerCallback.onRequestFinished(mTaskId);
        }
    }

    private void checkRequestForbidden() {
        if (mRequestResponse.isRequestForbidden()) {
            int errorCode = mRequestResponse.getErrorCode().orElse(RcsUceAdapter.ERROR_FORBIDDEN);
            long retryAfter = mRequestResponse.getRetryAfterMillis();
            mRequestManagerCallback.onRequestForbidden(true, errorCode, retryAfter);
        }
    }

    /**
     * Send the onComplete callback to finish this request.
     */
    public void handleRequestCompleted(boolean notifyRequestManager) {
        logd("handleRequestCompleted: notify=" + notifyRequestManager);
        // Trigger the onComplete callback
        mRequestResponse.triggerCompletedCallback();

        // Notify RequestManager that the request is finished.
        if (notifyRequestManager) {
            mRequestManagerCallback.onRequestFinished(mTaskId);
        }
    }

    /*
     * Requests capabilities from IMS. The inherited request is required to override this method
     * to define the behavior of requesting capabilities.
     */
    protected abstract void requestCapabilities(List<Uri> requestCapUris);

    protected void logd(String log) {
        Log.d(LOG_TAG, getLogPrefix().append(log).toString());
    }

    protected void logw(String log) {
        Log.w(LOG_TAG, getLogPrefix().append(log).toString());
    }

    protected void logi(String log) {
        Log.i(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId).append("][taskId=").append(mTaskId).append("] ");
        return builder;
    }
}
