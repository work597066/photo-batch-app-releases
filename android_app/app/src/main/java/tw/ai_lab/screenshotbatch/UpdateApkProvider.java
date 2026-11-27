package tw.ai_lab.screenshotbatch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Grants Android's package installer read-only access to a downloaded update APK. */
public final class UpdateApkProvider extends ContentProvider {
    static Uri uriFor(Context context, String filename) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".updates")
                .appendPath("updates")
                .appendPath(filename)
                .build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        File file = resolve(uri);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Update APKs are read-only.");
        File file = resolve(uri);
        if (!file.isFile()) throw new FileNotFoundException(file.getName());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider is not attached.");
        if (uri.getPathSegments().size() != 2 || !"updates".equals(uri.getPathSegments().get(0))) {
            throw new IllegalArgumentException("Invalid update path.");
        }
        File root = new File(context.getCacheDir(), "updates");
        File file = new File(root, uri.getPathSegments().get(1));
        try {
            if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
                throw new IllegalArgumentException("Invalid update path.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid update path.", e);
        }
        return file;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only provider"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only provider"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only provider"); }
}
