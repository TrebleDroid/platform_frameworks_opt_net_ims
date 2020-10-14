package com.android.ims.rcs.uce.presence.pidfparser;

import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
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
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(true, true);
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
            RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
            RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tupleBuilder.addContactUri(contact).addServiceCapabilities(servCapsBuilder.build());

        PresenceBuilder presenceBuilder = new PresenceBuilder(contact,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());

        String pidfResult = PidfParser.convertToPidf(presenceBuilder.build());

        String audioSupported = "<caps:audio>true</caps:audio>";
        String videoSupported = "<caps:video>true</caps:video>";
        assertTrue(pidfResult.contains(audioSupported));
        assertTrue(pidfResult.contains(videoSupported));
    }
}
