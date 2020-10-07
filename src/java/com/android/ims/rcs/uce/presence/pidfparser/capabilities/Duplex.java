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

package com.android.ims.rcs.uce.presence.pidfparser.capabilities;

import android.annotation.StringDef;

import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The "duplex" element indicates how the communication service send and receive media. It can
 * contain two elements: "supported" and "notsupported." The supported and
 * nonsupported elements can contains four elements: "full", "half", "receive-only" and
 * "send-only".
 */
public class Duplex extends ElementBase {

    /** The device can simultaneously send and receive media */
    public static final String DUPLEX_FULL = "full";

    /** The service can alternate between sending and receiving media.*/
    public static final String DUPLEX_HALF = "half";

    /** The service can only receive media */
    public static final String DUPLEX_RECEIVE_ONLY = "receive-only";

    /** The service can only send media */
    public static final String DUPLEX_SEND_ONLY = "send-only";

    @StringDef(value = {
            DUPLEX_FULL,
            DUPLEX_HALF,
            DUPLEX_RECEIVE_ONLY,
            DUPLEX_SEND_ONLY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DuplexType {}

    private String mSupportedType;
    private String mNotSupportedType;

    public Duplex() {
    }

    @Override
    protected String initNamespace() {
        return CapsConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "duplex";
    }

    public void setSupportedType(@DuplexType String type) {
        mSupportedType = type;
    }

    public void setNotSupportedType(@DuplexType String type) {
        mNotSupportedType = type;
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        if (mSupportedType == null && mSupportedType == null) {
            return;
        }
        String namespace = getNamespace();
        String elementName = getElementName();
        serializer.startTag(namespace, elementName);
        if (mSupportedType != null) {
            serializer.startTag(namespace, "supported");
            serializer.text(mSupportedType);
            serializer.endTag(namespace, "supported");
        }
        if (mNotSupportedType != null) {
            serializer.startTag(namespace, "notsupported");
            serializer.text(mNotSupportedType);
            serializer.endTag(namespace, "notsupported");
        }
        serializer.endTag(namespace, elementName);
    }
}
