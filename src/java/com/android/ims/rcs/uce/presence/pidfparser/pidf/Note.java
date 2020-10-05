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

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * The "note" element of the pidf.
 */
public class Note extends ElementBase {
    /**
     * The "note" element is usually used for a human readable comment. This element may appear as
     * a child element of "presence" or as a child element of the "tuple" element.
     */
    private final String mNote;

    public Note(String note) {
        mNote = note;
    }

    @Override
    protected String initNamespace() {
        return PidfConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "note";
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        if (mNote == null) {
            return;
        }
        final String namespace = getNamespace();
        final String element = getElementName();
        serializer.startTag(namespace, element);
        serializer.text(mNote);
        serializer.endTag(namespace, element);
    }
}
