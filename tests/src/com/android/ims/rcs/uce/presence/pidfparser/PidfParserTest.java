package com.android.ims.rcs.uce.presence.pidfparser;

import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

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
        Uri contact = Uri.fromParts("sip", "test", null);
        RcsContactUceCapability.Builder builder = new RcsContactUceCapability.Builder(contact);
        builder.add(RcsContactUceCapability.CAPABILITY_DISCOVERY_VIA_PRESENCE);
        builder.add(RcsContactUceCapability.CAPABILITY_IP_VOICE_CALL);
        builder.add(RcsContactUceCapability.CAPABILITY_IP_VIDEO_CALL);

        String pidfResult = PidfParser.convertToPidf(builder.build());

        String audioSupported = "<caps:audio>true</caps:audio>";
        String videoSupported = "<caps:video>true</caps:video>";
        assertTrue(pidfResult.contains(audioSupported));
        assertTrue(pidfResult.contains(videoSupported));
    }
}
