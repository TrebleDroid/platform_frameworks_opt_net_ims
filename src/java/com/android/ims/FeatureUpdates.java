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

import android.os.IBinder;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;

import com.android.ims.internal.IImsServiceFeatureCallback;

/**
 * Interface used by Manager interfaces that will use a {@link FeatureConnector} to connect to
 * remote ImsFeature Binder interfaces.
 */
public interface FeatureUpdates {
    /**
     * Register a calback for the slot specified so that the FeatureConnector can notify its
     * listener of changes.
     * @param slotId The slot the callback is registered for.
     * @param cb The callback that the FeatureConnector will use to update its state and notify
     *           its callback of changes.
     * @param oneShot True if this callback should only be registered for one update (feature is
     *                available or not), false if this listener should be persistent.
     */
    void registerFeatureCallback(int slotId, IImsServiceFeatureCallback cb, boolean oneShot);

    /**
     * Unregister a previously registered callback due to the FeatureConnector disconnecting.
     * <p>
     * This does not need to be called if the callback was previously registered for a one
     * shot result.
     * @param cb The callback to unregister.
     */
    void unregisterFeatureCallback(IImsServiceFeatureCallback cb);

    /**
     * Associate this Manager instance with the IMS Binder interfaces specified. This is usually
     * done by creating a FeatureConnection instance with these interfaces.
     */
    void associate(IBinder feature, IImsConfig c, IImsRegistration r);

    /**
     * Invalidate the previously associated Binder interfaces set in {@link #associate}.
     */
    void invalidate();

    /**
     * Update the state of the remote ImsFeature associated with this Manager instance.
     */
    void updateFeatureState(@ImsFeature.ImsState int state);

    /**
     * Update the capabilities of the remove ImsFeature associated with this Manager instance.
     */
    void updateFeatureCapabilities(@ImsService.ImsServiceCapability long capabilities);
}