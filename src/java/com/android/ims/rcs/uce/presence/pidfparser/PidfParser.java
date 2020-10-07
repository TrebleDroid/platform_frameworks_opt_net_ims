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

import android.telephony.ims.RcsContactUceCapability;

import com.android.ims.rcs.uce.presence.pidfparser.capabilities.CapsConstant;
import com.android.ims.rcs.uce.presence.pidfparser.omapres.OmaPresConstant;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.PidfConstant;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Presence;
import com.android.ims.rcs.uce.presence.pidfparser.pidf.Tuple;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

/**
 * The pidf parser class
 */
public class PidfParser {
    /**
     * Convert the RcsContactUceCapability to the string of pidf.
     */
    public static String convertToPidf(RcsContactUceCapability capabilities) {
        StringWriter pidfWriter = new StringWriter();
        try {
            // Init the instance of the XmlSerializer.
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlSerializer serializer = factory.newSerializer();

            // setup output and namespace
            serializer.setOutput(pidfWriter);
            serializer.setPrefix("", PidfConstant.NAMESPACE);
            serializer.setPrefix("op", OmaPresConstant.NAMESPACE);
            serializer.setPrefix("caps", CapsConstant.NAMESPACE);

            // Get the Presence element
            Presence presence = getPresence(capabilities);

            // Start serializing.
            serializer.startDocument(PidfParserConstant.ENCODING_UTF_8, true);
            presence.serialize(serializer);
            serializer.endDocument();
            serializer.flush();

        } catch (XmlPullParserException parserEx) {
            parserEx.printStackTrace();
            return null;
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return null;
        }
        return pidfWriter.toString();
    }

    private static Presence getPresence(RcsContactUceCapability capabilities) {
        // Create "presence" element which is the root element of the pidf
        Presence presence = new Presence(capabilities);

        // Add the Capabilities discovery via Presence tuple.
        Tuple capsDiscoveryTuple = TupleFactory.getCapabilityDiscoveryTuple(capabilities);
        if (capsDiscoveryTuple != null) {
            presence.addTuple(capsDiscoveryTuple);
        }

        // Add the VoLTE voice/video tuple.
        Tuple ipCallTuple = TupleFactory.getIpCallTuple(capabilities);
        if (ipCallTuple != null) {
            presence.addTuple(ipCallTuple);
        }
        return presence;
    }
}
