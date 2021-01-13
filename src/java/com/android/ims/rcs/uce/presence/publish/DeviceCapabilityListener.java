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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsMmTelManager.CapabilityCallback;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.util.Log;

import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.util.HandlerExecutor;

/**
 * Listen to the device changes and notify the PublishController to publish the device's
 * capabilities to the Presence server.
 */
public class DeviceCapabilityListener {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "DeviceCapListener";

    // Delay to send the registered changed because the registered state changed of MMTEL and RCS
    // may be called at the same time.
    private static final long DELAY_SEND_IMS_REGISTERED_CHANGED_MSG = 500L;

    /**
     * Used to inject ImsMmTelManager instances for testing.
     */
    @VisibleForTesting
    public interface ImsMmTelManagerFactory {
        ImsMmTelManager getImsMmTelManager(int subId);
    }

    /**
     * Used to inject ImsRcsManager instances for testing.
     */
    @VisibleForTesting
    public interface ImsRcsManagerFactory {
        ImsRcsManager getImsRcsManager(int subId);
    }

    /**
     * Used to inject ProvisioningManager instances for testing.
     */
    @VisibleForTesting
    public interface ProvisioningManagerFactory {
        ProvisioningManager getProvisioningManager(int subId);
    }

    // The handler to re-register ims provision callback.
    private class RegisterCallbackHandler extends Handler {
        private static final int EVENT_REGISTER_IMS_CONTENT_CHANGE = 1;
        private static final long REGISTER_IMS_CHANGED_DELAY = 5000L;  // 5 seconds

        RegisterCallbackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            logd("handleMessage: " + msg.what);
            switch (msg.what) {
                case EVENT_REGISTER_IMS_CONTENT_CHANGE:
                    registerImsProvisionCallback();
                    break;
            }
        }

        public void sendRegisterImsContentChangedMessage() {
            // Remove the existing message and send a new one with the delayed time.
            removeMessages(EVENT_REGISTER_IMS_CONTENT_CHANGE);
            Message msg = obtainMessage(EVENT_REGISTER_IMS_CONTENT_CHANGE);
            sendMessageDelayed(msg, REGISTER_IMS_CHANGED_DELAY);
        }

        public void removeRegisterImsContentChangedMessage() {
            removeMessages(EVENT_REGISTER_IMS_CONTENT_CHANGE);
        }
    }

    private final int mSubId;
    private final Context mContext;
    private volatile boolean mInitialized;

    // The listener is destroyed
    private volatile boolean mIsDestroyed;

    // The callback to trigger the internal publish request
    private final PublishControllerCallback mCallback;
    private final DeviceCapabilityInfo mCapabilityInfo;
    private final RegisterCallbackHandler mHandler;
    private final HandlerExecutor mHandlerExecutor;

    private ImsMmTelManager mImsMmTelManager;
    private ImsMmTelManagerFactory mImsMmTelManagerFactory = (subId) -> getImsMmTelManager(subId);

    private ImsRcsManager mImsRcsManager;
    private ImsRcsManagerFactory mImsRcsManagerFactory = (subId) -> getImsRcsManager(subId);

    private ProvisioningManager mProvisioningManager;
    private ProvisioningManagerFactory mProvisioningMgrFactory = (subId)
            -> ProvisioningManager.createForSubscriptionId(subId);

    private ContentObserver mMobileDataObserver = null;
    private ContentObserver mSimInfoContentObserver = null;

    private final Object mLock = new Object();

    public DeviceCapabilityListener(Context context, int subId, DeviceCapabilityInfo info,
            PublishControllerCallback callback, Looper looper) {
        mSubId = subId;
        logi("create");

        mContext = context;
        mCallback = callback;
        mCapabilityInfo = info;
        mInitialized = false;
        mHandler = new RegisterCallbackHandler(looper);
        mHandlerExecutor = new HandlerExecutor(mHandler);
    }

    /**
     * Turn on the device capabilities changed listener
     */
    public void initialize() {
        synchronized (mLock) {
            if (mIsDestroyed) {
                logw("initialize: This instance is already destroyed");
                return;
            }
            if (mInitialized) return;

            logi("initialize");
            mImsMmTelManager = mImsMmTelManagerFactory.getImsMmTelManager(mSubId);
            mImsRcsManager = mImsRcsManagerFactory.getImsRcsManager(mSubId);
            mProvisioningManager = mProvisioningMgrFactory.getProvisioningManager(mSubId);
            registerReceivers();
            registerImsProvisionCallback();

            mInitialized = true;
        }
    }

    /**
     * Notify the instance is destroyed
     */
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyed = true;
        synchronized (mLock) {
            if (!mInitialized) return;
            logi("turnOffListener");
            mInitialized = false;
            unregisterReceivers();
            unregisterImsProvisionCallback();
        }
    }

    /*
     * Register receivers to listen to the data changes.
     */
    private void registerReceivers() {
        logd("registerReceivers");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        ContentResolver resolver = mContext.getContentResolver();
        if (resolver != null) {
            // Listen to the mobile data content changed.
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA), false,
                    getMobileDataObserver());
            // Listen to the SIM info content changed.
            resolver.registerContentObserver(Telephony.SimInfo.CONTENT_URI, false,
                    getSimInfoContentObserver());
        }
    }

    private void unregisterReceivers() {
        logd("unregisterReceivers");
        mContext.unregisterReceiver(mReceiver);
        ContentResolver resolver = mContext.getContentResolver();
        if (resolver != null) {
            resolver.unregisterContentObserver(getMobileDataObserver());
            resolver.unregisterContentObserver(getSimInfoContentObserver());
        }
    }

    private void registerImsProvisionCallback() {
        logd("registerImsProvisionCallback");
        try {
            // Register mmtel callback
            if (mImsMmTelManager != null) {
                mImsMmTelManager.registerImsRegistrationCallback(mHandlerExecutor,
                        mMmtelRegistrationCallback);
                mImsMmTelManager.registerMmTelCapabilityCallback(mHandlerExecutor,
                        mMmtelCapabilityCallback);
            }

            // Register rcs callback
            if (mImsRcsManager != null) {
                mImsRcsManager.registerImsRegistrationCallback(mHandlerExecutor,
                        mRcsRegistrationCallback);
            }

            // Register provisioning changed callback
            mProvisioningManager.registerProvisioningChangedCallback(mHandlerExecutor,
                    mProvisionChangedCallback);
        } catch (ImsException e) {
            logw("registerImsProvisionCallback error: " + e);
            // Unregister the callback
            unregisterImsProvisionCallback();
            // Retry registering IMS content change callback
            mHandler.sendRegisterImsContentChangedMessage();
        }
    }

    private void unregisterImsProvisionCallback() {
        logd("unregisterImsProvisionCallback");

        // Clear the registering IMS callback message from the handler thread
        mHandler.removeRegisterImsContentChangedMessage();

        // Unregister mmtel callback
        if (mImsMmTelManager != null) {
            try {
                mImsMmTelManager.unregisterImsRegistrationCallback(mMmtelRegistrationCallback);
            } catch (RuntimeException e) {
                logw("unregister MMTel registration error: " + e.getMessage());
            }
            try {
                mImsMmTelManager.unregisterMmTelCapabilityCallback(mMmtelCapabilityCallback);
            } catch (RuntimeException e) {
                logw("unregister MMTel capability error: " + e.getMessage());
            }
        }

        // Unregister rcs callback
        if (mImsRcsManager != null) {
            try {
                mImsRcsManager.unregisterImsRegistrationCallback(mRcsRegistrationCallback);
            } catch (RuntimeException e) {
                logw("unregister rcs capability error: " + e.getMessage());
            }
        }

        try {
            // Unregister provisioning changed callback
            mProvisioningManager.unregisterProvisioningChangedCallback(mProvisionChangedCallback);
        } catch (RuntimeException e) {
            logw("unregister provisioning callback error: " + e.getMessage());
        }
    }

    @VisibleForTesting
    public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED:
                    int preferredMode = intent.getIntExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                            TelecomManager.TTY_MODE_OFF);
                    handleTtyPreferredModeChanged(preferredMode);
                    break;

                case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                    boolean airplaneMode = intent.getBooleanExtra("state", false);
                    handleAirplaneModeChanged(airplaneMode);
                    break;
            }
        }
    };

    private ContentObserver getMobileDataObserver() {
        synchronized (mLock) {
            if (mMobileDataObserver == null) {
                mMobileDataObserver = new ContentObserver(new Handler(mHandler.getLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean isEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                                Settings.Global.MOBILE_DATA, 1) == 1;
                        handleMobileDataChanged(isEnabled);
                    }
                };
            }
            return mMobileDataObserver;
        }
    }

    private ContentObserver getSimInfoContentObserver() {
        synchronized (mLock) {
            if (mSimInfoContentObserver == null) {
                mSimInfoContentObserver = new ContentObserver(new Handler(mHandler.getLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        if (mImsMmTelManager == null) {
                            logw("SimInfo change error: MmTelManager is null");
                            return;
                        }

                        try {
                            boolean isEnabled = mImsMmTelManager.isVtSettingEnabled();
                            handleVtSettingChanged(isEnabled);
                        } catch (RuntimeException e) {
                            logw("SimInfo change error: " + e);
                        }
                    }
                };
            }
            return mSimInfoContentObserver;
        }
    }

    private ImsMmTelManager getImsMmTelManager(int subId) {
        try {
            ImsManager imsManager = mContext.getSystemService(
                    android.telephony.ims.ImsManager.class);
            return (imsManager == null) ? null : imsManager.getImsMmTelManager(subId);
        } catch (IllegalArgumentException e) {
            logw("getImsMmTelManager error: " + e.getMessage());
            return null;
        }
    }

    private ImsRcsManager getImsRcsManager(int subId) {
        try {
            ImsManager imsManager = mContext.getSystemService(
                    android.telephony.ims.ImsManager.class);
            return (imsManager == null) ? null : imsManager.getImsRcsManager(subId);
        } catch (IllegalArgumentException e) {
            logw("getImsRcsManager error: " + e.getMessage());
            return null;
        }
    }

    @VisibleForTesting
    public final RegistrationManager.RegistrationCallback mRcsRegistrationCallback =
            new RegistrationManager.RegistrationCallback() {
                @Override
                public void onRegistered(int imsTransportType) {
                    synchronized (mLock) {
                        logi("onRcsRegistered: " + imsTransportType);
                        handleImsRcsRegistered(imsTransportType);
                    }
                }

                @Override
                public void onUnregistered(ImsReasonInfo info) {
                    synchronized (mLock) {
                        logi("onRcsUnregistered: " + info);
                        handleImsRcsUnregistered();
                    }
                }
    };

    @VisibleForTesting
    public final RegistrationManager.RegistrationCallback mMmtelRegistrationCallback =
            new RegistrationManager.RegistrationCallback() {
                @Override
                public void onRegistered(int imsTransportType) {
                    synchronized (mLock) {
                        logi("onMmTelRegistered: " + imsTransportType);
                        handleImsMmtelRegistered(imsTransportType);
                    }
                }

                @Override
                public void onUnregistered(ImsReasonInfo info) {
                    synchronized (mLock) {
                        logi("onMmTelUnregistered: " + info);
                        handleImsMmtelUnregistered();
                    }
                }
            };

    @VisibleForTesting
    public final ImsMmTelManager.CapabilityCallback mMmtelCapabilityCallback =
            new CapabilityCallback() {
                @Override
                public void onCapabilitiesStatusChanged(MmTelCapabilities capabilities) {
                    if (capabilities == null) {
                        logw("onCapabilitiesStatusChanged: parameter is null");
                        return;
                    }
                    synchronized (mLock) {
                        handleMmtelCapabilitiesStatusChanged(capabilities);
                    }
                }
            };

    @VisibleForTesting
    public final ProvisioningManager.Callback mProvisionChangedCallback =
            new ProvisioningManager.Callback() {
                @Override
                public void onProvisioningIntChanged(int item, int value) {
                    logi("onProvisioningIntChanged: item=" + item + ", value=" + value);
                    switch (item) {
                        case ProvisioningManager.KEY_EAB_PROVISIONING_STATUS:
                        case ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS:
                        case ProvisioningManager.KEY_VT_PROVISIONING_STATUS:
                            handleProvisioningChanged();
                            break;
                    }
                }
            };

    private void handleTtyPreferredModeChanged(int preferredMode) {
        logi("TTY preferred mode changed: " + preferredMode);
        boolean isChanged = mCapabilityInfo.updateTtyPreferredMode(preferredMode);
        if (isChanged) {
            mCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_TTY_PREFERRED_CHANGE, 0L);
        }
    }

    private void handleAirplaneModeChanged(boolean state) {
        logi("Airplane mode changed: " + state);
        boolean isChanged = mCapabilityInfo.updateAirplaneMode(state);
        if (isChanged) {
            mCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_AIRPLANE_MODE_CHANGE, 0L);
        }
    }

    private void handleMobileDataChanged(boolean isEnabled) {
        logi("Mobile data changed: " + isEnabled);
        boolean isChanged = mCapabilityInfo.updateMobileData(isEnabled);
        if (isChanged) {
            mCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_MOBILE_DATA_CHANGE, 0L);
        }
    }

    private void handleVtSettingChanged(boolean isEnabled) {
        logi("VT setting changed: " + isEnabled);
        boolean isChanged = mCapabilityInfo.updateVtSetting(isEnabled);
        if (isChanged) {
            mCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_VT_SETTING_CHANGE, 0L);
        }
    }

    /*
     * This method is called when the MMTEL is registered.
     */
    private void handleImsMmtelRegistered(int imsTransportType) {
        mCapabilityInfo.updateImsMmtelRegistered(imsTransportType);
        mCallback.requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_MMTEL_REGISTERED,
                DELAY_SEND_IMS_REGISTERED_CHANGED_MSG);
    }

    /*
     * This method is called when the MMTEL is unregistered.
     */
    private void handleImsMmtelUnregistered() {
        mCapabilityInfo.updateImsMmtelUnregistered();
        mCallback.requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_MMTEL_UNREGISTERED, 0L);
    }

    private void handleMmtelCapabilitiesStatusChanged(MmTelCapabilities capabilities) {
        boolean isChanged = mCapabilityInfo.updateMmtelCapabilitiesChanged(capabilities);
        logi("MMTel capabilities status changed: isChanged=" + isChanged);
        if (isChanged) {
            mCallback.requestPublishFromInternal(
                    PublishController.PUBLISH_TRIGGER_MMTEL_CAPABILITY_CHANGE, 0L);
        }
    }

    /*
     * This method is called when the RCS is registered.
     */
    private void handleImsRcsRegistered(int imsTransportType) {
        mCapabilityInfo.updateImsRcsRegistered(imsTransportType);
        mCallback.requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_RCS_REGISTERED,
                DELAY_SEND_IMS_REGISTERED_CHANGED_MSG);
    }

    /*
     * This method is called when the RCS is unregistered.
     */
    private void handleImsRcsUnregistered() {
        mCapabilityInfo.updateImsRcsUnregistered();
        mCallback.requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_RCS_UNREGISTERED, 0L);
    }

    private void handleProvisioningChanged() {
        mCallback.requestPublishFromInternal(
                PublishController.PUBLISH_TRIGGER_PROVISIONING_CHANGE, 0L);
    }

    @VisibleForTesting
    public void setImsMmTelManagerFactory(ImsMmTelManagerFactory factory) {
        mImsMmTelManagerFactory = factory;
    }

    @VisibleForTesting
    public void setImsRcsManagerFactory(ImsRcsManagerFactory factory) {
        mImsRcsManagerFactory = factory;
    }

    @VisibleForTesting
    public void setProvisioningMgrFactory(ProvisioningManagerFactory factory) {
        mProvisioningMgrFactory = factory;
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
