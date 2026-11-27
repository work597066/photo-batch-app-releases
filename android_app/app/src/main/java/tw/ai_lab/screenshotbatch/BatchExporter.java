package tw.ai_lab.screenshotbatch;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.media.ExifInterface;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class BatchExporter {
    interface ProgressListener { void onProgress(int current, int total, String message); }

    static final class Result {
        final String batchId;
        final File zipFile;
        final int imageCount;
        Result(String batchId, File zipFile, int imageCount) {
            this.batchId = batchId;
            this.zipFile = zipFile;
            this.imageCount = imageCount;
        }
    }

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO_STAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int PDF_LONG_EDGE = 792;

    private BatchExporter() {}

    static Result create(Context context, List<PhotoItem> input, int startingBatchNumber,
                         MetadataOptions options, ProgressListener listener) throws Exception {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("請先選取照片。");
        if (options == null) options = new MetadataOptions();
        final MetadataOptions batchOptions = options;
        ArrayList<PhotoItem> photos = new ArrayList<>(input);
        photos.sort((a, b) -> {
            if (a.captureTimeMillis == null && b.captureTimeMillis != null) return 1;
            if (a.captureTimeMillis != null && b.captureTimeMillis == null) return -1;
            if (a.captureTimeMillis != null) {
                int byTime = Long.compare(a.captureTimeMillis, b.captureTimeMillis);
                if (byTime != 0) return byTime;
            }
            return Integer.compare(a.selectionOrder, b.selectionOrder);
        });

        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) throw new IllegalStateException("無法建立暫存輸出資料夾。");
        int number = Math.max(1, startingBatchNumber);
        String batchId;
        File workDir;
        File zipFile;
        do {
            batchId = String.format(Locale.US, "BATCH_%03d", number);
            workDir = new File(context.getCacheDir(), "work/" + batchId);
            zipFile = new File(exportDir, String.format(Locale.US, "batch_%03d.zip", number));
            number++;
        } while (workDir.exists() || zipFile.exists());
        File originalsDir = new File(workDir, "originals");
        if (!originalsDir.mkdirs()) throw new IllegalStateException("無法建立原始照片輸出資料夾。");

        ArrayList<JSONObject> imageRecords = new ArrayList<>();
        try {
            for (int i = 0; i < photos.size(); i++) {
                PhotoItem item = photos.get(i);
                int sequence = i + 1;
                item.sequence = sequence;
                JSONObject namingRecord = namingRecord(item, sequence, batchOptions);
                item.outputFilename = namingRecord.getString("generated_filename");
                File target = new File(originalsDir, item.outputFilename);
                String sha256 = copyAndHash(context.getContentResolver(), item.uri, target);
                item.outputFilename = target.getName();
                item.sizeBytes = target.length();
                item.sha256 = sha256;
                item.outputPath = namingRecord.getString("relative_path");
                imageRecords.add(imageRecord(context, item, batchId, namingRecord, batchOptions));
                if (listener != null) listener.onProgress(sequence, photos.size(), "正在保留原始照片 " + sequence + "/" + photos.size());
            }

            File pdfFile = new File(workDir, "batch_001.pdf");
            createPdf(photos, originalsDir, pdfFile);
            if (listener != null) listener.onProgress(photos.size(), photos.size(), "正在建立 JSON、說明與 ZIP");
            writeJson(new File(workDir, "batch_001.json"), batchId, imageRecords, pdfFile.length(), batchOptions);
            writeReadme(new File(workDir, "README.txt"), batchId, photos.size());
            writeZip(workDir, zipFile, batchId);
            deleteTree(workDir);
            return new Result(batchId, zipFile, photos.size());
        } catch (Exception e) {
            deleteTree(workDir);
            if (zipFile.exists()) // A partial ZIP is never offered for sharing.
                //noinspection ResultOfMethodCallIgnored
                zipFile.delete();
            throw e;
        }
    }

    private static JSONObject namingRecord(PhotoItem item, int sequence, MetadataOptions options) throws Exception {
        JSONObject record = new JSONObject();
        record.put("sequence", sequence);
        if (item.captureTimeMillis == null) record.put("capture_time", JSONObject.NULL);
        else record.put("capture_time", ISO_STAMP.format(Instant.ofEpochMilli(item.captureTimeMillis).atZone(ZoneId.systemDefault())));
        record.put("capture_time_source", item.captureTimeSource);
        String extension = extensionFor(item.originalFilename, item.mimeType);
        String stamp = record.isNull("capture_time") ? "undated"
                : FILE_STAMP.format(OffsetDateTime.parse(record.getString("capture_time")).toInstant());
        String prefix = safeBatchName(options.batchName);
        String filename = prefix.isEmpty()
                ? String.format(Locale.US, "%s_%03d.%s", stamp, sequence, extension)
                : String.format(Locale.US, "%s_%03d.%s", prefix, sequence, extension);
        record.put("generated_filename", filename);
        record.put("relative_path", "originals/" + filename);
        return record;
    }

    private static String extensionFor(String filename, String mime) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
                if (!ext.isEmpty() && ext.length() <= 8) return ext;
            }
        }
        if (mime != null && mime.equalsIgnoreCase("image/png")) return "png";
        if (mime != null && mime.equalsIgnoreCase("image/webp")) return "webp";
        return "jpg";
    }

    private static String copyAndHash(ContentResolver resolver, Uri uri, File destination) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            if (input == null) throw new IllegalStateException("無法讀取照片來源：" + uri);
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }

    private static JSONObject imageRecord(Context context, PhotoItem item, String batchId,
                                          JSONObject record, MetadataOptions options) throws Exception {
        record.put("image_id", String.format(Locale.US, "IMG-%04d", item.sequence));
        record.put("generated_filename", item.outputFilename);
        record.put("relative_path", item.outputPath);
        record.put("sha256", item.sha256);
        record.put("output_file", "batch_001.pdf");
        record.put("page_number", item.sequence);
        record.put("conversion_method", "Original image bytes copied unchanged; PDF page rendered from source image");
        record.put("batch_id", batchId);
        record.put("metadata", selectedMetadata(context, item, options));
        return record;
    }

    private static JSONObject selectedMetadata(Context context, PhotoItem item, MetadataOptions options) throws Exception {
        JSONObject metadata = new JSONObject();
        // These baseline fields are always present, even when the user has not opened settings.
        metadata.put("custom_identifier", provided(String.format(Locale.US, "IMG-%04d", item.sequence)));
        metadata.put("file_size_bytes", provided(item.sizeBytes));
        if (item.captureTimeMillis == null) {
            metadata.put("capture_date", notProvided("照片未提供可確認的拍攝日期"));
            metadata.put("capture_time", notProvided("照片未提供可確認的拍攝時間"));
        } else {
            java.time.ZonedDateTime captured = Instant.ofEpochMilli(item.captureTimeMillis).atZone(ZoneId.systemDefault());
            metadata.put("capture_date", provided(captured.toLocalDate().toString()));
            metadata.put("capture_time", provided(captured.toLocalTime().withNano(0).toString()));
        }

        Set<String> selected = options.selectedFields;
        if (selected.contains("original_filename")) metadata.put("original_filename", provided(item.originalFilename));
        if (selected.contains("file_format")) metadata.put("file_format", provided(new JSONObject()
                .put("mime_type", item.mimeType).put("extension", extensionFor(item.originalFilename, item.mimeType))));
        if (selected.contains("image_dimensions")) metadata.put("image_dimensions", provided(new JSONObject()
                .put("width_px", item.widthPx).put("height_px", item.heightPx)));

        ExifInterface exif = null;
        try (InputStream input = context.getContentResolver().openInputStream(item.uri)) {
            if (input != null) exif = new ExifInterface(input);
        } catch (Exception ignored) { }
        if (selected.contains("device_brand")) metadata.put("device_brand", exifValue(exif, ExifInterface.TAG_MAKE));
        if (selected.contains("device_model")) metadata.put("device_model", exifValue(exif, ExifInterface.TAG_MODEL));
        if (selected.contains("camera_info")) {
            JSONObject camera = new JSONObject();
            putExisting(camera, "lens_make", attribute(exif, "LensMake"));
            putExisting(camera, "lens_model", attribute(exif, "LensModel"));
            putExisting(camera, "focal_length", attribute(exif, ExifInterface.TAG_FOCAL_LENGTH));
            metadata.put("camera_info", camera.length() == 0 ? notProvided("照片未提供相機資訊") : provided(camera));
        }
        if (selected.contains("other_exif")) metadata.put("other_exif", otherExif(exif));

        float[] latLong = new float[2];
        boolean hasGps = exif != null && exif.getLatLong(latLong);
        if (selected.contains("gps_latitude")) metadata.put("gps_latitude", hasGps ? provided(latLong[0]) : notProvided("照片未提供 GPS 緯度"));
        if (selected.contains("gps_longitude")) metadata.put("gps_longitude", hasGps ? provided(latLong[1]) : notProvided("照片未提供 GPS 經度"));
        if (selected.contains("place_name")) metadata.put("place_name", notProvided("未執行地點名稱或地址分析"));
        if (selected.contains("geography")) {
            JSONObject geography = new JSONObject();
            if (hasGps) {
                geography.put("latitude", latLong[0]);
                geography.put("longitude", latLong[1]);
                String altitude = attribute(exif, ExifInterface.TAG_GPS_ALTITUDE);
                putExisting(geography, "altitude", altitude);
                metadata.put("geography", provided(geography));
            } else metadata.put("geography", notProvided("照片未提供地理位置 EXIF"));
        }

        ArrayList<String> labels = new ArrayList<>();
        addLabelIfSelected(labels, selected, "label_japan", "日本之旅");
        addLabelIfSelected(labels, selected, "label_hawaii", "夏威夷之旅");
        addLabelIfSelected(labels, selected, "label_korea", "韓國之旅");
        addLabelIfSelected(labels, selected, "label_work", "工作紀錄");
        if (selected.contains("label_custom")) labels.addAll(options.labels);
        if (!labels.isEmpty()) {
            JSONArray labelArray = new JSONArray();
            for (String label : labels) labelArray.put(label);
            metadata.put("custom_labels", provided(labelArray));
        }
        return metadata;
    }

    private static void addLabelIfSelected(List<String> labels, Set<String> selected, String key, String value) {
        if (selected.contains(key)) labels.add(value);
    }

    private static JSONObject provided(Object value) throws Exception {
        return new JSONObject().put("value", value == null ? JSONObject.NULL : value).put("status", "provided");
    }

    private static JSONObject notProvided(String reason) throws Exception {
        return new JSONObject().put("value", JSONObject.NULL).put("status", "not_provided").put("reason", reason);
    }

    private static JSONObject exifValue(ExifInterface exif, String tag) throws Exception {
        String value = attribute(exif, tag);
        return value == null ? notProvided("照片未提供此 EXIF 資料") : provided(value);
    }

    private static String attribute(ExifInterface exif, String tag) {
        if (exif == null) return null;
        String value = exif.getAttribute(tag);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static JSONObject otherExif(ExifInterface exif) throws Exception {
        JSONObject values = new JSONObject();
        String[][] tags = {
                {"datetime", ExifInterface.TAG_DATETIME}, {"datetime_original", ExifInterface.TAG_DATETIME_ORIGINAL},
                {"datetime_digitized", ExifInterface.TAG_DATETIME_DIGITIZED}, {"exposure_time", ExifInterface.TAG_EXPOSURE_TIME},
                {"f_number", ExifInterface.TAG_F_NUMBER}, {"iso_speed", ExifInterface.TAG_ISO_SPEED_RATINGS},
                {"focal_length", ExifInterface.TAG_FOCAL_LENGTH}, {"flash", ExifInterface.TAG_FLASH},
                {"white_balance", ExifInterface.TAG_WHITE_BALANCE}, {"orientation", ExifInterface.TAG_ORIENTATION},
                {"software", ExifInterface.TAG_SOFTWARE}, {"image_description", ExifInterface.TAG_IMAGE_DESCRIPTION},
                {"copyright", ExifInterface.TAG_COPYRIGHT}, {"exposure_bias", ExifInterface.TAG_EXPOSURE_BIAS_VALUE},
                {"metering_mode", ExifInterface.TAG_METERING_MODE}
        };
        for (String[] pair : tags) putExisting(values, pair[0], attribute(exif, pair[1]));
        return values.length() == 0 ? notProvided("照片沒有可用的其他 EXIF 資料") : provided(values);
    }

    private static void putExisting(JSONObject object, String key, String value) throws Exception {
        if (value != null) object.put(key, value);
    }

    private static String safeBatchName(String value) {
        if (value == null) return "";
        String safe = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim().replaceAll("[. ]+$", "");
        if (safe.length() > 64) safe = safe.substring(0, 64).trim();
        return safe;
    }

    private static void writeJson(File file, String batchId, List<JSONObject> records, long pdfBytes,
                                  MetadataOptions options) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema_version", "1.2.0");
        root.put("batch_id", batchId);
        if (options.batchName != null && !options.batchName.trim().isEmpty()) root.put("batch_name", options.batchName.trim());
        root.put("created_at", OffsetDateTime.now().format(ISO_STAMP));
        root.put("image_count", records.size());
        root.put("ordering", new JSONObject().put("basis", "valid_exif_then_validated_filename_then_mediastore_then_document_modified_millis")
                .put("direction", "oldest_capture_first")
                .put("missing_time_policy", "unknown timestamps follow known timestamps in original selection order")
                .put("naming_policy", "generated_filename is derived from capture_time and sequence in this JSON index"));
        root.put("pdf", new JSONObject().put("file", "batch_001.pdf").put("page_count", records.size()).put("size_bytes", pdfBytes));
        JSONArray images = new JSONArray();
        for (JSONObject record : records) images.put(record);
        root.put("images", images);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write((root.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeReadme(File file, String batchId, int count) throws Exception {
        String text = String.format(Locale.US, """
                圖知 Android 照片整理 Beta

                批次編號：%s
                照片數量：%d

                ZIP 內容：
                - originals/：依拍攝時間命名的原始照片副本；照片位元組不經重壓。
                - batch_001.pdf：每張照片各一頁，維持方向及長寬比例。
                - batch_001.json：索引、來源檔名、拍攝時間來源、順序、SHA-256 與 PDF 頁碼。
                - README.txt：本說明。

                排序：有效 EXIF DateTimeOriginal 優先，其次是驗證過的檔名時間、MediaStore 拍攝時間及檔案修改時間（毫秒）；超出 2000–2100 或格式錯誤的時間會被忽略。仍無可信時間時 capture_time 為 null，並排在最後。
                命名：generated_filename 由 JSON 的 capture_time 和 sequence 產生；未知時間使用 undated 序號名，原始檔名另欄保存以供追溯。
                原始照片：以位元組複製，不修改來源照片。
                PDF：一張照片對應一頁，依方向選擇頁面比例，不裁切、不變形。
                """, batchId, count);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void createPdf(List<PhotoItem> photos, File originalsDir, File pdfFile) throws Exception {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        try {
            for (int i = 0; i < photos.size(); i++) {
                PhotoItem item = photos.get(i);
                File imageFile = new File(originalsDir, item.outputFilename);
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                if (bitmap == null) throw new IllegalStateException("無法解碼照片：" + item.originalFilename);
                Bitmap displayBitmap = applyExifOrientation(imageFile, bitmap);
                float scale = PDF_LONG_EDGE / (float) Math.max(displayBitmap.getWidth(), displayBitmap.getHeight());
                int pageWidth = Math.max(1, Math.round(displayBitmap.getWidth() * scale));
                int pageHeight = Math.max(1, Math.round(displayBitmap.getHeight() * scale));
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                page.getCanvas().drawBitmap(displayBitmap, null, new RectF(0, 0, pageWidth, pageHeight), paint);
                document.finishPage(page);
                if (displayBitmap != bitmap) displayBitmap.recycle();
                bitmap.recycle();
            }
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(pdfFile))) {
                document.writeTo(output);
            }
        } finally {
            document.close();
        }
    }

    private static Bitmap applyExifOrientation(File file, Bitmap source) {
        try (InputStream input = new FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(input);
            Matrix matrix = new Matrix();
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f);
                case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f);
                case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f);
                case ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(270f); }
                case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f);
                case ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setScale(-1f, 1f); matrix.postRotate(90f); }
                case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f);
                default -> { return source; }
            }
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (Exception ignored) {
            return source;
        }
    }

    private static void writeZip(File workDir, File zipFile, String batchId) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            addFile(zip, new File(workDir, "batch_001.pdf"), batchId + "/batch_001.pdf");
            addFile(zip, new File(workDir, "batch_001.json"), batchId + "/batch_001.json");
            addFile(zip, new File(workDir, "README.txt"), batchId + "/README.txt");
            File originals = new File(workDir, "originals");
            File[] files = originals.listFiles();
            if (files == null) throw new IllegalStateException("原始照片目錄為空。");
            for (File file : files) addFile(zip, file, batchId + "/originals/" + file.getName());
        }
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }

    private static void deleteTree(File root) {
        if (root == null || !root.exists()) return;
        File[] children = root.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        root.delete();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value & 0xff));
        return builder.toString();
    }
}
