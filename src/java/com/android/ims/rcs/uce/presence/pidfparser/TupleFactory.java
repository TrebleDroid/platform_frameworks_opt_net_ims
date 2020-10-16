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

package com.android.ims.rcs.uce.presence.pidfparser;

import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;

import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Audio;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Duplex;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.ServiceCaps;
import com.android.ims.rcs.uce.presence.pidfparser.capabilities.Video;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Description;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceDescription;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.ServiceId;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.Version;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Basic;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Contact;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Status;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Tuple;

public class TupleFactory {

    /**
     * Get the Capability discovery by Presence tuple.
     */
    public static Tuple getCapabilityDiscoveryTuple(RcsContactUceCapability contactCaps) {
        // Return directly if it is not supported.
        if (contactCaps == null) {
            return null;
        }
        Tuple tuple = new Tuple();

        // The status element has a basic element with the value "open".
        Basic basic = new Basic(Basic.OPEN);
        Status status = new Status();
        status.setBasic(basic);
        tuple.setStatus(status);

        // Describe the capability discovery via presence is supported.
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceId(new ServiceId(PidfParserConstant.SERVICE_ID_CAPS_DISCOVERY));
        serviceDescription.setVersion(new Version(1, 0));
        serviceDescription.setDescription(new Description("Capabilities Discovery"));
        tuple.addElement(serviceDescription);

        // The "contact" element
        Contact contact = new Contact();
        contact.setContact(contactCaps.getContactUri().toString());
        tuple.setContact(contact);

        return tuple;
    }

    /**
     * Get the VoLTE Voice and Video call tuple.
     */
    public static Tuple getIpCallTuple(RcsContactUceCapability contactCaps) {
        Tuple tuple = new Tuple();

        // The status element has a basic element with the value "open".
        Basic basic = new Basic(Basic.OPEN);
        Status status = new Status();
        status.setBasic(basic);
        tuple.setStatus(status);

        // Describe the VoLTE voice and video.
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setServiceId(new ServiceId(PidfParserConstant.SERVICE_ID_IpCall));
        serviceDescription.setVersion(new Version(1, 0));
        serviceDescription.setDescription(new Description("VoLTE Voice/Video service"));
        tuple.addElement(serviceDescription);

        // The service capabilities element
        ServiceCaps serviceCaps = new ServiceCaps();
        Audio audio = new Audio(isVolteCapable(contactCaps));
        Video video = new Video(isVtCapable(contactCaps));
        Duplex duplex = new Duplex();
        duplex.setSupportedType(Duplex.DUPLEX_FULL);
        serviceCaps.addElement(audio);
        serviceCaps.addElement(video);
        serviceCaps.addElement(duplex);
        tuple.addElement(serviceCaps);

        // The "contact" element
        Contact contact = new Contact();
        contact.setContact(contactCaps.getContactUri().toString());
        tuple.setContact(contact);

        return tuple;
    }

    private static boolean isVolteCapable(RcsContactUceCapability capability) {
        RcsContactPresenceTuple presenceTuple = capability.getPresenceTuple(
                RcsContactPresenceTuple.SERVICE_ID_MMTEL);
        if (presenceTuple != null) {
            ServiceCapabilities serviceCaps = presenceTuple.getServiceCapabilities();
            if (serviceCaps != null && serviceCaps.isAudioCapable()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVtCapable(RcsContactUceCapability capability) {
        RcsContactPresenceTuple presenceTuple = capability.getPresenceTuple(
                RcsContactPresenceTuple.SERVICE_ID_MMTEL);
        if (presenceTuple != null) {
            ServiceCapabilities serviceCaps = presenceTuple.getServiceCapabilities();
            if (serviceCaps != null && serviceCaps.isVideoCapable()) {
                return true;
            }
        }
        return false;
    }
}
