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
import java.util.ArrayList;
import java.util.List;

/**
 * The "tuple" element of the pidf.
 */
public class Tuple extends ElementBase {
    /**
     * The tuple element consists the following elements:
     * 1: one "status" element
     * 2: any number of optional extension elements
     * 3: an optional "contact" element
     * 4: any number of optional "note" elements
     */

    private static long sTupleId = 0;
    private static final Object TUPLE_Lock = new Object();

    private final String mId;
    private Status mStatus;
    private List<ElementBase> mExtensionElements = new ArrayList<>();
    private Contact mContact;
    private List<Note> mNoteList = new ArrayList<>();

    public Tuple() {
        mId = getTupleId();
    }

    @Override
    protected String initNamespace() {
        return PidfConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return "tuple";
    }

    public void setStatus(Status status) {
        mStatus = status;
    }

    public void addElement(ElementBase element) {
        mExtensionElements.add(element);
    }

    public void setContact(Contact contact) {
        mContact = contact;
    }

    public void addNote(Note note) {
        mNoteList.add(note);
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        String namespace = getNamespace();
        String elementName = getElementName();

        serializer.startTag(namespace, elementName);
        // id attribute
        serializer.attribute(XmlPullParser.NO_NAMESPACE, "id", mId);
        // status element
        mStatus.serialize(serializer);
        // extension elements
        for(ElementBase element : mExtensionElements) {
            element.serialize(serializer);
        }
        // contact element
        if (mContact != null) {
            mContact.serialize(serializer);
        }
        // note element
        for (Note note: mNoteList) {
            note.serialize(serializer);
        }
        serializer.endTag(namespace, elementName);
    }

    private String getTupleId() {
        synchronized (TUPLE_Lock) {
            return "tid" + (sTupleId++);
        }
    }
}
