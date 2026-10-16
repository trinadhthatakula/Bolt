package com.valhalla.bolt.model;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Instrumentation APK only; uses no application or Kotlin runtime classes. */
public class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/zip"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {"../../kernel';echo injected;.zip", null});
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("null".equals(uri.getLastPathSegment())) return null;
        File file = new File(getContext().getCacheDir(), "fixture-" + uri.getLastPathSegment());
        try {
            if ("oversize".equals(uri.getLastPathSegment())) {
                try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[1024]); }
            } else {
                try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
                    for (String name : new String[] {"anykernel.sh", "META-INF/com/google/android/update-binary"}) {
                        output.putNextEntry(new ZipEntry(name));
                        output.write("#!/bin/sh\necho harmless-fixture\nexit 0\n".getBytes(StandardCharsets.UTF_8));
                        output.closeEntry();
                    }
                }
            }
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
