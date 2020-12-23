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

import android.content.Context;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.util.Log;

/**
 * Stores the device's capabilities information.
 */
public class DeviceCapabilityInfo {
    private static final String LOG_TAG = "DeviceCapabilityInfo";

    private final int mSubId;

    // The mmtel feature is registered or not
    private boolean mMmtelRegistered;

    // The network type which ims mmtel registers on.
    private int mMmtelNetworkRegType;

    // The rcs feature is registered or not
    private boolean mRcsRegistered;

    // The network type which ims rcs registers on.
    private int mRcsNetworkRegType;

    // The MMTel capabilities of this subscription Id
    private MmTelFeature.MmTelCapabilities mMmTelCapabilities;

    // Whether the settings are changed or not
    private int mTtyPreferredMode;
    private boolean mAirplaneMode;
    private boolean mMobileData;
    private boolean mVtSetting;

    public DeviceCapabilityInfo(int subId) {
        mSubId = subId;
        reset();
    }

    /**
     * Reset all the status.
     */
    public synchronized void reset() {
        logd("reset");
        mMmtelRegistered = false;
        mMmtelNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        mRcsRegistered = false;
        mRcsNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        mTtyPreferredMode = TelecomManager.TTY_MODE_OFF;
        mAirplaneMode = false;
        mMobileData = true;
        mVtSetting = true;
        mMmTelCapabilities = new MmTelCapabilities();
    }

    public synchronized boolean isImsRegistered() {
        return mMmtelRegistered;
    }

    /**
     * Update the status that IMS MMTEL is registered.
     */
    public synchronized void updateImsMmtelRegistered(int type) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMS MMTEL registered: original state=").append(mMmtelRegistered)
                .append(", changes type from ").append(mMmtelNetworkRegType)
                .append(" to ").append(type);
        logi(builder.toString());

        if (!mMmtelRegistered) {
            mMmtelRegistered = true;
        }

        if (mMmtelNetworkRegType != type) {
            mMmtelNetworkRegType = type;
        }
    }

    /**
     * Update the status that IMS MMTEL is unregistered.
     */
    public synchronized void updateImsMmtelUnregistered() {
        logi("IMS MMTEL unregistered: original state=" + mMmtelRegistered);
        if (mMmtelRegistered) {
            mMmtelRegistered = false;
        }
        mMmtelNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Update the status that IMS RCS is registered.
     */
    public synchronized void updateImsRcsRegistered(int type) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMS RCS registered: original state=").append(mRcsRegistered)
                .append(", changes type from ").append(mRcsNetworkRegType)
                .append(" to ").append(type);
        logi(builder.toString());

        if (!mRcsRegistered) {
            mRcsRegistered = true;
        }

        if (mRcsNetworkRegType != type) {
            mRcsNetworkRegType = type;
        }
    }

    /**
     * Update the status that IMS RCS is unregistered.
     */
    public synchronized void updateImsRcsUnregistered() {
        logi("IMS RCS unregistered: original state=" + mRcsRegistered);
        if (mRcsRegistered) {
            mRcsRegistered = false;
        }
        mRcsNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Update the TTY preferred mode.
     * @return {@code true} if tty preferred mode is changed, {@code false} otherwise.
     */
    public synchronized boolean updateTtyPreferredMode(int ttyMode) {
        if (mTtyPreferredMode != ttyMode) {
            logd("TTY preferred mode changes from " + mTtyPreferredMode + " to " + ttyMode);
            mTtyPreferredMode = ttyMode;
            return true;
        }
        return false;
    }

    /**
     * Update airplane mode state.
     * @return {@code true} if the airplane mode is changed, {@code false} otherwise.
     */
    public synchronized boolean updateAirplaneMode(boolean state) {
        if (mAirplaneMode != state) {
            logd("Airplane mode changes from " + mAirplaneMode + " to " + state);
            mAirplaneMode = state;
            return true;
        }
        return false;
    }

    /**
     * Update mobile data setting.
     * @return {@code true} if the mobile data setting is changed, {@code false} otherwise.
     */
    public synchronized boolean updateMobileData(boolean mobileData) {
        if (mMobileData != mobileData) {
            logd("Mobile data changes from " + mMobileData + " to " + mobileData);
            mMobileData = mobileData;
            return true;
        }
        return false;
    }

    /**
     * Update VT setting.
     * @return {@code true} if vt setting is changed, {@code false}.otherwise.
     */
    public synchronized boolean updateVtSetting(boolean vtSetting) {
        if (mVtSetting != vtSetting) {
            logd("VT setting changes from " + mVtSetting + " to " + vtSetting);
            mVtSetting = vtSetting;
            return true;
        }
        return false;
    }

    /**
     * Update the MMTEL capabilities if the capabilities is changed.
     * @return {@code true} if the mmtel capabilities are changed, {@code false} otherwise.
     */
    public synchronized boolean updateMmtelCapabilitiesChanged(MmTelCapabilities capabilities) {
        if (capabilities == null) {
            return false;
        }
        boolean oldVolteAvailable = isVolteAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldVoWifiAvailable = isVoWifiAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldVtAvailable = isVtAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldViWifiAvailable = isViWifiAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldCallComposerAvailable = isCallComposerAvailable(mMmTelCapabilities);

        boolean volteAvailable = isVolteAvailable(mMmtelNetworkRegType, capabilities);
        boolean voWifiAvailable = isVoWifiAvailable(mMmtelNetworkRegType, capabilities);
        boolean vtAvailable = isVtAvailable(mMmtelNetworkRegType, capabilities);
        boolean viWifiAvailable = isViWifiAvailable(mMmtelNetworkRegType, capabilities);
        boolean callComposerAvailable = isCallComposerAvailable(capabilities);

        logd("updateMmtelCapabilitiesChanged: from " + mMmTelCapabilities + " to " + capabilities);

        // Update to the new mmtel capabilities
        mMmTelCapabilities = deepCopyCapabilities(capabilities);

        if (oldVolteAvailable != volteAvailable
                || oldVoWifiAvailable != voWifiAvailable
                || oldVtAvailable != vtAvailable
                || oldViWifiAvailable != viWifiAvailable
                || oldCallComposerAvailable != callComposerAvailable) {
            return true;
        }
        return false;
    }

    private boolean isVolteAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
    }

    private boolean isVoWifiAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
    }

    private boolean isVtAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
    }

    private boolean isViWifiAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
    }

    private boolean isCallComposerAvailable(MmTelCapabilities capabilities) {
        return capabilities.isCapable(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER);
    }

    /**
     * Get the device's capabilities.
     */
    public synchronized RcsContactUceCapability getDeviceCapabilities(Context context) {
        Uri uri = PublishUtils.getPublishingUri(context, mSubId);
        if (uri == null) {
            logw("getDeviceCapabilities: uri is empty");
            return null;
        }
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(
            hasVolteCapability(), hasVtCapability());
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tupleBuilder.addContactUri(uri).addServiceCapabilities(servCapsBuilder.build());

        RcsContactPresenceTuple.Builder callComposerTupleBuilder =
                new RcsContactPresenceTuple.Builder(
                        hasCallComposerCapability() ?
                                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN :
                                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_CLOSED,
                        RcsContactPresenceTuple.SERVICE_ID_CALL_COMPOSER, "1.0");
        callComposerTupleBuilder.addContactUri(uri).addServiceCapabilities(
                servCapsBuilder.build());

        PresenceBuilder presenceBuilder = new PresenceBuilder(uri,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());
        presenceBuilder.addCapabilityTuple(callComposerTupleBuilder.build());

        return presenceBuilder.build();
    }

    // Check if the device has the VoLTE capability
    private synchronized boolean hasVolteCapability() {
        if (mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE)) {
            return true;
        }
        return false;
    }

    // Check if the device has the VT capability
    private synchronized boolean hasVtCapability() {
        if (mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO)) {
            return true;
        }
        return false;
    }

    // Check if the device has the Call Composer capability
    private synchronized boolean hasCallComposerCapability() {
        if (mMmTelCapabilities != null && mMmTelCapabilities.isCapable(
                MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER)) {
            return true;
        }
        return false;
    }

    private synchronized MmTelCapabilities deepCopyCapabilities(MmTelCapabilities capabilities) {
        MmTelCapabilities mmTelCapabilities = new MmTelCapabilities();
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_UT)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_UT);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_SMS)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_SMS);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER);
        }
        return mmTelCapabilities;
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
}
