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

import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.IImsRegistrationCallback;
import android.telephony.ims.aidl.IRcsFeatureListener;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.telephony.ims.stub.RcsPresenceExchangeImplBase;
import com.android.telephony.Rlog;
import android.util.Log;

import com.android.ims.FeatureConnection.IFeatureUpdate;
import com.android.ims.RcsFeatureConnection.IRcsFeatureUpdate;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class RcsFeatureManager implements IFeatureConnector {
    private static final String TAG = "RcsFeatureManager";
    private static boolean DBG = true;

    private static final int CAPABILITY_OPTIONS = RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE;
    private static final int CAPABILITY_PRESENCE = RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE;

    private final int mSlotId;
    private final Context mContext;

    @VisibleForTesting
    public RcsFeatureConnection mRcsFeatureConnection;
    @VisibleForTesting
    public RcsCapabilityCallbackManager mCapabilityCallbackManager;
    @VisibleForTesting
    public ImsRegistrationCallbackAdapter mRegistrationCallbackManager;
    @VisibleForTesting
    public Set<IFeatureUpdate> mStatusCallbacks = new CopyOnWriteArraySet<>();

    public RcsFeatureManager(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
        logi("RcsFeatureManager");

        mCapabilityCallbackManager = new RcsCapabilityCallbackManager(context);
        mRegistrationCallbackManager = new ImsRegistrationCallbackAdapter(context);

        createImsService();
    }

    public void release() {
        logi("release");
        mStatusCallbacks.clear();
        mCapabilityCallbackManager.close();
        mRegistrationCallbackManager.close();
        mRcsFeatureConnection.close();
    }

    // Binds the IMS service to the RcsFeature instance.
    private void createImsService() {
        mRcsFeatureConnection = RcsFeatureConnection.create(mContext, mSlotId,
                new IRcsFeatureUpdate() {
                    @Override
                    public void notifyFeatureCreated() {
                        logi("RcsFeature is created");
                        setRcsFeatureListener();
                        updateCapabilities();
                    }
                    @Override
                    public void notifyStateChanged() {
                        mStatusCallbacks.forEach(
                            FeatureConnection.IFeatureUpdate::notifyStateChanged);
                    }
                    @Override
                    public void notifyUnavailable() {
                        logi("RcsFeature is unavailable");
                        mStatusCallbacks.forEach(
                            FeatureConnection.IFeatureUpdate::notifyUnavailable);
                    }
                });
    }

    /**
     * Set RcsFeature listener and it will also trigger onFeatureReady in RcsFeature.
     */
    private void setRcsFeatureListener() {
        if (DBG) log("Set RcsFeature listener");
        try {
            mRcsFeatureConnection.setRcsFeatureListener(mRcsFeatureListener);
        } catch (RemoteException e) {
            loge("setRcsFeatureListener: ", e);
        }
    }

    /**
     * Update current UCE capabilities.
     */
    @VisibleForTesting
    public void updateCapabilities() {
        boolean optionsSupport = isOptionsSupported();
        boolean presenceSupported = isPresenceSupported();

        if (DBG) log("Update capabilities: options=" + optionsSupport
                + ", presence=" + presenceSupported);

        if (optionsSupport || presenceSupported) {
            if (optionsSupport) {
                enableRcsUceCapability(CAPABILITY_OPTIONS);
            }
            if (presenceSupported) {
                enableRcsUceCapability(CAPABILITY_PRESENCE);
            }
        } else {
            disableAllRcsUceCapabilities();
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
        public void onNotifyUpdateCapabilities(
                @RcsPresenceExchangeImplBase.StackPublishTriggerType int triggerType) {
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
     * A inner class to manager all the ImsRegistrationCallback associated with RcsFeature.
     */
    private class ImsRegistrationCallbackAdapter extends
            ImsCallbackAdapterManager<IImsRegistrationCallback> {

        public ImsRegistrationCallbackAdapter(Context context) {
            super(context, new Object() /* Lock object */, mSlotId);
        }

        @Override
        public void registerCallback(IImsRegistrationCallback localCallback) {
            if (DBG) log("Register IMS registration callback");

            IImsRegistration imsRegistration = getRegistration();
            if (imsRegistration == null) {
                loge("Register IMS registration callback: ImsRegistration is null");
                throw new IllegalStateException("ImsRegistrationCallbackAdapter: RcsFeature is"
                        + " not available!");
            }

            try {
                imsRegistration.addRegistrationCallback(localCallback);
            } catch (RemoteException e) {
                throw new IllegalStateException("ImsRegistrationCallbackAdapter: RcsFeature"
                        + " binder is dead.");
            }
        }

        @Override
        public void unregisterCallback(IImsRegistrationCallback localCallback) {
            if (DBG) log("Unregister IMS registration callback");

            IImsRegistration imsRegistration = getRegistration();
            if (imsRegistration == null) {
                log("Unregister IMS registration callback: ImsRegistration is null");
                return;
            }

            try {
                imsRegistration.removeRegistrationCallback(localCallback);
            } catch (RemoteException e) {
                loge("Cannot remove registration callback: " + e);
            }
        }

        private @Nullable IImsRegistration getRegistration() {
            if (mRcsFeatureConnection == null) {
                return null;
            }
            return mRcsFeatureConnection.getRegistration();
        }
    }

    /**
     * Add a {@link RegistrationManager.RegistrationCallback} callback that gets called when IMS
     * registration has changed for a specific subscription.
     */
    public void registerImsRegistrationCallback(IImsRegistrationCallback callback)
            throws ImsException {
        try {
            int subId = sSubscriptionManagerProxy.getSubId(mSlotId);
            mRegistrationCallbackManager.addCallbackForSubscription(callback, subId);
        } catch (IllegalStateException e) {
            loge("registerImsRegistrationCallback error: ", e);
            throw new ImsException(
                    "register registration callback", e, ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
    }

    /**
     * Removes a previously registered {@link RegistrationManager.RegistrationCallback} callback
     * that is associated with a specific subscription.
     */
    public void unregisterImsRegistrationCallback(IImsRegistrationCallback callback) {
        try {
            int subId = sSubscriptionManagerProxy.getSubId(mSlotId);
            mRegistrationCallbackManager.removeCallbackForSubscription(callback, subId);
        } catch (IllegalStateException e) {
            loge("unregisterImsRegistrationCallback error: ", e);
        }
    }

    /**
     * Get the IMS RCS registration technology for this Phone,
     * defined in {@link ImsRegistrationImplBase}.
     */
    public void getImsRegistrationTech(Consumer<Integer> callback) {
        try {
            int tech = mRcsFeatureConnection.getRegistrationTech();
            callback.accept(tech);
        } catch (RemoteException e) {
            loge("getImsRegistrationTech error: ", e);
            callback.accept(ImsRegistrationImplBase.REGISTRATION_TECH_NONE);
        }
    }

    /**
     * A inner class to manager all the ImsCapabilityCallbacks associated with RcsFeature.
     */
    @VisibleForTesting
    public class RcsCapabilityCallbackManager extends
            ImsCallbackAdapterManager<IImsCapabilityCallback> {

        RcsCapabilityCallbackManager(Context context) {
            super(context, new Object() /* Lock object */, mSlotId);
        }

        @Override
        public void registerCallback(IImsCapabilityCallback localCallback) {
            if (DBG) log("Register capability callback");
            try {
                mRcsFeatureConnection.addCapabilityCallback(localCallback);
            } catch (RemoteException e) {
                loge("Register capability callback error: " + e);
                throw new IllegalStateException(
                        " CapabilityCallbackManager: Register callback error");
            }
        }

        @Override
        public void unregisterCallback(IImsCapabilityCallback localCallback) {
            if (DBG) log("Unregister capability callback");
            try {
                mRcsFeatureConnection.removeCapabilityCallback(localCallback);
            } catch (RemoteException e) {
                loge("Cannot remove capability callback: " + e);
            }
        }
    }

    /**
     * Register an ImsCapabilityCallback with RCS service, which will provide RCS availability
     * updates.
     */
    public void registerRcsAvailabilityCallback(IImsCapabilityCallback callback)
            throws ImsException {
        try {
            int subId = sSubscriptionManagerProxy.getSubId(mSlotId);
            mCapabilityCallbackManager.addCallbackForSubscription(callback, subId);
        } catch (IllegalStateException e) {
            loge("registerRcsAvailabilityCallback: ", e);
            throw new ImsException(
                    "register capability callback", e, ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
    }

    /**
     * Remove an registered ImsCapabilityCallback from RCS service.
     */
    public void unregisterRcsAvailabilityCallback(IImsCapabilityCallback callback)
            throws ImsException {
        try {
            int subId = sSubscriptionManagerProxy.getSubId(mSlotId);
            mCapabilityCallbackManager.removeCallbackForSubscription(callback, subId);
        } catch (IllegalStateException e) {
            loge("unregisterRcsAvailabilityCallback: ", e);
            throw new ImsException(
                    "unregister capability callback", e, ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
    }

    /**
     * Query for the specific capability.
     */
    public boolean isCapable(
            @RcsImsCapabilities.RcsImsCapabilityFlag int capability,
            @ImsRegistrationImplBase.ImsRegistrationTech int radioTech) throws ImsException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> capableRef = new AtomicReference<>();

        IImsCapabilityCallback callback = new IImsCapabilityCallback.Stub() {
            @Override
            public void onQueryCapabilityConfiguration(
                    int resultCapability, int resultRadioTech, boolean enabled) {
                if ((capability != resultCapability) || (radioTech != resultRadioTech)) {
                    return;
                }
                if (DBG) log("capable result:capability=" + capability + ", enabled=" + enabled);
                capableRef.set(enabled);
                latch.countDown();
            }

            @Override
            public void onCapabilitiesStatusChanged(int config) {
                // Don't handle it
            }

            @Override
            public void onChangeCapabilityConfigurationError(int capability, int radioTech,
                    int reason) {
                // Don't handle it
            }
        };

        try {
            if (DBG) log("Query capability: " + capability + ", radioTech=" + radioTech);
            mRcsFeatureConnection.queryCapabilityConfiguration(capability, radioTech, callback);
            return awaitResult(latch, capableRef);
        } catch (RemoteException e) {
            loge("isCapable error: ", e);
            throw new ImsException("is capable", e, ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
    }

    private static <T> T awaitResult(CountDownLatch latch, AtomicReference<T> resultRef) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return resultRef.get();
    }

    /**
     * Query the availability of an IMS RCS capability.
     */
    public boolean isAvailable(@RcsImsCapabilities.RcsImsCapabilityFlag int capability)
            throws ImsException {
        try {
            int currentStatus = mRcsFeatureConnection.queryCapabilityStatus();
            return new RcsImsCapabilities(currentStatus).isCapable(capability);
        } catch (RemoteException e) {
            loge("isAvailable error: ", e);
            throw new ImsException("is RCS available", e, ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
    }

    /**
     * Adds a callback for status changed events if the binder is already available. If it is not,
     * this method will throw an ImsException.
     */
    @Override
    public void addNotifyStatusChangedCallbackIfAvailable(FeatureConnection.IFeatureUpdate c)
            throws ImsException {
        if (!mRcsFeatureConnection.isBinderAlive()) {
            throw new ImsException("Binder is not active!",
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        if (c != null) {
            mStatusCallbacks.add(c);
        }
    }

    @Override
    public void removeNotifyStatusChangedCallback(FeatureConnection.IFeatureUpdate c) {
        if (c != null) {
            mStatusCallbacks.remove(c);
        }
    }

    /**
     * Enable UCE capabilities with given type.
     * @param capability the specific RCS UCE capability wants to enable
     */
    public void enableRcsUceCapability(
            @RcsImsCapabilities.RcsImsCapabilityFlag int capability) {

        CapabilityChangeRequest request = new CapabilityChangeRequest();
        request.addCapabilitiesToEnableForTech(capability,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
        request.addCapabilitiesToEnableForTech(capability,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        try {
            if (DBG) log("enableRcsUceCapability: " + capability);
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            loge("enableRcsUceCapability: ", e);
        }
    }

    /**
     * Disable all of the UCE capabilities.
     */
    private void disableAllRcsUceCapabilities() {
        final int techLte = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
        final int techIWlan = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
        CapabilityChangeRequest request = new CapabilityChangeRequest();
        request.addCapabilitiesToDisableForTech(CAPABILITY_OPTIONS, techLte);
        request.addCapabilitiesToDisableForTech(CAPABILITY_OPTIONS, techIWlan);
        request.addCapabilitiesToDisableForTech(CAPABILITY_PRESENCE, techLte);
        request.addCapabilitiesToDisableForTech(CAPABILITY_PRESENCE, techIWlan);
        try {
            if (DBG) log("disableAllRcsUceCapabilities");
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            Log.e(TAG, "disableAllRcsUceCapabilities " + e);
        }
    }

    private boolean isOptionsSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId, CAPABILITY_OPTIONS);
    }

    private boolean isPresenceSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId, CAPABILITY_PRESENCE);
    }

    /**
     * Check if RCS UCE feature is supported by carrier.
     */
    public static boolean isRcsUceSupportedByCarrier(Context context, int slotId) {
        boolean isOptionsSupported = isCapabilityTypeSupported(
            context, slotId, CAPABILITY_OPTIONS);
        boolean isPresenceSupported = isCapabilityTypeSupported(
            context, slotId, CAPABILITY_PRESENCE);

        if (DBG) Log.d(TAG, "isRcsUceSupportedByCarrier: options=" + isOptionsSupported
                + ", presence=" + isPresenceSupported);

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
            Log.e(TAG, "isCapabilityTypeSupported: CarrierConfigManager is null, " + slotId);
            return false;
        }

        PersistableBundle b = configManager.getConfigForSubId(subId);
        if (b == null) {
            Log.e(TAG, "isCapabilityTypeSupported: PersistableBundle is null, " + slotId);
            return false;
        }

        if (capabilityType == CAPABILITY_OPTIONS) {
            return b.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false);
        } else if (capabilityType == CAPABILITY_PRESENCE) {
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

    private void log(String s) {
        Rlog.d(TAG + " [" + mSlotId + "]", s);
    }

    private void logi(String s) {
        Rlog.i(TAG + " [" + mSlotId + "]", s);
    }

    private void loge(String s) {
        Rlog.e(TAG + " [" + mSlotId + "]", s);
    }

    private void loge(String s, Throwable t) {
        Rlog.e(TAG + " [" + mSlotId + "]", s, t);
    }
}
