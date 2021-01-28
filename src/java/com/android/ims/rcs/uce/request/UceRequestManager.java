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

package com.android.ims.rcs.uce.request;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.util.Log;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Managers the capabilities requests and the availability requests from UceController.
 */
public class UceRequestManager {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "UceRequestManager";

    /**
     * Testing interface used to mock UceUtils in testing.
     */
    @VisibleForTesting
    public interface UceUtilsProxy {
        /**
         * The interface for {@link UceUtils#isPresenceCapExchangeEnabled(Context, int)} used for
         * testing.
         */
        boolean isPresenceCapExchangeEnabled(Context context, int subId);

        /**
         * The interface for {@link UceUtils#isPresenceSupported(Context, int)} used for testing.
         */
        boolean isPresenceSupported(Context context, int subId);

        /**
         * The interface for {@link UceUtils#isSipOptionsSupported(Context, int)} used for testing.
         */
        boolean isSipOptionsSupported(Context context, int subId);
    }

    private static UceUtilsProxy sUceUtilsProxy = new UceUtilsProxy() {
        @Override
        public boolean isPresenceCapExchangeEnabled(Context context, int subId) {
            return UceUtils.isPresenceCapExchangeEnabled(context, subId);
        }

        @Override
        public boolean isPresenceSupported(Context context, int subId) {
            return UceUtils.isPresenceSupported(context, subId);
        }

        @Override
        public boolean isSipOptionsSupported(Context context, int subId) {
            return UceUtils.isSipOptionsSupported(context, subId);
        }
    };

    @VisibleForTesting
    public void setsUceUtilsProxy(UceUtilsProxy uceUtilsProxy) {
        sUceUtilsProxy = uceUtilsProxy;
    }

    /**
     * The callback interface to receive the request and result from the UceRequests.
     */
    public interface RequestManagerCallback {
        /**
         * Retrieve the contact capabilities from the cache.
         */
        List<EabCapabilityResult> getCapabilitiesFromCache(List<Uri> uriList);

        /**
         * Retrieve the contact availability from the cache.
         */
        EabCapabilityResult getAvailabilityFromCache(Uri uri);

        /**
         * Store the given capabilities to the cache.
         */
        void saveCapabilities(List<RcsContactUceCapability> contactCapabilities);

        /**
         * Notify that the request is finish. It only removed this request from the collections.
         */
        void onRequestFinished(Long taskId);

        /**
         * Notify that the request is failed. It will trigger the callback
         * IRcsUceControllerCallback#onError
         */
        void onRequestFailed(Long taskId);

        /**
         * Notify that the request is success. It will trigger the callback
         * IRcsUceControllerCallback#onComplete
         */
        void onRequestSuccess(Long taskId);

        /**
         * Notify that some contacts are not RCS anymore. It will updated the cached capabilities
         * and trigger the callback IRcsUceControllerCallback#onCapabilitiesReceived
         */
        void onResourceTerminated(Long taskId);

        /**
         * Notify that the capabilities updates. It will update the cached and trigger the callback
         * IRcsUceControllerCallback#onCapabilitiesReceived
         */
        void onCapabilityUpdate(Long taskId);

        /**
         * Check if UCE requests are forbidden by the network.
         * @return true when the UCE requests are forbidden by the network.
         */
        boolean isRequestForbidden();

        /**
         * Get the milliseconds need to wait for retry.
         * @return The milliseconds need to wait
         */
        long getRetryAfterMillis();

        /**
         * Notify UceController that the UCE request is forbidden.
         */
        void onRequestForbidden(boolean isForbidden, Integer errorCode, long retryAfterMillis);
    }

    private RequestManagerCallback mRequestMgrCallback = new RequestManagerCallback() {
        @Override
        public List<EabCapabilityResult> getCapabilitiesFromCache(List<Uri> uriList) {
            return mControllerCallback.getCapabilitiesFromCache(uriList);
        }

        @Override
        public EabCapabilityResult getAvailabilityFromCache(Uri uri) {
            return mControllerCallback.getAvailabilityFromCache(uri);
        }

        @Override
        public void saveCapabilities(List<RcsContactUceCapability> contactCapabilities) {
            mControllerCallback.saveCapabilities(contactCapabilities);
        }

        @Override
        public void onRequestFinished(Long taskId) {
            mHandler.sendRequestFinishedMessage(taskId);
        }

        @Override
        public void onRequestFailed(Long taskId) {
            mHandler.sendRequestFailedMessage(taskId);
        }

        @Override
        public void onRequestSuccess(Long taskId) {
            mHandler.sendRequestSuccessMessage(taskId);
        }

        @Override
        public void onResourceTerminated(Long taskId) {
            mHandler.sendResourceTerminatedMessage(taskId);
        }

        @Override
        public void onCapabilityUpdate(Long taskId) {
            mHandler.sendCapabilitiesUpdateMessage(taskId);
        }

        @Override
        public boolean isRequestForbidden() {
            return mControllerCallback.isRequestForbiddenByNetwork();
        }

        @Override
        public long getRetryAfterMillis() {
            return mControllerCallback.getRetryAfterMillis();
        }

        @Override
        public void onRequestForbidden(boolean isForbidden, Integer errorCode,
                long retryAfterMillis) {
            mControllerCallback.updateRequestForbidden(isForbidden, errorCode, retryAfterMillis);
        }
    };

    @VisibleForTesting
    public RequestManagerCallback getRequestManagerCallback() {
        return mRequestMgrCallback;
    }

    private final int mSubId;
    private final Context mContext;
    private final UceRequestHandler mHandler;
    private final Map<Long, UceRequest> mRequestCollection;
    private final Object mLock = new Object();
    private volatile boolean mIsDestroyed;

    private SubscribeController mSubscribeCtrl;
    private UceControllerCallback mControllerCallback;

    public UceRequestManager(Context context, int subId, Looper looper, UceControllerCallback c) {
        mSubId = subId;
        mContext = context;
        mControllerCallback = c;
        mRequestCollection = new HashMap<>();
        mHandler = new UceRequestHandler(this, looper);
        logi("create");
    }

    @VisibleForTesting
    public UceRequestManager(Context context, int subId, Looper looper, UceControllerCallback c,
            Map<Long, UceRequest> collection) {
        mSubId = subId;
        mContext = context;
        mControllerCallback = c;
        mRequestCollection = collection;
        mHandler = new UceRequestHandler(this, looper);
    }

    /**
     * Set the SubscribeController for requesting capabilities by Subscribe mechanism.
     */
    public void setSubscribeController(SubscribeController controller) {
        mSubscribeCtrl = controller;
    }

    /**
     * Notify that the request manager is destroyed.
     */
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyed = true;
        mHandler.onDestroy();
        synchronized (mLock) {
            mRequestCollection.forEach((taskId, request) -> request.onFinish());
            mRequestCollection.clear();
        }
    }

    /**
     * Send a new capability request. It is called by UceController.
     */
    public void sendCapabilityRequest(List<Uri> uriList, boolean skipFromCache,
            IRcsUceControllerCallback callback) throws RemoteException {
        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE, 0L);
            return;
        }
        sendRequestInternal(UceRequest.REQUEST_TYPE_CAPABILITY, uriList, skipFromCache, callback);
    }

    /**
     * Send a new availability request. It is called by UceController.
     */
    public void sendAvailabilityRequest(Uri uri, IRcsUceControllerCallback callback)
            throws RemoteException {
        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE, 0L);
            return;
        }
        sendRequestInternal(UceRequest.REQUEST_TYPE_AVAILABILITY,
                Collections.singletonList(uri), false /* skipFromCache */, callback);
    }

    private void sendRequestInternal(@UceRequest.UceRequestType int type, List<Uri> uriList,
            boolean skipFromCache, IRcsUceControllerCallback callback) throws RemoteException {
        UceRequest request = null;
        if (sUceUtilsProxy.isPresenceCapExchangeEnabled(mContext, mSubId) &&
                sUceUtilsProxy.isPresenceSupported(mContext, mSubId)) {
            request = new SubscribeRequest(mSubId, type, mRequestMgrCallback, mSubscribeCtrl);
            request.setContactUri(uriList);
            request.setSkipGettingFromCache(skipFromCache);
            request.setCapabilitiesCallback(callback);
        } else if (sUceUtilsProxy.isSipOptionsSupported(mContext, mSubId)) {
            // TODO: Implement the OPTIONS request
        }

        if (request == null) {
            logw("sendCapabilityRequest: Neither Presence nor OPTIONS are supported");
            callback.onError(RcsUceAdapter.ERROR_NOT_ENABLED, 0L);
            return;
        }

        logd("sendRequestInternal: taskId=" + request.getTaskId() + ", type=" + type);

        // Add this request to collection for tracking.
        addRequestToCollection(request);

        // Send this request to the message queue.
        mHandler.sendRequestMessage(request.getTaskId());
    }

    private void addRequestToCollection(UceRequest request) {
        synchronized (mLock) {
            mRequestCollection.put(request.getTaskId(), request);
        }
    }

    private UceRequest removeRequestFromCollection(Long taskId) {
        synchronized (mLock) {
            return mRequestCollection.remove(taskId);
        }
    }

    private UceRequest getRequestFromCollection(Long taskId) {
        synchronized (mLock) {
            return mRequestCollection.get(taskId);
        }
    }

    @VisibleForTesting
    public UceRequestHandler getUceRequestHandler() {
        return mHandler;
    }

    private static class UceRequestHandler extends Handler {
        private static final int EVENT_REQUEST_CAPABILITIES = 1;
        private static final int EVENT_REQUEST_FAILED = 2;
        private static final int EVENT_REQUEST_SUCCESS = 3;
        private static final int EVENT_REQUEST_FINISHED = 4;
        private static final int EVENT_RESOURCE_TERMINATED = 5;
        private static final int EVENT_CAPABILITIES_UPDATE = 6;

        private final WeakReference<UceRequestManager> mUceRequestMgrRef;

        public UceRequestHandler(UceRequestManager requestManager, Looper looper) {
            super(looper);
            mUceRequestMgrRef = new WeakReference<>(requestManager);
        }

        /**
         * Send the capabilities request message.
         */
        public void sendRequestMessage(Long taskId) {
            Message message = obtainMessage();
            message.what = EVENT_REQUEST_CAPABILITIES;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Send the task is finished message.
         */
        public void sendRequestFinishedMessage(Long taskId) {
            if (!hasMessages(EVENT_REQUEST_FINISHED, taskId)) {
                Message message = obtainMessage();
                message.what = EVENT_REQUEST_FINISHED;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Send the task is failed message.
         */
        public void sendRequestFailedMessage(Long taskId) {
            if (!hasMessages(EVENT_REQUEST_FAILED, taskId)) {
                Message message = obtainMessage();
                message.what = EVENT_REQUEST_FAILED;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Send the task is success message.
         */
        public void sendRequestSuccessMessage(Long taskId) {
            if (!hasMessages(EVENT_REQUEST_SUCCESS, taskId)) {
                Message message = obtainMessage();
                message.what = EVENT_REQUEST_SUCCESS;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Send the resource terminated message.
         */
        public void sendResourceTerminatedMessage(Long taskId) {
            if (!hasMessages(EVENT_RESOURCE_TERMINATED, taskId)) {
                Message message = obtainMessage();
                message.what = EVENT_RESOURCE_TERMINATED;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Send the capabilities is updated message.
         */
        public void sendCapabilitiesUpdateMessage(Long taskId) {
            if (!hasMessages(EVENT_CAPABILITIES_UPDATE, taskId)) {
                Message message = obtainMessage();
                message.what = EVENT_CAPABILITIES_UPDATE;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Remove all the messages from the handler
         */
        public void onDestroy() {
            removeCallbacksAndMessages(null);
        }

        @Override
        public void handleMessage(Message msg) {
            UceRequestManager requestManager = mUceRequestMgrRef.get();
            if (requestManager == null) {
                return;
            }
            final Long taskId = (Long) msg.obj;
            requestManager.logd("handleMessage: " + EVENT_DESCRIPTION.get(msg.what)
                + ", taskId=" + taskId);
            switch (msg.what) {
                case EVENT_REQUEST_CAPABILITIES: {
                    UceRequest request = requestManager.getRequestFromCollection(taskId);
                    if (request == null) {
                        requestManager.logw("handleMessage: cannot find request,taskId=" + taskId);
                        return;
                    }
                    request.executeRequest();
                    break;
                }
                case EVENT_REQUEST_FAILED: {
                    // Trigger the onError callback and terminate this request.
                    UceRequest request = requestManager.removeRequestFromCollection(taskId);
                    if (request == null) {
                        requestManager.logw("handleMessage: cannot find request,taskId=" + taskId);
                        return;
                    }
                    request.handleRequestFailed(false);
                    request.onFinish();
                    break;
                }
                case EVENT_REQUEST_SUCCESS: {
                    // Trigger the onComplete callback and finish this request.
                    UceRequest request = requestManager.removeRequestFromCollection(taskId);
                    if (request == null) {
                        requestManager.logw("handleMessage: cannot find request,taskId=" + taskId);
                        return;
                    }
                    request.handleRequestCompleted(false);
                    request.onFinish();
                    break;
                }
                case EVENT_REQUEST_FINISHED: {
                    // Terminate this request internally. It doesn't trigger any callbacks.
                    UceRequest request = requestManager.removeRequestFromCollection(taskId);
                    if (request == null) {
                        return;
                    }
                    request.onFinish();
                    break;
                }
                case EVENT_RESOURCE_TERMINATED: {
                    UceRequest request = requestManager.getRequestFromCollection(taskId);
                    if (request == null) {
                        requestManager.logw("handleMessage: cannot find request,taskId=" + taskId);
                        return;
                    }
                    boolean result = request.handleResourceTerminated();
                    if (!result) {
                        // Terminate the request if triggering capabilities callback failed.
                        requestManager.logd("Handle resource terminated failed, taskId=" + taskId);
                        requestManager.removeRequestFromCollection(taskId);
                        request.handleRequestFailed(false);
                        request.onFinish();
                    }
                    break;
                }
                case EVENT_CAPABILITIES_UPDATE: {
                    UceRequest request = requestManager.getRequestFromCollection(taskId);
                    if (request == null) {
                        requestManager.logw("handleMessage: cannot find request,taskId=" + taskId);
                        return;
                    }
                    boolean result = request.handleCapabilitiesUpdated();
                    if (!result) {
                        // Terminate the request if triggering capabilities callback failed.
                        requestManager.logd("Handle capabilities update failed, taskId=" + taskId);
                        requestManager.removeRequestFromCollection(taskId);
                        request.handleRequestFailed(false);
                        request.onFinish();
                    }
                    break;
                }
                default:
                    break;
            }
        }

        private static Map<Integer, String> EVENT_DESCRIPTION = new HashMap<>();
        static {
            EVENT_DESCRIPTION.put(EVENT_REQUEST_CAPABILITIES, "REQUEST_CAPABILITIES");
            EVENT_DESCRIPTION.put(EVENT_REQUEST_FAILED, "REQUEST_FAILED");
            EVENT_DESCRIPTION.put(EVENT_REQUEST_SUCCESS, "REQUEST_SUCCESS");
            EVENT_DESCRIPTION.put(EVENT_REQUEST_FINISHED, "REQUEST_FINISHED");
            EVENT_DESCRIPTION.put(EVENT_RESOURCE_TERMINATED, "RESOURCE_TERMINATED");
            EVENT_DESCRIPTION.put(EVENT_CAPABILITIES_UPDATE, "CAPABILITIES_UPDATE");
        }
    }

    private void logi(String log) {
        Log.i(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logd(String log) {
        Log.d(LOG_TAG, getLogPrefix().append(log).toString());
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
}
