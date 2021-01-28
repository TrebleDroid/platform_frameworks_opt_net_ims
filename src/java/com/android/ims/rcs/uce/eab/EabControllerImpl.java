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

package com.android.ims.rcs.uce.eab;

import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS;
import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The implementation of EabController.
 */
public class EabControllerImpl implements EabController {
    private static final String TAG = "EabControllerImpl";

    // 90 days
    private static final int DEFAULT_CAPABILITY_CACHE_EXPIRATION_SEC = 90 * 24 * 60 * 60;
    private static final int DEFAULT_AVAILABILITY_CACHE_EXPIRATION_SEC = 60;

    private final Context mContext;
    private final int mSubId;
    private final EabBulkCapabilityUpdater mEabBulkCapabilityUpdater;

    private UceControllerCallback mUceControllerCallback;
    private volatile boolean mIsSetDestroyedFlag = false;

    public EabControllerImpl(Context context, int subId, UceControllerCallback c, Looper looper) {
        mContext = context;
        mSubId = subId;
        mUceControllerCallback = c;
        mEabBulkCapabilityUpdater = new EabBulkCapabilityUpdater(mContext, mSubId,
                this,
                new EabContactSyncController(),
                mUceControllerCallback,
                looper);
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
    }

    @Override
    public void onRcsDisconnected() {
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mIsSetDestroyedFlag = true;
        mEabBulkCapabilityUpdater.onDestroy();
    }

    /**
     * Set the callback for sending the request to UceController.
     */
    @Override
    public void setUceRequestCallback(UceControllerCallback c) {
        Objects.requireNonNull(c);
        if (mIsSetDestroyedFlag) {
            Log.d(TAG, "EabController destroyed.");
            return;
        }
        mUceControllerCallback = c;
        mEabBulkCapabilityUpdater.setUceRequestCallback(c);
    }

    /**
     * Retrieve the contacts' capabilities from the EAB database.
     */
    @Override
    public @NonNull List<EabCapabilityResult> getCapabilities(@NonNull List<Uri> uris) {
        Objects.requireNonNull(uris);
        if (mIsSetDestroyedFlag) {
            Log.d(TAG, "EabController destroyed.");
            return generateDestroyedResult(uris);
        }

        Log.d(TAG, "getCapabilities uri size=" + uris.size());
        List<EabCapabilityResult> capabilityResultList = new ArrayList();

        for (Uri uri : uris) {
            EabCapabilityResult result = generateEabResult(uri, this::isCapabilityExpired);
            capabilityResultList.add(result);
        }
        return capabilityResultList;
    }

    /**
     * Retrieve the contact's capabilities from the availability cache.
     */
    @Override
    public @NonNull EabCapabilityResult getAvailability(@NonNull Uri contactUri) {
        Objects.requireNonNull(contactUri);
        if (mIsSetDestroyedFlag) {
            Log.d(TAG, "EabController destroyed.");
            return new EabCapabilityResult(
                    contactUri,
                    EabCapabilityResult.EAB_CONTROLLER_DESTROYED_FAILURE,
                    null);
        }
        return generateEabResult(contactUri, this::isAvailabilityExpired);
    }

    /**
     * Update the availability catch and save the capabilities to the EAB database.
     */
    @Override
    public void saveCapabilities(@NonNull List<RcsContactUceCapability> contactCapabilities) {
        Objects.requireNonNull(contactCapabilities);
        if (mIsSetDestroyedFlag) {
            Log.d(TAG, "EabController destroyed.");
            return;
        }
        Log.d(TAG, "Save capabilities: " + contactCapabilities.size());

        // Update the capabilities
        for (RcsContactUceCapability capability : contactCapabilities) {
            String phoneNumber = getNumberFromUri(capability.getContactUri());
            Cursor c = mContext.getContentResolver().query(
                    EabProvider.CONTACT_URI, null,
                    EabProvider.ContactColumns.PHONE_NUMBER + "=?",
                    new String[]{phoneNumber}, null);

            if (c != null && c.moveToNext()) {
                int contactId = getIntValue(c, EabProvider.ContactColumns._ID);
                if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
                    Log.d(TAG, "Insert presence capability");
                    deleteOldPresenceCapability(contactId);
                    insertNewPresenceCapability(contactId, capability);
                } else if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_OPTIONS) {
                    Log.d(TAG, "Insert options capability");
                    deleteOldOptionCapability(contactId);
                    insertNewOptionCapability(contactId, capability);
                }
            } else {
                Log.e(TAG, "The phone number can't find in contact table. ");
                int contactId = insertNewContact(phoneNumber);
                if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
                    insertNewPresenceCapability(contactId, capability);
                } else if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_OPTIONS) {
                    insertNewOptionCapability(contactId, capability);
                }
            }

            if (c != null) {
                c.close();
            }
        }

        mEabBulkCapabilityUpdater.updateExpiredTimeAlert();
    }

    private List<EabCapabilityResult> generateDestroyedResult(List<Uri> contactUri) {
        List<EabCapabilityResult> destroyedResult = new ArrayList<>();
        for (Uri uri : contactUri) {
            destroyedResult.add(new EabCapabilityResult(
                    uri,
                    EabCapabilityResult.EAB_CONTROLLER_DESTROYED_FAILURE,
                    null));
        }
        return destroyedResult;
    }

    private EabCapabilityResult generateEabResult(Uri contactUri,
            Predicate<Cursor> isExpiredMethod) {
        RcsUceCapabilityBuilderWrapper builder = null;
        EabCapabilityResult result;

        // query EAB provider
        Uri queryUri = Uri.withAppendedPath(
                Uri.withAppendedPath(EabProvider.ALL_DATA_URI, String.valueOf(mSubId)),
                getNumberFromUri(contactUri));
        Cursor cursor = mContext.getContentResolver().query(
                queryUri, null, null, null, null);

        boolean isExpired = false;
        if (cursor != null && cursor.getCount() != 0) {
            while (cursor.moveToNext()) {
                if (builder == null) {
                    builder = createNewBuilder(contactUri, cursor);
                } else {
                    updateCapability(contactUri, cursor, builder);
                }
                if (isExpiredMethod.test(cursor)) {
                    isExpired = true;
                    break;
                }
            }
            cursor.close();

            if (isExpired) {
                result = new EabCapabilityResult(contactUri,
                        EabCapabilityResult.EAB_CONTACT_EXPIRED_FAILURE,
                        null);
            } else {
                if (builder.getMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
                    PresenceBuilder presenceBuilder = builder.getPresenceBuilder();
                    result = new EabCapabilityResult(contactUri,
                            EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                            presenceBuilder.build());
                } else {
                    OptionsBuilder optionsBuilder = builder.getOptionsBuilder();
                    result = new EabCapabilityResult(contactUri,
                            EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                            optionsBuilder.build());
                }

            }
        } else {
            result = new EabCapabilityResult(contactUri,
                    EabCapabilityResult.EAB_CONTACT_NOT_FOUND_FAILURE, null);
        }
        return result;
    }

    private void updateCapability(Uri contactUri, Cursor cursor,
                RcsUceCapabilityBuilderWrapper builderWrapper) {
        if (builderWrapper.getMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
            PresenceBuilder builder = builderWrapper.getPresenceBuilder();
            if (builder != null) {
                builder.addCapabilityTuple(createPresenceTuple(contactUri, cursor));
            }
        } else {
            OptionsBuilder builder = builderWrapper.getOptionsBuilder();
            if (builder != null) {
                builder.addFeatureTag(createOptionTuple(cursor));
            }
        }
    }

    private RcsUceCapabilityBuilderWrapper createNewBuilder(Uri contactUri, Cursor cursor) {
        int mechanism = getIntValue(cursor, EabProvider.EabCommonColumns.MECHANISM);
        int result = getIntValue(cursor, EabProvider.EabCommonColumns.REQUEST_RESULT);
        RcsUceCapabilityBuilderWrapper builderWrapper =
                new RcsUceCapabilityBuilderWrapper(mechanism);

        if (mechanism == CAPABILITY_MECHANISM_PRESENCE) {
            PresenceBuilder builder = new PresenceBuilder(
                    contactUri, CAPABILITY_MECHANISM_PRESENCE, result);
            builder.addCapabilityTuple(createPresenceTuple(contactUri, cursor));
            builderWrapper.setPresenceBuilder(builder);
        } else {
            builderWrapper.setOptionsBuilder(new OptionsBuilder(contactUri));
        }
        return builderWrapper;
    }

    private String createOptionTuple(Cursor cursor) {
        return getStringValue(cursor, EabProvider.OptionsColumns.FEATURE_TAG);
    }

    private RcsContactPresenceTuple createPresenceTuple(Uri contactUri, Cursor cursor) {
        // RcsContactPresenceTuple fields
        String status = getStringValue(cursor, EabProvider.PresenceTupleColumns.BASIC_STATUS);
        String serviceId = getStringValue(cursor, EabProvider.PresenceTupleColumns.SERVICE_ID);
        String version = getStringValue(cursor, EabProvider.PresenceTupleColumns.SERVICE_VERSION);
        String description = getStringValue(cursor, EabProvider.PresenceTupleColumns.DESCRIPTION);
        String timeStamp = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP);

        // ServiceCapabilities fields
        String audioCapableStr = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.AUDIO_CAPABLE);
        String videoCapableStr = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.VIDEO_CAPABLE);
        String duplexModes = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.DUPLEX_MODE);
        String unsupportedDuplexModes = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE);
        String[] duplexModeList, unsupportedDuplexModeList;

        if (!TextUtils.isEmpty(duplexModes)) {
            duplexModeList = duplexModes.split(",");
        } else {
            duplexModeList = new String[0];
        }
        if (!TextUtils.isEmpty(unsupportedDuplexModes)) {
            unsupportedDuplexModeList = unsupportedDuplexModes.split(",");
        } else {
            unsupportedDuplexModeList = new String[0];
        }

        // Create ServiceCapabilities
        ServiceCapabilities serviceCapabilities = null;
        if (!TextUtils.isEmpty(audioCapableStr)
                || !TextUtils.isEmpty(videoCapableStr)
                || !TextUtils.isEmpty(duplexModes)
                || !TextUtils.isEmpty(unsupportedDuplexModes)) {
            boolean audioCapable = Boolean.parseBoolean(audioCapableStr);
            boolean videoCapable = Boolean.parseBoolean(videoCapableStr);

            ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                    new ServiceCapabilities.Builder(audioCapable, videoCapable);
            for (String duplexMode : duplexModeList) {
                serviceCapabilitiesBuilder.addSupportedDuplexMode(duplexMode);
            }
            for (String unsupportedDuplex : unsupportedDuplexModeList) {
                serviceCapabilitiesBuilder.addUnsupportedDuplexMode(unsupportedDuplex);
            }
            serviceCapabilities = serviceCapabilitiesBuilder.build();
        }

        // Create RcsContactPresenceTuple
        RcsContactPresenceTuple.Builder rcsContactPresenceTupleBuilder =
                new RcsContactPresenceTuple.Builder(status, serviceId, version);
        if (description != null) {
            rcsContactPresenceTupleBuilder.setServiceDescription(description);
        }
        if (contactUri != null) {
            rcsContactPresenceTupleBuilder.setContactUri(contactUri);
        }
        if (serviceCapabilities != null) {
            rcsContactPresenceTupleBuilder.setServiceCapabilities(serviceCapabilities);
        }
        if (timeStamp != null) {
            rcsContactPresenceTupleBuilder.setTimestamp(timeStamp);
        }

        return rcsContactPresenceTupleBuilder.build();
    }

    private boolean isCapabilityExpired(Cursor cursor) {
        boolean expired = false;
        String requestTimeStamp = getRequestTimestamp(cursor);

        if (requestTimeStamp != null) {
            Instant expiredTimestamp = Instant
                    .ofEpochSecond(Long.parseLong(requestTimeStamp))
                    .plus(getCapabilityCacheExpiration(mSubId), ChronoUnit.SECONDS);
            expired = expiredTimestamp.isBefore(Instant.now());
            Log.d(TAG, "Capability expiredTimestamp: "
                    + expiredTimestamp.getEpochSecond() + ", expired:" + expired);
        } else {
            Log.d(TAG, "Capability requestTimeStamp is null");
        }
        return expired;
    }

    private boolean isAvailabilityExpired(Cursor cursor) {
        boolean expired = false;
        String requestTimeStamp = getRequestTimestamp(cursor);

        if (requestTimeStamp != null) {
            Instant expiredTimestamp = Instant
                    .ofEpochSecond(Long.parseLong(requestTimeStamp))
                    .plus(getAvailabilityCacheExpiration(mSubId), ChronoUnit.SECONDS);
            expired = expiredTimestamp.isBefore(Instant.now());
            Log.d(TAG, "Availability insertedTimestamp: "
                    + expiredTimestamp.getEpochSecond() + ", expired:" + expired);
        } else {
            Log.d(TAG, "Capability requestTimeStamp is null");
        }
        return expired;
    }

    private String getRequestTimestamp(Cursor cursor) {
        String expiredTimestamp = null;
        int mechanism = getIntValue(cursor, EabProvider.EabCommonColumns.MECHANISM);
        if (mechanism == CAPABILITY_MECHANISM_PRESENCE) {
            expiredTimestamp = getStringValue(cursor,
                    EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP);

        } else if (mechanism == CAPABILITY_MECHANISM_OPTIONS) {
            expiredTimestamp = getStringValue(cursor, EabProvider.OptionsColumns.REQUEST_TIMESTAMP);
        }
        return expiredTimestamp;
    }

    protected static long getCapabilityCacheExpiration(int subId) {
        long value = -1;
        try {
            ProvisioningManager pm = ProvisioningManager.createForSubscriptionId(subId);
            value = pm.getProvisioningIntValue(
                    ProvisioningManager.KEY_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC);
        } catch (Exception ex) {
            Log.e(TAG, "Exception in getCapabilityCacheExpiration(): " + ex);
        }

        if (value <= 0) {
            value = DEFAULT_CAPABILITY_CACHE_EXPIRATION_SEC;
            Log.e(TAG, "The capability expiration cannot be less than 0.");
        }
        return value;
    }

    protected static long getAvailabilityCacheExpiration(int subId) {
        long value = -1;
        try {
            ProvisioningManager pm = ProvisioningManager.createForSubscriptionId(subId);
            value = pm.getProvisioningIntValue(
                    ProvisioningManager.KEY_RCS_AVAILABILITY_CACHE_EXPIRATION_SEC);
        } catch (Exception ex) {
            Log.e(TAG, "Exception in getAvailabilityCacheExpiration(): " + ex);
        }

        if (value <= 0) {
            value = DEFAULT_AVAILABILITY_CACHE_EXPIRATION_SEC;
            Log.e(TAG, "The Availability expiration cannot be less than 0.");
        }
        return value;
    }

    private int insertNewContact(String phoneNumber) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.ContactColumns.PHONE_NUMBER, phoneNumber);
        Uri result = mContext.getContentResolver().insert(EabProvider.CONTACT_URI, contentValues);
        return Integer.valueOf(result.getLastPathSegment());
    }

    private void deleteOldPresenceCapability(int id) {
        Cursor c = mContext.getContentResolver().query(
                EabProvider.COMMON_URI,
                new String[]{EabProvider.EabCommonColumns._ID},
                EabProvider.EabCommonColumns.EAB_CONTACT_ID + "=?",
                new String[]{String.valueOf(id)}, null);

        if (c != null && c.getCount() > 0) {
            while(c.moveToNext()) {
                int commonId = c.getInt(c.getColumnIndex(EabProvider.EabCommonColumns._ID));
                mContext.getContentResolver().delete(
                        EabProvider.PRESENCE_URI,
                        EabProvider.PresenceTupleColumns.EAB_COMMON_ID + "=?",
                        new String[]{String.valueOf(commonId)});
            }
        }

        if (c != null) {
            c.close();
        }
    }

    private void insertNewPresenceCapability(int contactId, RcsContactUceCapability capability) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, contactId);
        contentValues.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        contentValues.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, mSubId);
        contentValues.put(EabProvider.EabCommonColumns.REQUEST_RESULT,
                capability.getRequestResult());
        Uri result = mContext.getContentResolver().insert(EabProvider.COMMON_URI, contentValues);
        int commonId = Integer.valueOf(result.getLastPathSegment());
        Log.d(TAG, "Insert into common table. Id: " + commonId);

        ContentValues[] presenceContent =
                new ContentValues[capability.getCapabilityTuples().size()];
        for (int i = 0; i < presenceContent.length; i++) {
            RcsContactPresenceTuple tuple = capability.getCapabilityTuples().get(i);

            // Create new ServiceCapabilities
            ServiceCapabilities serviceCapabilities = tuple.getServiceCapabilities();
            String duplexMode = null, unsupportedDuplexMode = null;
            if (serviceCapabilities != null) {
                List<String> duplexModes = serviceCapabilities.getSupportedDuplexModes();
                if (duplexModes != null && duplexModes.size() != 0) {
                    duplexMode = TextUtils.join(",", duplexModes);
                }

                List<String> unsupportedDuplexModes =
                        serviceCapabilities.getSupportedDuplexModes();
                if (unsupportedDuplexModes != null && unsupportedDuplexModes.size() != 0) {
                    unsupportedDuplexMode =
                            TextUtils.join(",", unsupportedDuplexModes);
                }
            }

            // Using the current timestamp if the timestamp doesn't populate
            String timeStamp = tuple.getTimestamp();
            if (timeStamp == null) {
                timeStamp = String.valueOf(Instant.now().getEpochSecond());
            }

            contentValues = new ContentValues();
            contentValues.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, commonId);
            contentValues.put(EabProvider.PresenceTupleColumns.BASIC_STATUS, tuple.getStatus());
            contentValues.put(EabProvider.PresenceTupleColumns.SERVICE_ID, tuple.getServiceId());
            contentValues.put(EabProvider.PresenceTupleColumns.SERVICE_VERSION,
                    tuple.getServiceVersion());
            contentValues.put(EabProvider.PresenceTupleColumns.DESCRIPTION,
                    tuple.getServiceDescription());
            contentValues.put(EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP, timeStamp);
            contentValues.put(EabProvider.PresenceTupleColumns.CONTACT_URI,
                    tuple.getContactUri().toString());
            if (serviceCapabilities != null) {
                contentValues.put(EabProvider.PresenceTupleColumns.DUPLEX_MODE, duplexMode);
                contentValues.put(EabProvider.PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE,
                        unsupportedDuplexMode);

                contentValues.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE,
                        serviceCapabilities.isAudioCapable());
                contentValues.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE,
                        serviceCapabilities.isVideoCapable());
            }
            presenceContent[i] = contentValues;
        }
        Log.d(TAG, "Insert into presence table. count: " + presenceContent.length);
        mContext.getContentResolver().bulkInsert(EabProvider.PRESENCE_URI, presenceContent);
    }

    private void deleteOldOptionCapability(int contactId) {
        Cursor c = mContext.getContentResolver().query(
                EabProvider.COMMON_URI,
                new String[]{EabProvider.EabCommonColumns._ID},
                EabProvider.EabCommonColumns.EAB_CONTACT_ID + "=?",
                new String[]{String.valueOf(contactId)}, null);

        if (c != null && c.getCount() > 0) {
            while(c.moveToNext()) {
                int commonId = c.getInt(c.getColumnIndex(EabProvider.EabCommonColumns._ID));
                mContext.getContentResolver().delete(
                        EabProvider.OPTIONS_URI,
                        EabProvider.OptionsColumns.EAB_COMMON_ID + "=?",
                        new String[]{String.valueOf(commonId)});
            }
        }

        if (c != null) {
            c.close();
        }
    }

    private void insertNewOptionCapability(int contactId, RcsContactUceCapability capability) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, contactId);
        contentValues.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_OPTIONS);
        contentValues.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, mSubId);
        contentValues.put(EabProvider.EabCommonColumns.REQUEST_RESULT,
                capability.getRequestResult());
        Uri result = mContext.getContentResolver().insert(EabProvider.COMMON_URI, contentValues);

        int commonId = Integer.valueOf(result.getLastPathSegment());
        ContentValues[] optionContent =
                new ContentValues[capability.getOptionsFeatureTags().size()];

        for (int i = 0; i < optionContent.length; i++) {
            String feature = capability.getOptionsFeatureTags().get(i);
            contentValues = new ContentValues();
            contentValues.put(EabProvider.OptionsColumns.EAB_COMMON_ID, commonId);
            contentValues.put(EabProvider.OptionsColumns.FEATURE_TAG, feature);
            contentValues.put(EabProvider.OptionsColumns.REQUEST_TIMESTAMP,
                    Instant.now().getEpochSecond());
            optionContent[i] = contentValues;
        }
        mContext.getContentResolver().bulkInsert(EabProvider.OPTIONS_URI, optionContent);
    }

    private String getStringValue(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndex(column));
    }

    private int getIntValue(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndex(column));
    }

    private static String getNumberFromUri(Uri uri) {
        String number = uri.getSchemeSpecificPart();
        String[] numberParts = number.split("[@;:]");
        if (numberParts.length == 0) {
            return null;
        }
        return numberParts[0];
    }
}
