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

import android.telephony.ims.RcsContactUceCapability;

import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "present" element is the root element of an "application/pidf+xml" object.
 */
public class Presence extends ElementBase {
    /**
     * The presence element consists the following elements:
     * 1: Any number (including 0) of <tuple> elements
     * 2: Any number (including 0) of <note> elements
     * 3: Any number of OPTIONAL extension elements from other namespaces.
     */

    // The presence element must have an "entity" attribute.
    private String mEntity;

    // The presence element contains any number of <tuple> elements
    private final List<Tuple> mTupleList = new ArrayList<>();

    // The presence element contains any number of <note> elements;
    private final List<Note> mNoteList = new ArrayList<>();

    public Presence(RcsContactUceCapability capability) {
        initEntity(capability);
    }

    private void initEntity(RcsContactUceCapability capability) {
        mEntity = capability.getContactUri().toString();
    }

    @Override
    protected String initNamespace() {
        return PidfConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "presence";
    }

    public void addTuple(Tuple tuple) {
        mTupleList.add(tuple);
    }

    public void addNote(Note note) {
        mNoteList.add(note);
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        String namespace = getNamespace();
        String elementName = getElementName();

        serializer.startTag(namespace, elementName);
        // entity attribute
        serializer.attribute("", "entity", mEntity);
        // tuple element
        for (Tuple tuple : mTupleList) {
            tuple.serialize(serializer);
        }
        // note element
        for (Note note : mNoteList) {
            note.serialize(serializer);
        }
        serializer.endTag(namespace, elementName);
    }
}
