/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.ims.rcs.uce.util;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.text.TextUtils;

import java.util.List;

/**
 * The util class of the feature tags.
 */
public class FeatureTags {

    private static final String FEATURE_TAG_STANDALONE_MSG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-"
                    + "service.ims.icsi.oma.cpm.msg,urn%3Aurn-7%3A3gpp-"
                    + "service.ims.icsi.oma.cpm.largemsg,urn%3Aurn-7%3A3gpp-"
                    + "service.ims.icsi.oma.cpm.deferred\";+g.gsma.rcs.cpm.pager-large";

    private static final String FEATURE_TAG_CHAT =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";

    private static final String FEATURE_TAG_FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";

    private static final String FEATURE_TAG_FILE_TRANSFER_VIA_SMS =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.ftsms\"";

    private static final String FEATURE_TAG_CALL_COMPOSER_ENRICHED_CALLING =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.callcomposer\"";

    private static final String FEATURE_TAG_CALL_COMPOSER_VIA_TELEPHONY = "+g.gsma.callcomposer";

    private static final String FEATURE_TAG_POST_CALL =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.callunanswered\"";

    private static final String FEATURE_TAG_SHARED_MAP =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.sharedmap\"";

    private static final String FEATURE_TAG_SHARED_SKETCH =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.gsma.sharedsketch\"";

    private static final String FEATURE_TAG_GEO_PUSH =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.geopush\"";

    private static final String FEATURE_TAG_GEO_PUSH_VIA_SMS =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.geosms\"";

    private static final String FEATURE_TAG_CHATBOT_COMMUNICATION_USING_SESSION =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot\"";

    private static final String FEATURE_TAG_CHATBOT_COMMUNICATION_USING_STANDALONE_MSG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot.sa\"";

    private static final String FEATURE_TAG_CHATOBT_VERSION_SUPPORTED =
            "+g.gsma.rcs.botversion=\"#=1,#=2\"";

    private static final String FEATURE_TAG_CPIM_EXTENSION = "+g.gsma.rcs.cpimext";

    private static final String FEATURE_TAG_DATA_OFF_ACTIVE = "+g.3gpp.ps-data-off=\"active\"";

    private static final String FEATURE_TAG_DATA_OFF_INACTIVE = "+g.3gpp.ps-data-off=\"inactive\"";

    private static final String FEATURE_TAG_MMTEL_AUDIO =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";

    private static final String FEATURE_TAG_MMTEL_VIDEO =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\";video";

    private static final String FEATURE_TAG_PRESENCE =
            "+g.3gpp.iari-ref=\"urn:urn-7:3gpp-application.ims.iari.rcse.dp\"";

    /**
     * Add the MMTEL feature tag to the given RcsContactUceCapability OPTIONS builder.
     * @param optionsBuilder The OptionsBuilder to add the mmtel feature tags
     * @param audioSupport If the audio capability is supported
     * @param videoSupport If the video capability is supported
     */
    public static void addMmTelFeatureTags(final OptionsBuilder optionsBuilder,
            boolean audioSupport, boolean videoSupport) {
        StringBuilder builder = new StringBuilder();
        if (audioSupport && videoSupport) {
            builder.append(FEATURE_TAG_MMTEL_VIDEO);
        } else if (audioSupport) {
            builder.append(FEATURE_TAG_MMTEL_AUDIO);
        }
        String mmtelFeature = builder.toString();
        if (!TextUtils.isEmpty(mmtelFeature)) {
            optionsBuilder.addFeatureTag(mmtelFeature);
        }
    }

    /**
     * Get RcsContactUceCapabilities from the given feature tags.
     */
    public static RcsContactUceCapability getContactCapability(Uri contact,
            List<String> featureTags) {
        OptionsBuilder builder = new OptionsBuilder(contact);
        builder.setRequestResult(RcsContactUceCapability.REQUEST_RESULT_FOUND);
        featureTags.forEach(feature -> builder.addFeatureTag(feature));
        return builder.build();
    }
}
