/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.ims;

import android.annotation.NonNull;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IRcsFeatureListener;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * A container of the IImsServiceController binder, which implements all of the RcsFeatures that
 * the platform currently supports: RCS
 */
public class RcsFeatureConnection extends FeatureConnection {
    private static final String TAG = "RcsFeatureConnection";

    public interface IRcsFeatureUpdate extends IFeatureUpdate {
      /**
       * Called when the ImsFeature has been created.
       */
       void notifyFeatureCreated();
    }

    public static @NonNull RcsFeatureConnection create(Context context , int slotId,
            IFeatureUpdate callback) {
        RcsFeatureConnection serviceProxy = new RcsFeatureConnection(context, slotId, callback);
        if (!ImsManager.isImsSupportedOnDevice(context)) {
            // Return empty service proxy in the case that IMS is not supported.
            sImsSupportedOnDevice = false;
            return serviceProxy;
        }

        if (!sRcsFeatureManagerProxy.isRcsUceSupportedByCarrier(context, slotId)) {
            // Return empty service proxy in the case that RCS feature is not supported.
            Rlog.w(TAG, "create: RCS UCE feature is not supported");
            return serviceProxy;
        }

        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            Rlog.w(TAG, "create: TelephonyManager is null");
            return serviceProxy;
        }

        IImsRcsFeature binder = tm.getImsRcsFeatureAndListen(slotId, serviceProxy.getListener());
        if (binder != null) {
            serviceProxy.setBinder(binder.asBinder());
            // Trigger the cache to be updated for feature status.
            serviceProxy.getFeatureState();
        } else {
            Rlog.w(TAG, "create: binder is null! Slot Id: " + slotId);
        }

        Rlog.d(TAG, "create: RcsFeatureConnection");
        return serviceProxy;
    }

    @VisibleForTesting
    public IRcsFeatureUpdate mRcsFeatureStatusCallback;

    private RcsFeatureConnection(Context context, int slotId, IFeatureUpdate callback) {
        super(context, slotId, ImsFeature.FEATURE_RCS);
        setStatusCallback(callback);
    }

    @Override
    public void setStatusCallback(IFeatureUpdate callback) {
        super.setStatusCallback(callback);
        mRcsFeatureStatusCallback = (IRcsFeatureUpdate) mStatusCallback;
    }

    /**
     * Testing interface used to mock RcsFeatureManager in testing
     * @hide
     */
    @VisibleForTesting
    public interface RcsFeatureManagerProxy {
        /**
         * Mock-able interface for
         * {@link RcsFeatureManager#isRcsUceSupportedByCarrier(Context, int)} used for testing.
         */
        boolean isRcsUceSupportedByCarrier(Context context, int slotId);
    }

    private static RcsFeatureManagerProxy sRcsFeatureManagerProxy = new RcsFeatureManagerProxy() {
        @Override
        public boolean isRcsUceSupportedByCarrier(Context context, int slotId) {
            return RcsFeatureManager.isRcsUceSupportedByCarrier(context, slotId);
        }
    };

    /**
     * Testing function used to mock RcsFeatureManager in testing
     * @hide
     */
    @VisibleForTesting
    public static void setRcsFeatureManagerProxy(RcsFeatureManagerProxy proxy) {
        sRcsFeatureManagerProxy = proxy;
    }

    @Override
    @VisibleForTesting
    public void handleImsFeatureCreatedCallback(int slotId, int feature) {
        Log.i(TAG, "IMS feature created: slotId= " + slotId + ", feature=" + feature);
        if (!isUpdateForThisFeatureAndSlot(slotId, feature)) {
            return;
        }
        synchronized(mLock) {
            if (!mIsAvailable) {
                Log.i(TAG, "RCS enabled on slotId: " + slotId);
                mIsAvailable = true;
            }
            // Notify RcsFeatureManager that RCS feature has already been created
            if (mRcsFeatureStatusCallback != null) {
              mRcsFeatureStatusCallback.notifyFeatureCreated();
            }
        }
    }

    @Override
    @VisibleForTesting
    public void handleImsFeatureRemovedCallback(int slotId, int feature) {
        Log.i(TAG, "IMS feature removed: slotId= " + slotId + ", feature=" + feature);
        if (!isUpdateForThisFeatureAndSlot(slotId, feature)) {
            return;
        }
        synchronized(mLock) {
            Log.i(TAG, "Rcs UCE removed on slotId: " + slotId);
            onRemovedOrDied();
        }
    }

    @Override
    @VisibleForTesting
    public void handleImsStatusChangedCallback(int slotId, int feature, int status) {
        Log.i(TAG, "IMS status changed: slotId=" + slotId
                + ", feature=" + feature + ", status=" + status);
        if (!isUpdateForThisFeatureAndSlot(slotId, feature)) {
            return;
        }
        synchronized(mLock) {
            mFeatureStateCached = status;
            // notify RCS feature status changed
            if (mRcsFeatureStatusCallback != null) {
                mRcsFeatureStatusCallback.notifyStateChanged();
            }
        }
    }

    private boolean isUpdateForThisFeatureAndSlot(int slotId, int feature) {
        if (mSlotId == slotId && feature == ImsFeature.FEATURE_RCS) {
            return true;
        }
        return false;
    }

    public void setRcsFeatureListener(IRcsFeatureListener listener) throws RemoteException {
        checkServiceIsReady();
        getServiceInterface(mBinder).setListener(listener);
    }

    public void changeEnabledCapabilities(CapabilityChangeRequest request,
        IImsCapabilityCallback callback) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).changeCapabilitiesConfiguration(request, callback);
        }
    }

    @Override
    @VisibleForTesting
    public void checkServiceIsReady() throws RemoteException {
        super.checkServiceIsReady();
        if (!sRcsFeatureManagerProxy.isRcsUceSupportedByCarrier(mContext, mSlotId)) {
            throw new RemoteException("RCS UCE feature is not supported");
        }
    }

    @Override
    @VisibleForTesting
    public Integer retrieveFeatureState() {
        if (mBinder != null) {
            try {
                return getServiceInterface(mBinder).getFeatureState();
            } catch (RemoteException e) {
                // Status check failed, don't update cache
            }
        }
        return null;
    }

    private IImsRcsFeature getServiceInterface(IBinder b) {
        return IImsRcsFeature.Stub.asInterface(b);
    }
}
