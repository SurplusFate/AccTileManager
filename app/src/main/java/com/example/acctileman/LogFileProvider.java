package com.example.acctileman;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal FileProvider implementation for sharing log files via share intent.
 * Replaces androidx.core.content.FileProvider which is not available in manual builds.
 */
public class LogFileProvider extends ContentProvider {

    private static final String TAG = "LogFileProvider";

    public static final String AUTHORITY = "com.example.acctileman.fileprovider";

    private static final String[] DEFAULT_PROJECTION = {
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = uriToFile(uri);
        if (file == null || !file.exists()) {
            return null;
        }

        String[] cols = projection != null ? projection : DEFAULT_PROJECTION;
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = uriToFile(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File uriToFile(Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        // Security: only allow access to files in app external files dir
        File externalDir = getContext().getExternalFilesDir(null);
        if (externalDir != null && path.startsWith(externalDir.getAbsolutePath())) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        Log.w(TAG, "Blocked file access: " + path);
        return null;
    }

    /**
     * Convenience method to get a content URI for a file.
     */
    public static Uri getUriForFile(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path(file.getAbsolutePath())
                .build();
    }
}
