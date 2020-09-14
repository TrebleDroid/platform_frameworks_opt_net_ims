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

package com.android.ims.rcs.uce;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.telephony.ims.ImsException;

import java.util.List;

/**
 * The adapter class of RcsCapabilityExchangeImplBase
 */
public class RcsCapabilityExchangeImplAdapter {

    /**  Service is unknown. */
    public static final int COMMAND_CODE_SERVICE_UNKNOWN = 0;
    /** The command failed with an unknown error. */
    public static final int COMMAND_CODE_GENERIC_FAILURE = 1;
    /**  Invalid parameter(s). */
    public static final int COMMAND_CODE_INVALID_PARAM = 2;
    /**  Fetch error. */
    public static final int COMMAND_CODE_FETCH_ERROR = 3;
    /**  Request timed out. */
    public static final int COMMAND_CODE_REQUEST_TIMEOUT = 4;
    /**  Failure due to insufficient memory available. */
    public static final int COMMAND_CODE_INSUFFICIENT_MEMORY = 5;
    /**  Network connection is lost. */
    public static final int COMMAND_CODE_LOST_NETWORK_CONNECTION = 6;
    /**  Requested feature/resource is not supported. */
    public static final int COMMAND_CODE_NOT_SUPPORTED = 7;
    /**  Contact or resource is not found. */
    public static final int COMMAND_CODE_NOT_FOUND = 8;
    /**  Service is not available. */
    public static final int COMMAND_CODE_SERVICE_UNAVAILABLE = 9;
    /** Command resulted in no change in state, ignoring. */
    public static final int COMMAND_CODE_NO_CHANGE = 10;

    /**
     * The adapter class of the interface PublishResponseCallback
     */
    public interface PublishResponseCallback {
        /**
         * Notify the framework that the command associated with this callback has failed.
         */
        void onCommandError(int code) throws ImsException;

        /**
         * Provide the framework with a subsequent network response update to
         * {@link #publishCapabilities(RcsContactUceCapability, int)}.
         */
        void onNetworkResponse(int code, @NonNull String reason) throws ImsException;
    }

    /**
     * The adapter class of the interface OptionsResponseCallback
     */
    public interface OptionsResponseCallback {
        /**
         * Notify the framework that the command associated with this callback has
         * failed.
         */
        void onCommandError(int code) throws ImsException;

        /**
         * Send the response of a SIP OPTIONS capability exchange to the framework.
         */
        void onNetworkResponse(int code, @NonNull String reason,
                @Nullable List<String> theirCaps) throws ImsException;
    }

    /**
     * The adapter class of the interface SubscribeResponseCallback
     */
    public interface SubscribeResponseCallback {
        /**
         * Notify the framework that the command associated with this callback has failed.
         */
        void onCommandError(int code) throws ImsException;

        /**
         * Notify the framework  of the response to the SUBSCRIBE request from
         * {@link #subscribeForCapabilities(RcsContactUceCapability, int)}.
         */
        void onNetworkResponse(int code, @NonNull String reason) throws ImsException;

        /**
         * Provides the framework with latest XML PIDF documents included in the
         * network response for the requested  contacts' capabilities requested by the
         * Framework  using {@link #requestCapabilities(List, int)}. This should be
         * called every time a new NOTIFY event is received with new capability
         * information.
         */
        void onNotifyCapabilitiesUpdate(@NonNull List<String> pidfXmls) throws ImsException;

        /**
         * The subscription associated with a previous #requestCapabilities operation
         * has been terminated. This will mostly be due to the subscription expiring,
         * but may also happen due to an error.
         */
        void onTerminated(String reason, String retryAfter) throws ImsException;
    }
}
