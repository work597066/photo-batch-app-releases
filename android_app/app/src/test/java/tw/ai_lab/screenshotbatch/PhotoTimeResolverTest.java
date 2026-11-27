package tw.ai_lab.screenshotbatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;
import java.time.OffsetDateTime;

public class PhotoTimeResolverTest {
    @Test public void parsesMillisecondEpochFilename() {
        PhotoTimeResolver.ResolvedTime time = PhotoTimeResolver.parseFilenameTime("1795334470726.jpg");
        assertEquals(Long.valueOf(1795334470726L), time.millis);
        assertEquals("filename_epoch", time.source);
    }

    @Test public void parsesSecondEpochFilename() {
        PhotoTimeResolver.ResolvedTime time = PhotoTimeResolver.parseFilenameTime("Screenshot_1795334470.png");
        assertEquals(Long.valueOf(1795334470000L), time.millis);
    }

    @Test public void parsesCameraTimestampInDeviceTimezone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
            PhotoTimeResolver.ResolvedTime time = PhotoTimeResolver.parseFilenameTime("IMG_20261122_160447.jpg");
            assertEquals(Long.valueOf(1795334687000L), time.millis);
            assertEquals("filename_datetime", time.source);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test public void parsesChineseGrabTimestamp() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
            PhotoTimeResolver.ResolvedTime time = PhotoTimeResolver.parseFilenameTime("抓圖_20261122_154827.jpg");
            assertEquals(Long.valueOf(1795333707000L), time.millis);
            assertEquals("filename_datetime", time.source);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test public void parsesScreenshotTimestampWithMilliseconds() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
            PhotoTimeResolver.ResolvedTime time = PhotoTimeResolver.parseFilenameTime(
                    "Screenshot_2026-11-21-21-27-56-881_ai.x.grok.bot.jpg");
            assertEquals(Long.valueOf(OffsetDateTime.parse("2026-11-21T21:27:56.881+08:00").toInstant().toEpochMilli()), time.millis);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test public void rejectsImpossibleOrOutOfRangeDate() {
        assertNull(PhotoTimeResolver.parseFilenameTime("Screenshot_2026-02-30-12-00-00.jpg"));
        assertNull(PhotoTimeResolver.parseFilenameTime("photo_9999999999.jpg"));
    }

    @Test public void acceptsDocumentTimestampAsMilliseconds() {
        assertTrue(PhotoTimeResolver.isTrustedTime(1795330293489L));
        assertFalse(PhotoTimeResolver.isTrustedTime(1795330293489L * 1000L));
    }

    @Test public void rejectsFilenameWithoutTime() {
        assertNull(PhotoTimeResolver.parseFilenameTime("photo-final.jpg"));
    }
}
