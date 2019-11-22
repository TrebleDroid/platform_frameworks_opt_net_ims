/*
 * Copyright (c) 2019 The Android Open Source Project
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

package com.android.ims;

import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IRcsFeatureListener;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.util.Log;

import com.android.ims.FeatureConnection.IFeatureUpdate;
import com.android.ims.RcsFeatureConnection.IRcsFeatureUpdate;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

public class RcsFeatureManager implements IFeatureConnector {
    private static final String TAG = "RcsFeatureManager";

    private final int mSlotId;
    private final Context mContext;
    @VisibleForTesting
    public RcsFeatureConnection mRcsFeatureConnection;
    @VisibleForTesting
    public Set<IFeatureUpdate> mStatusCallbacks = new CopyOnWriteArraySet<>();

    public RcsFeatureManager(Context context, int slotId) {
        Log.d(TAG, "RcsFeatureManager slotId: " + slotId);
        mContext = context;
        mSlotId = slotId;
        createImsService();
    }

    /**
     * Binds the IMS service to make/receive the call.
     */
    private void createImsService() {
        mRcsFeatureConnection = RcsFeatureConnection.create(mContext, mSlotId,
                new IRcsFeatureUpdate() {
                    @Override
                    public void notifyFeatureCreated() {
                        Log.d(TAG, "Feature created");
                        setRcsFeatureListener();
                        changeEnabledCapabilitiesAfterRcsFeatureCreated();
                    }
                    @Override
                    public void notifyStateChanged() {
                        mStatusCallbacks.forEach(
                            FeatureConnection.IFeatureUpdate::notifyStateChanged);
                    }
                    @Override
                    public void notifyUnavailable() {
                        mStatusCallbacks.forEach(
                            FeatureConnection.IFeatureUpdate::notifyUnavailable);
                    }
                });
    }

    /**
     * Set RcsFeature listener and it will also trigger onFeatureReady in RcsFeature.
     */
    private void setRcsFeatureListener() {
        try {
            mRcsFeatureConnection.setRcsFeatureListener(mRcsFeatureListener);
        } catch (RemoteException e) {
            Log.e(TAG, "setRcsFeatureListener " + e);
        }
    }

    /**
     * The callback to receive updated from RcsFeature
     */
    protected IRcsFeatureListener mRcsFeatureListener = new IRcsFeatureListener.Stub() {
        @Override
        public void onCommandUpdate(int commandCode, int operationToken) {
        }

        @Override
        public void onNetworkResponse(int code, String reason, int operationToken) {
        }

        @Override
        public void onCapabilityRequestResponsePresence(
                List<RcsContactUceCapability> infos, int operationToken) {
        }

        @Override
        public void onNotifyUpdateCapabilities() {
        }

        @Override
        public void onUnpublish() {
        }

        @Override
        public void onCapabilityRequestResponseOptions(
                int code, String reason, RcsContactUceCapability info, int operationToken) {
        }

        @Override
        public void onRemoteCapabilityRequest(
                Uri contactUri, RcsContactUceCapability remoteInfo, int operationToken) {
        }
    };

    /**
     * Adds a callback for status changed events if the binder is already available. If it is not,
     * this method will throw an ImsException.
     */
    @Override
    public void addNotifyStatusChangedCallbackIfAvailable(FeatureConnection.IFeatureUpdate c)
            throws ImsException {
        if (!mRcsFeatureConnection.isBinderAlive()) {
            int reason = ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN;
            if (!isRcsUceSupportedByCarrier(mContext, mSlotId)) {
                reason = ImsReasonInfo.CODE_LOCAL_IMS_NOT_SUPPORTED_ON_DEVICE;
            } else {
                throw new ImsException("Binder is not active!", reason);
            }
        }
        if (c != null) {
            mStatusCallbacks.add(c);
        }
    }

    @Override
    public void removeNotifyStatusChangedCallback(FeatureConnection.IFeatureUpdate c) {
        if (c != null) {
            mStatusCallbacks.remove(c);
        } else {
            Log.w(TAG, "removeNotifyStatusChangedCallback: callback is null!");
        }
    }

    /**
     * Enable/Disable UCE capabilities after RcsFeature has already been created.
     */
    @VisibleForTesting
    public void changeEnabledCapabilitiesAfterRcsFeatureCreated() {
        if (isOptionsSupported()) {
            enableRcsUceCapability(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
        } else if (isPresenceSupported()) {
            enableRcsUceCapability(RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);
        } else {
            disableRcsUceCapabilities();
        }
    }

    /**
     * Enable UCE capabilities with given type.
     * @param capabilityType the
     */
    public void enableRcsUceCapability(
            @RcsImsCapabilities.RcsImsCapabilityFlag int capabilityType) {

        CapabilityChangeRequest request = new CapabilityChangeRequest();
        request.addCapabilitiesToEnableForTech(capabilityType,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
        request.addCapabilitiesToEnableForTech(capabilityType,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        try {
            Log.i(TAG, "enableRcsUceCapability: " + capabilityType);
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            Log.e(TAG, "enableRcsUceCapability " + e);
        }
    }

    /**
     * Disable UCE capabilities.
     */
    public void disableRcsUceCapabilities() {
        final int techLte = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
        final int techIWlan = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
        CapabilityChangeRequest request = new CapabilityChangeRequest();
        request.addCapabilitiesToDisableForTech(
                RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE, techLte);
        request.addCapabilitiesToDisableForTech(
                RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE, techIWlan);
        request.addCapabilitiesToDisableForTech(
                RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE, techLte);
        request.addCapabilitiesToDisableForTech(
                RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE, techIWlan);
        try {
            Log.i(TAG, "disableRcsUceCapabilities");
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            Log.e(TAG, "disableRcsUceCapabilities " + e);
        }
    }

    private boolean isOptionsSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId,
            RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
    }

    private boolean isPresenceSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId,
            RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);
    }

    /**
     * Check if RCS UCE feature is supported by carrier.
     */
    public static boolean isRcsUceSupportedByCarrier(Context context, int slotId) {
        boolean isOptionsSupported = isCapabilityTypeSupported(
            context, slotId, RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
        boolean isPresenceSupported = isCapabilityTypeSupported(
            context, slotId, RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);

        Log.i(TAG, "isRcsUceSupported: options=" + isOptionsSupported
            + ", Presence=" + isPresenceSupported);

        return isOptionsSupported | isPresenceSupported;
    }

    /*
     * Check if the given type of capability is supported.
     */
    private static boolean isCapabilityTypeSupported(
        Context context, int slotId, int capabilityType) {
        int subId = sSubscriptionManagerProxy.getSubId(slotId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.e(TAG, "isCapabilityTypeSupported: Getting subIds is failure! slotId=" + slotId);
            return false;
        }

        CarrierConfigManager configManager =
            (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }

        PersistableBundle b = configManager.getConfigForSubId(subId);
        if (b == null) {
            return false;
        }

        if (capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE) {
            return b.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false);
        } else if (capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE) {
            return b.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, false);
        }
        return false;
    }

    @Override
    public int getImsServiceState() throws ImsException {
        return mRcsFeatureConnection.getFeatureState();
    }

    /**
     * Testing interface used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        /**
         * Mock-able interface for {@link SubscriptionManager#getSubId(int)} used for testing.
         */
        int getSubId(int slotId);
    }

    private static SubscriptionManagerProxy sSubscriptionManagerProxy
            = slotId -> {
                int[] subIds = SubscriptionManager.getSubId(slotId);
                if (subIds != null) {
                    Log.d(TAG, "getSubId : " + subIds[0]);
                    return subIds[0];
                }
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            };

    /**
     * Testing function used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public static void setSubscriptionManager(SubscriptionManagerProxy proxy) {
        sSubscriptionManagerProxy = proxy;
    }
}
