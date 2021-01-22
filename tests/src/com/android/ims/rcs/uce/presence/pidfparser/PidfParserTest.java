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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import java.time.Instant;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

@RunWith(AndroidJUnit4.class)
public class PidfParserTest extends ImsTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testConvertToPidf() throws Exception {
        RcsContactUceCapability capability = getRcsContactUceCapability();

        String pidfResult = PidfParser.convertToPidf(capability);

        String contact = "<contact>sip:test</contact>";
        String audioSupported = "<caps:audio>true</caps:audio>";
        String videoSupported = "<caps:video>true</caps:video>";
        String description = "<op:version>1.0</op:version>";
        assertTrue(pidfResult.contains(contact));
        assertTrue(pidfResult.contains(audioSupported));
        assertTrue(pidfResult.contains(videoSupported));
        assertTrue(pidfResult.contains(description));
    }

    @Test
    @SmallTest
    public void testConvertToRcsContactUceCapability() throws Exception {
        // Create the pidf data
        String pidf = getPidfData();

        // Convert to the class RcsContactUceCapability
        RcsContactUceCapability capabilities = PidfParser.convertToRcsContactUceCapability(pidf);

        assertNotNull(capabilities);
        assertEquals(Uri.fromParts("sip", "test", null), capabilities.getContactUri());

        List<RcsContactPresenceTuple> presenceTupleList = capabilities.getPresenceTuples();
        assertNotNull(presenceTupleList);
        assertEquals(2, presenceTupleList.size());

        RcsContactPresenceTuple presenceTuple1 = presenceTupleList.get(0);
        assertEquals("service_id_01", presenceTuple1.getServiceId());
        assertEquals("1.0", presenceTuple1.getServiceVersion());
        assertEquals("description_test1", presenceTuple1.getServiceDescription());
        assertEquals("2001-01-01T01:00:000Z", presenceTuple1.getTimestamp());
        assertEquals(Uri.fromParts("sip", "test", null), presenceTuple1.getContactUri());
        assertTrue(presenceTuple1.getServiceCapabilities().isAudioCapable());
        assertTrue(presenceTuple1.getServiceCapabilities().isVideoCapable());

        RcsContactPresenceTuple presenceTuple2 = presenceTupleList.get(1);
        assertEquals("service_id_02", presenceTuple2.getServiceId());
        assertEquals("1.0", presenceTuple2.getServiceVersion());
        assertEquals("description_test2", presenceTuple2.getServiceDescription());
        assertEquals("2001-02-02T01:00:000Z", presenceTuple2.getTimestamp());
        assertEquals(Uri.fromParts("sip", "test", null), presenceTuple2.getContactUri());
        assertTrue(presenceTuple2.getServiceCapabilities().isAudioCapable());
        assertFalse(presenceTuple2.getServiceCapabilities().isVideoCapable());
    }

    @Test
    @SmallTest
    public void testConversionAndRestoration() throws Exception {
        // Create the capability
        final RcsContactUceCapability capability = getRcsContactUceCapability();

        // Convert the capability to the pidf
        final String pidf = PidfParser.convertToPidf(capability);

        // Restore to the RcsContactUceCapability from the pidf
        final RcsContactUceCapability restoredCapability =
                PidfParser.convertToRcsContactUceCapability(pidf);

        assertEquals(capability.getContactUri(), restoredCapability.getContactUri());
        assertEquals(capability.getCapabilityMechanism(),
                restoredCapability.getCapabilityMechanism());
        assertEquals(capability.getSourceType(), restoredCapability.getSourceType());

        // Assert all the tuples are equal
        List<RcsContactPresenceTuple> originalTuples = capability.getPresenceTuples();
        List<RcsContactPresenceTuple> restoredTuples = restoredCapability.getPresenceTuples();

        assertNotNull(restoredTuples);
        assertEquals(originalTuples.size(), restoredTuples.size());

        for (int i = 0; i < originalTuples.size(); i++) {
            RcsContactPresenceTuple tuple = originalTuples.get(i);
            RcsContactPresenceTuple restoredTuple = restoredTuples.get(i);

            assertEquals(tuple.getContactUri(), restoredTuple.getContactUri());
            assertEquals(tuple.getStatus(), restoredTuple.getStatus());
            assertEquals(tuple.getServiceId(), restoredTuple.getServiceId());
            assertEquals(tuple.getServiceVersion(), restoredTuple.getServiceVersion());
            assertEquals(tuple.getServiceDescription(), restoredTuple.getServiceDescription());

            boolean isAudioCapable = false;
            boolean isVideoCapable = false;
            boolean isRestoredAudioCapable = false;
            boolean isRestoredVideoCapable = false;

            ServiceCapabilities servCaps = tuple.getServiceCapabilities();
            if (servCaps != null) {
                isAudioCapable = servCaps.isAudioCapable();
                isVideoCapable = servCaps.isVideoCapable();
            }

            ServiceCapabilities restoredServCaps = restoredTuple.getServiceCapabilities();
            if (restoredServCaps != null) {
                isRestoredAudioCapable = restoredServCaps.isAudioCapable();
                isRestoredVideoCapable = restoredServCaps.isVideoCapable();
            }

            assertEquals(isAudioCapable, isRestoredAudioCapable);
            assertEquals(isVideoCapable, isRestoredVideoCapable);
        }
     }

    private String getPidfData() {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"sip:test\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                // tuple 1
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // support video
                .append("<caps:video>true</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("<timestamp>2001-01-01T01:00:000Z</timestamp>")
                .append("</tuple>")
                // tuple 2
                .append("<tuple id=\"tid1\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_02</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test2</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // NOT support video
                .append("<caps:video>false</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("<timestamp>2001-02-02T01:00:000Z</timestamp>")
                .append("</tuple></presence>");

        return pidfBuilder.toString();
    }

    private RcsContactUceCapability getRcsContactUceCapability() {
        final Uri contact = Uri.fromParts("sip", "test", null);
        final boolean isAudioCapable = true;
        final boolean isVideoCapable = true;
        final String duplexMode = ServiceCapabilities.DUPLEX_MODE_FULL;
        final String basicStatus = RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN;
        final String version = "1.0";
        final String description = "description test";
        final String nowTime = Instant.now().toString();

        // init the capabilities
        ServiceCapabilities.Builder servCapsBuilder =
                new ServiceCapabilities.Builder(isAudioCapable, isVideoCapable);
        servCapsBuilder.addSupportedDuplexMode(duplexMode);

        // init the presence tuple
        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                basicStatus, RcsContactPresenceTuple.SERVICE_ID_MMTEL, version);
        tupleBuilder.addContactUri(contact)
                .addDescription(description)
                .addTimeStamp(nowTime)
                .addServiceCapabilities(servCapsBuilder.build());

        PresenceBuilder presenceBuilder = new PresenceBuilder(contact,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());

        return presenceBuilder.build();
    }
}
