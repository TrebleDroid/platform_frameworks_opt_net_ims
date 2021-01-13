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

import android.util.Log;

import com.android.ims.rcs.uce.util.UceUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * The helper class to manage the publish request parameters.
 */
public class PublishProcessorState {

    private static final String LOG_TAG = UceUtils.getLogPrefix() + "PublishProcessorState";

    // The waiting period before the first retry.
    private static final int RETRY_BASE_PERIOD = 1;   // minute

    // The maximum number of the publication retries.
    private static final int PUBLISH_MAXIMUM_NUM_RETRIES = 4;

    private long mTaskId;
    private volatile boolean mIsPublishing;
    private volatile boolean mPendingRequest;

    private int mRetryCount;

    // The timestamp to allow to execute the publishing request.
    private Instant mAllowedTimestamp;

    private final Object mLock = new Object();

    public PublishProcessorState() {
    }

    // Generate a unique task Id for this request.
    public long generatePublishTaskId() {
        synchronized (mLock) {
            mTaskId = UceUtils.generateTaskId();
            return mTaskId;
        }
    }

    public long getCurrentTaskId() {
        synchronized (mLock) {
            return mTaskId;
        }
    }

    public void setPublishingFlag(boolean flag) {
        mIsPublishing = flag;
    }

    public boolean isPublishingNow() {
        return mIsPublishing;
    }

    public void setPendingRequest(boolean pendingRequest) {
        mPendingRequest = pendingRequest;
    }

    boolean hasPendingRequest() {
        return mPendingRequest;
    }

    // Check if it has reached the maximum retry count.
    public boolean isReachMaximumRetries() {
       synchronized (mLock) {
           return (mRetryCount >= PUBLISH_MAXIMUM_NUM_RETRIES) ? true : false;
       }
    }

    // Check if the current timestamp is allowed to request publish.
    public boolean isCurrentTimeAllowed() {
        synchronized (mLock) {
            Instant now = Instant.now();
            return (now.isAfter(mAllowedTimestamp)) ? true : false;
        }
    }

    // Get the delay time to allow to execute the publish request.
    public long getDelayTimeToAllowPublish() {
        synchronized (mLock) {
            // Setup the delay to the time which publish request is allowed to execute.
            long delayTime = ChronoUnit.MILLIS.between(Instant.now(), mAllowedTimestamp);
            if (delayTime < 0) {
                delayTime = 0L;
            }
            return delayTime;
        }
    }

    public void resetRetryCount() {
        synchronized (mLock) {
            mRetryCount = 0;
            // Adjust the allowed timestamp of the publishing request.
            adjustAllowedPublishTimestamp();
        }
    }

    public void increaseRetryCount() {
        synchronized (mLock) {
            if (mRetryCount < PUBLISH_MAXIMUM_NUM_RETRIES) {
                mRetryCount++;
            }
            adjustAllowedPublishTimestamp();
        }
    }

    // Adjust the timestamp to allow request PUBLISH with the specific delay time
    private void adjustAllowedPublishTimestamp() {
        synchronized (mLock) {
            Log.d(LOG_TAG, "adjustAllowedPublishTimestamp: retry=" + mRetryCount);
            if (mAllowedTimestamp == null) {
                // Now for the initialization.
                mAllowedTimestamp = Instant.now();
            } else {
                long nextRetryDuration = getNextRetryDuration();
                mAllowedTimestamp = Instant.now().plus(Duration.ofMillis(nextRetryDuration));
            }
            Log.d(LOG_TAG, "adjustAllowedPublishTimestamp: timestamp="
                    + mAllowedTimestamp.toString());
        }
    }

    // Return the milliseconds of the next retry delay.
    private long getNextRetryDuration() {
        synchronized (mLock) {
            // If the current retry count is zero, the duration is also zero.
            if (mRetryCount == 0) {
                return 0L;
            }

            // Next retry duration (minute)
            int power = mRetryCount - 1;
            Double retryDuration = RETRY_BASE_PERIOD * Math.pow(2, power);

            // Convert to millis
            return TimeUnit.MINUTES.toMillis(retryDuration.longValue());
        }
    }
}
