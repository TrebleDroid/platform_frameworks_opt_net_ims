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

import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.CommandCode;
import android.util.Log;

import com.android.ims.rcs.uce.presence.pidfparser.PidfParserUtils;
import com.android.ims.rcs.uce.util.NetworkSipCode;
import com.android.ims.rcs.uce.util.UceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handle the result of the capability request.
 */
public class CapabilityRequestResponse {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "CapRequestResponse";

    // The command error code of the request. It is assigned by the callback "onCommandError"
    private @CommandCode Optional<Integer> mCommandError;

    // The SIP code of the network response. It is assigned by the callback "onNetworkResponse"
    private Optional<Integer> mNetworkRespSipCode;

    // The reason of the network response. It is assigned by the callback "onNetworkResponse"
    private Optional<String> mReasonPhrase;

    // The response sip code from the reason header
    private Optional<Integer> mReasonHeaderCause;

    // The phrase from the reason header
    private Optional<String> mReasonHeaderText;

    // The reason why the this request was terminated. This value is assigned by the callback
    // "onTerminated"
    private Optional<String> mTerminatedReason;

    // How long after this request can be retried. This value is assigned by the callback
    // "onTerminated"
    private Optional<Long> mRetryAfterMillis;

    // The error code of this request.
    private @RcsUceAdapter.ErrorCode Optional<Integer> mErrorCode;

    // The list of the terminated resource. This is assigned by the callback "onResourceTerminated"
    private final List<RcsContactUceCapability> mTerminatedResource;

    // The list of the updated capabilities. This is assigned by the callback
    // "onNotifyCapabilitiesUpdate"
    private final List<RcsContactUceCapability> mUpdatedCapabilityList;

    // The list of the remote contact's capability.
    private final Set<String> mRemoteCaps;

    // The callback to notify the result of this request.
    public IRcsUceControllerCallback mCapabilitiesCallback;

    public CapabilityRequestResponse() {
        mCommandError = Optional.empty();
        mNetworkRespSipCode = Optional.empty();
        mReasonPhrase = Optional.empty();
        mReasonHeaderCause = Optional.empty();
        mReasonHeaderText = Optional.empty();
        mTerminatedReason = Optional.empty();
        mRetryAfterMillis = Optional.of(0L);
        mTerminatedResource = new ArrayList<>();
        mUpdatedCapabilityList = new ArrayList<>();
        mRemoteCaps = new HashSet<>();
    }

    /**
     * Set the callback to receive the contacts capabilities update.
     */
    public void setCapabilitiesCallback(IRcsUceControllerCallback c) {
        mCapabilitiesCallback = c;
    }

    /**
     * Set the error code when the request is failed.
     */
    public void setErrorCode(@RcsUceAdapter.ErrorCode int errorCode) {
        mErrorCode = Optional.of(errorCode);
    }

    /**
     * Get the error code of this request.
     */
    public Optional<Integer> getErrorCode() {
        return mErrorCode;
    }

    /**
     * Set the command error code.
     */
    public void setCommandError(@CommandCode int commandError) {
        mCommandError = Optional.of(commandError);
    }

    /**
     * Set the network response of this request which is sent by the network.
     */
    public void setNetworkResponseCode(int sipCode, String reason) {
        mNetworkRespSipCode = Optional.of(sipCode);
        mReasonPhrase = Optional.ofNullable(reason);
    }

    /**
     * Set the network response of this request which is sent by the network.
     */
    public void setNetworkResponseCode(int sipCode, String reasonPhrase,
            int reasonHeaderCause, String reasonHeaderText) {
        mNetworkRespSipCode = Optional.of(sipCode);
        mReasonPhrase = Optional.ofNullable(reasonPhrase);
        mReasonHeaderCause = Optional.of(reasonHeaderCause);
        mReasonHeaderText = Optional.ofNullable(reasonHeaderText);
    }

    /**
     * Get the sip code of the network response.
     */
    public Optional<Integer> getNetworkRespSipCode() {
        return mNetworkRespSipCode;
    }

    /**
     * Get the reason of the network response.
     */
    public Optional<String> getReasonPhrase() {
        return mReasonPhrase;
    }

    /**
     * Get the response sip code from the reason header.
     */
    public Optional<Integer> getReasonHeaderCause() {
        return mReasonHeaderCause;
    }

    /**
     * Get the response phrae from the reason header.
     */
    public Optional<String> getReasonHeaderText() {
        return mReasonHeaderText;
    }

    /**
     * Check if the network response is success.
     * @return true if the network response code is OK or Accepted and the Reason header cause
     * is either not present or OK.
     */
    public boolean isNetworkResponseOK() {
        final int sipCodeOk = NetworkSipCode.SIP_CODE_OK;
        final int sipCodeAccepted = NetworkSipCode.SIP_CODE_ACCEPTED;
        Optional<Integer> respSipCode = getNetworkRespSipCode();
        if (respSipCode.filter(c -> (c == sipCodeOk || c == sipCodeAccepted)).isPresent() &&
                (!getReasonHeaderCause().isPresent()
                        || getReasonHeaderCause().filter(c -> c == sipCodeOk).isPresent())) {
            return true;
        }
        return false;
    }

    /**
     * Check if the request is forbidden.
     * @return true if the Reason header sip code is 403(Forbidden) or the response sip code is 403.
     */
    public boolean isRequestForbidden() {
        // Check the Reason header sip code if the Reason header is present, otherwise check the
        // response sip code.
        if (getReasonHeaderCause().isPresent()) {
            return getReasonHeaderCause()
                    .filter(c -> c == NetworkSipCode.SIP_CODE_FORBIDDEN).isPresent();
        } else {
            return getNetworkRespSipCode()
            .filter(c -> c == NetworkSipCode.SIP_CODE_FORBIDDEN).isPresent();
        }
    }

    /**
     * Set the reason and retry-after info when the callback onTerminated is called.
     * @param reason The reason why this request is terminated.
     * @param retryAfterMillis How long to wait before retry this request.
     */
    public void setRequestTerminated(String reason, long retryAfterMillis) {
        mTerminatedReason = Optional.ofNullable(reason);
        mRetryAfterMillis = Optional.of(retryAfterMillis);
    }

    /**
     * @return Return the retryAfterMillis, 0L if the value is not present.
     */
    public long getRetryAfterMillis() {
        return mRetryAfterMillis.orElse(0L);
    }

    /**
     * Add the updated contact capabilities which sent from ImsService.
     */
    public void addUpdatedCapabilities(List<RcsContactUceCapability> capabilityList) {
        synchronized (mUpdatedCapabilityList) {
            mUpdatedCapabilityList.addAll(capabilityList);
        }
    }

    /**
     * Get all the updated capabilities to trigger the capability receive callback.
     */
    public List<RcsContactUceCapability> getUpdatedContactCapability() {
        synchronized (mUpdatedCapabilityList) {
            return Collections.unmodifiableList(mUpdatedCapabilityList);
        }
    }

    /**
     * Remove the given capabilities from the UpdatedCapabilityList when these capabilities have
     * updated to the requester.
     */
    private void removeCapabilities(List<RcsContactUceCapability> capabilityList) {
        synchronized (mUpdatedCapabilityList) {
            mUpdatedCapabilityList.removeAll(capabilityList);
        }
    }

    /**
     * Add the terminated resources which sent from ImsService.
     */
    public void addTerminatedResource(List<RcsContactTerminatedReason> terminatedResource) {
        // Convert the RcsContactTerminatedReason to RcsContactUceCapability
        List<RcsContactUceCapability> capabilityList = terminatedResource.stream()
                .filter(Objects::nonNull)
                .map(reason -> PidfParserUtils.getTerminatedCapability(
                        reason.getContactUri(), reason.getReason())).collect(Collectors.toList());

        synchronized (mTerminatedResource) {
            mTerminatedResource.addAll(capabilityList);
        }
    }

    /**
     * Get the terminated resources which sent from ImsService.
     */
    public List<RcsContactUceCapability> getTerminatedResources() {
        synchronized (mTerminatedResource) {
            return Collections.unmodifiableList(mTerminatedResource);
        }
    }

    // Remove the given capabilities from the mTerminatedResource when these capabilities have
    // updated to the requester.
    private void removeTerminatedResources(List<RcsContactUceCapability> terminatedResourceList) {
        synchronized (mTerminatedResource) {
            mTerminatedResource.removeAll(terminatedResourceList);
        }
    }

    /**
     * Set the remote's capabilities which are sent from the network.
     */
    public void setRemoteCapabilities(Uri contact, Set<String> remoteCaps) {
        // Set the remote capabilities
        if (remoteCaps != null) {
            remoteCaps.stream().filter(Objects::nonNull).forEach(cap -> mRemoteCaps.add(cap));
        }

        RcsContactUceCapability.OptionsBuilder optionsBuilder
                = new RcsContactUceCapability.OptionsBuilder(contact);
        int requestResult = RcsContactUceCapability.REQUEST_RESULT_FOUND;
        if (!getNetworkRespSipCode().isPresent()) {
            requestResult = RcsContactUceCapability.REQUEST_RESULT_UNKNOWN;
        } else {
            switch (getNetworkRespSipCode().get()) {
                case NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT:
                    // Intentional fallthrough
                case NetworkSipCode.SIP_CODE_TEMPORARILY_UNAVAILABLE:
                    requestResult = RcsContactUceCapability.REQUEST_RESULT_NOT_ONLINE;
                    break;
                case NetworkSipCode.SIP_CODE_NOT_FOUND:
                    // Intentional fallthrough
                case NetworkSipCode.SIP_CODE_DOES_NOT_EXIST_ANYWHERE:
                    requestResult = RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND;
                    break;
            }
        }
        optionsBuilder.setRequestResult(requestResult);
        optionsBuilder.addFeatureTags(mRemoteCaps);

        // Add the remote's capabilities to the updated capability list
        addUpdatedCapabilities(Collections.singletonList(optionsBuilder.build()));
    }

    /**
     * Trigger the capabilities callback. This capabilities is retrieved from the cache.
     */
    public boolean triggerCachedCapabilitiesCallback(
            List<RcsContactUceCapability> cachedCapabilities) {
        // Return if there's no cached capabilities.
        if (cachedCapabilities == null || cachedCapabilities.isEmpty()) {
            return true;
        }

        Log.d(LOG_TAG, "triggerCachedCapabilitiesCallback: size=" + cachedCapabilities.size());

        try {
            mCapabilitiesCallback.onCapabilitiesReceived(
                    Collections.unmodifiableList(cachedCapabilities));
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCachedCapabilitiesCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the capabilities updated callback and remove the given capability from the
     * capability updated list.
     */
    public boolean triggerCapabilitiesCallback(List<RcsContactUceCapability> capabilityList) {
        try {
            mCapabilitiesCallback.onCapabilitiesReceived(capabilityList);
            // Remove the elements that have executed the callback onCapabilitiesReceived.
            removeCapabilities(capabilityList);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCapabilitiesCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the capabilities updated callback and remove the given capability from the resource
     * terminated list.
     */
    public boolean triggerResourceTerminatedCallback(List<RcsContactUceCapability> capList) {
        try {
            mCapabilitiesCallback.onCapabilitiesReceived(capList);
            // Remove the elements that have executed the callback onCapabilitiesReceived.
            removeTerminatedResources(capList);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerTerminatedResourceCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the onComplete callback to notify the request is completed.
     */
    public void triggerCompletedCallback() {
        try {
            mCapabilitiesCallback.onComplete();
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCompletedCallback exception: " + e);
        }
    }

    /**
     * Trigger the onError callback to notify the request is failed.
     */
    public void triggerErrorCallback() {
        try {
            mCapabilitiesCallback.onError(mErrorCode.get(), mRetryAfterMillis.orElse(0L));
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerErrorCallback exception: " + e);
        }
    }

    /**
     * This method convert from the command error code which are defined in the
     * RcsCapabilityExchangeImplBase to the Capabilities error code which are defined in the
     * RcsUceAdapter.
     */
    public static int convertCommandErrorToCapabilityError(@CommandCode int cmdError) {
        int uceError;
        switch (cmdError) {
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNKNOWN:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INVALID_PARAM:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_FETCH_ERROR:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NO_CHANGE:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_FOUND:
                uceError = RcsUceAdapter.ERROR_NOT_FOUND;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT:
                uceError = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY:
                uceError = RcsUceAdapter.ERROR_INSUFFICIENT_MEMORY;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION:
                uceError = RcsUceAdapter.ERROR_LOST_NETWORK;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE:
                uceError = RcsUceAdapter.ERROR_SERVER_UNAVAILABLE;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    /**
     * Convert the SIP error code which sent by ImsService to the capability error code.
     */
    public int getCapabilityErrorFromSipError() {
        int sipError;
        String respReason;
        // Check the sip code in the Reason header first if the Reason Header is present.
        if (mReasonHeaderCause.isPresent()) {
            sipError = mReasonHeaderCause.get();
            respReason = mReasonHeaderText.orElse("");
        } else {
            sipError = mNetworkRespSipCode.orElse(-1);
            respReason = mReasonPhrase.orElse("");
        }
        int uceError;
        switch (sipError) {
            case NetworkSipCode.SIP_CODE_FORBIDDEN:   // 403
                if (NetworkSipCode.SIP_NOT_REGISTERED.equalsIgnoreCase(respReason)) {
                    // Not registered with IMS. Device shall register to IMS.
                    uceError = RcsUceAdapter.ERROR_NOT_REGISTERED;
                } else if (NetworkSipCode.SIP_NOT_AUTHORIZED_FOR_PRESENCE.equalsIgnoreCase(
                        respReason)) {
                    // Not provisioned for EAB. Device shall not retry.
                    uceError = RcsUceAdapter.ERROR_NOT_AUTHORIZED;
                } else {
                    // The network has responded SIP 403 error with no reason.
                    uceError = RcsUceAdapter.ERROR_FORBIDDEN;
                }
                break;
            case NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT:        // 408
                uceError = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
                break;
            case NetworkSipCode.SIP_CODE_INTERVAL_TOO_BRIEF:     // 423
                // Rejected by the network because the requested expiry interval is too short.
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case NetworkSipCode.SIP_CODE_SERVER_INTERNAL_ERROR:  // 500
            case NetworkSipCode.SIP_CODE_SERVICE_UNAVAILABLE:    // 503
                // The network is temporarily unavailable or busy.
                uceError = RcsUceAdapter.ERROR_SERVER_UNAVAILABLE;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        return builder.append("ErrorCode=").append(mErrorCode.orElse(-1))
                .append(", CommandErrorCode=").append(mCommandError.orElse(-1))
                .append(", NetworkResponseCode=").append(mNetworkRespSipCode.orElse(-1))
                .append(", NetworkResponseReason=").append(mReasonPhrase.orElse(""))
                .append(", ReasonHeaderCause=").append(mReasonHeaderCause.orElse(-1))
                .append(", ReasonHeaderText=").append(mReasonHeaderText.orElse(""))
                .append(", TerminatedReason=").append(mTerminatedReason.orElse(""))
                .append(", RetryAfterMillis=").append(mRetryAfterMillis.orElse(0L))
                .append(", RemoteCaps size=" + mRemoteCaps.size())
                .append(", Updated capability size=" + mUpdatedCapabilityList.size())
                .append(", Terminated resource size=" + mTerminatedResource.size())
                .toString();
    }
}
