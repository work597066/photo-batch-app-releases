package tw.ai_lab.screenshotbatch;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PhotoTimeResolver {
    static final class ResolvedTime {
        final Long millis;
        final String source;
        ResolvedTime(Long millis, String source) { this.millis = millis; this.source = source; }
    }

    static final class Metadata {
        final String displayName;
        final String mimeType;
        final long sizeBytes;
        final int widthPx;
        final int heightPx;
        final ResolvedTime captureTime;
        Metadata(String displayName, String mimeType, long sizeBytes, int widthPx, int heightPx,
                 ResolvedTime captureTime) {
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.captureTime = captureTime;
        }
    }

    private static final Pattern EPOCH = Pattern.compile("(?<![0-9])(\\d{13}|\\d{10})(?![0-9])");
    private static final Pattern COMPACT_DATE = Pattern.compile("(?i)(?:IMG|Screenshot|抓圖)[_-](\\d{8})[_-](\\d{6})");
    private static final Pattern SCREENSHOT_DASH = Pattern.compile("(?i)Screenshot[_-](\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})(?:-(\\d{3}))?");
    private static final long MIN_TRUSTED_MILLIS = 946684800000L; // 2000-01-01T00:00:00Z
    private static final long MAX_TRUSTED_MILLIS = 4133980800000L; // 2101-01-01T00:00:00Z

    private PhotoTimeResolver() {}

    static Metadata read(Context context, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String name = queryName(resolver, uri);
        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";
        long size = queryLong(resolver, uri, OpenableColumns.SIZE, -1L);

        int[] dimensions = readDimensions(resolver, uri);
        // Use EXIF capture time first when it is valid; filenames are the next source.
        ResolvedTime capture = readExifTime(resolver, uri);
        if (capture == null) capture = parseFilenameTime(name);
        if (capture == null) {
            Long mediaStoreTime = queryLongNullable(resolver, uri, MediaStore.Images.ImageColumns.DATE_TAKEN);
            if (isTrustedTime(mediaStoreTime)) capture = new ResolvedTime(mediaStoreTime, "media_store_date_taken");
        }
        if (capture == null) {
            // DocumentsContract.COLUMN_LAST_MODIFIED is already milliseconds.
            Long modifiedMillis = queryLongNullable(resolver, uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            if (isTrustedTime(modifiedMillis)) capture = new ResolvedTime(modifiedMillis, "document_last_modified_fallback");
        }
        if (capture == null) capture = new ResolvedTime(null, "selection_order_fallback");
        return new Metadata(name, mime, size, dimensions[0], dimensions[1], capture);
    }

    private static String queryName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        String segment = uri.getLastPathSegment();
        return segment == null ? "image" : segment;
    }

    private static long queryLong(ContentResolver resolver, Uri uri, String column, long fallback) {
        Long value = queryLongNullable(resolver, uri, column);
        return value == null ? fallback : value;
    }

    private static Long queryLongNullable(ContentResolver resolver, Uri uri, String column) {
        try (Cursor cursor = resolver.query(uri, new String[]{column}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static int[] readDimensions(ContentResolver resolver, Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input != null) BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) { }
        return new int[]{Math.max(0, options.outWidth), Math.max(0, options.outHeight)};
    }

    private static ResolvedTime readExifTime(ContentResolver resolver, Uri uri) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return null;
            ExifInterface exif = new ExifInterface(input);
            String value = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (value == null) return null;
            SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            format.setLenient(false);
            // OffsetTimeOriginal was added in API 30; using its stable EXIF tag name
            // keeps this read safe on the app's API 26 minimum.
            String offset = exif.getAttribute("OffsetTimeOriginal");
            if (offset != null && offset.matches("[+-]\\d{2}:\\d{2}")) {
                format.setTimeZone(TimeZone.getTimeZone("GMT" + offset));
            }
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length() && isTrustedTime(date.getTime()))
                return new ResolvedTime(date.getTime(), "exif_datetime_original");
        } catch (Exception ignored) { }
        return null;
    }

    static ResolvedTime parseFilenameTime(String filename) {
        if (filename == null) return null;
        Matcher compact = COMPACT_DATE.matcher(filename);
        if (compact.find()) {
            Date parsed = parse(compact.group(1) + compact.group(2), "yyyyMMddHHmmss");
            if (parsed != null && isTrustedTime(parsed.getTime())) return new ResolvedTime(parsed.getTime(), "filename_datetime");
        }
        Matcher dashed = SCREENSHOT_DASH.matcher(filename);
        if (dashed.find()) {
            String dateTime = String.join("", dashed.group(1), dashed.group(2), dashed.group(3),
                    dashed.group(4), dashed.group(5), dashed.group(6));
            String pattern = "yyyyMMddHHmmss";
            if (dashed.group(7) != null) {
                dateTime += dashed.group(7);
                pattern += "SSS";
            }
            Date parsed = parse(dateTime, pattern);
            if (parsed != null && isTrustedTime(parsed.getTime())) return new ResolvedTime(parsed.getTime(), "filename_datetime");
        }
        Matcher epoch = EPOCH.matcher(filename);
        if (epoch.find()) {
            try {
                long raw = Long.parseLong(epoch.group(1));
                long millis = epoch.group(1).length() == 10 ? Math.multiplyExact(raw, 1000L) : raw;
                if (isTrustedTime(millis)) return new ResolvedTime(millis, "filename_epoch");
            } catch (NumberFormatException | ArithmeticException ignored) { }
        }
        return null;
    }

    static boolean isTrustedTime(Long millis) {
        return millis != null && millis >= MIN_TRUSTED_MILLIS && millis < MAX_TRUSTED_MILLIS;
    }

    private static Date parse(String value, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length() ? parsed : null;
    }
}
