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

import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "servicecaps" element is the root element of service capabilities.
 */
public class ServiceCaps extends ElementBase {

    // The elements in the "servcaps" element.
    private final List<ElementBase> mElements = new ArrayList<>();

    public ServiceCaps() {
    }

    @Override
    protected String initNamespace() {
        return CapsConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "servcaps";
    }

    public void addElement(ElementBase element) {
        mElements.add(element);
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        if (mElements.isEmpty()) {
            return;
        }
        String namespace = getNamespace();
        String elementName = getElementName();
        serializer.startTag(namespace, elementName);
        for (ElementBase element : mElements) {
            element.serialize(serializer);
        }
        serializer.endTag(namespace, elementName);

    }
}
