package tw.ai_lab.screenshotbatch;

import android.net.Uri;

final class PhotoItem {
    final Uri uri;
    final String originalFilename;
    final String mimeType;
    final Long captureTimeMillis;
    final String captureTimeSource;
    long sizeBytes;
    final int widthPx;
    final int heightPx;
    final int selectionOrder;
    String outputFilename;
    String outputPath;
    String sha256;
    int sequence;

    PhotoItem(Uri uri, String originalFilename, String mimeType, Long captureTimeMillis,
              String captureTimeSource, long sizeBytes, int widthPx, int heightPx,
              int selectionOrder) {
        this.uri = uri;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.captureTimeMillis = captureTimeMillis;
        this.captureTimeSource = captureTimeSource;
        this.sizeBytes = sizeBytes;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.selectionOrder = selectionOrder;
    }
}
