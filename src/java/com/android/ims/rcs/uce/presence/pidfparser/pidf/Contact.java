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

package com.android.ims.rcs.uce.presence.pidfparser.pidf;

import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * The "contact" element of the pidf.
 */
public class Contact extends ElementBase {
    private Double mPriority;
    private String mContact;

    public Contact() {
    }

    @Override
    protected String initNamespace() {
        return PidfConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "contact";
    }

    public void setPriority(Double priority) {
        mPriority = priority;
    }

    public void setContact(String contact) {
        mContact = contact;
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        if (mContact == null) {
            return;
        }
        String noNamespace = XmlPullParser.NO_NAMESPACE;
        String namespace = getNamespace();
        String elementName = getElementName();
        serializer.startTag(namespace, elementName);
        if (mPriority != null) {
            serializer.attribute(noNamespace, "priority", String.valueOf(mPriority));
        }
        serializer.text(mContact);
        serializer.endTag(namespace, elementName);
    }
}
