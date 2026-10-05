package com.example.firechat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Synthetic files served from the standalone instrumentation APK, using only Android APIs. */
public class PhotoFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "oriented".equals(uri.getLastPathSegment()) ? "image/jpeg" : "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        String kind = uri.getLastPathSegment();
        if (!Arrays.asList("scene", "second", "invalid", "oversize", "oriented", "large").contains(kind)) return 0;
        return new File(getContext().getCacheDir(), "photo-" + kind + ".fixture").delete() ? 1 : 0;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)) || !"r".equals(mode)) throw new FileNotFoundException("Private emulator fixture only");
        String kind = uri.getLastPathSegment();
        if (!Arrays.asList("scene", "second", "invalid", "oversize", "oriented", "large").contains(kind)) throw new FileNotFoundException("Unknown fixture");
        File file = new File(getContext().getCacheDir(), "photo-" + kind + ".fixture");
        try {
            if (!file.isFile()) create(file, kind);
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    private void create(File file, String kind) throws IOException {
        if ("invalid".equals(kind)) {
            try (FileOutputStream output = new FileOutputStream(file)) { output.write("Synthetic invalid image".getBytes()); }
        } else if ("oversize".equals(kind)) {
            try (FileOutputStream output = new FileOutputStream(file)) { byte[] chunk = new byte[1024 * 1024]; for (int i = 0; i < 26; i++) output.write(chunk); }
        } else {
            int width = "large".equals(kind) ? 3200 : 1280;
            int height = "large".equals(kind) ? 1800 : 720;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor("second".equals(kind) ? Color.rgb(180, 110, 180) : Color.rgb(90, 185, 235));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(255, 210, 90));
            canvas.drawCircle(width * .75f, height * .25f, height * .12f, paint);
            paint.setColor(Color.rgb(54, 130, 88));
            Path hills = new Path();
            hills.moveTo(0, height * .7f); hills.lineTo(width * .3f, height * .38f);
            hills.lineTo(width * .65f, height * .78f); hills.lineTo(width, height * .48f);
            hills.lineTo(width, height); hills.lineTo(0, height); hills.close(); canvas.drawPath(hills, paint);
            try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress("oriented".equals(kind) ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 100, output); }
            bitmap.recycle();
            if ("oriented".equals(kind)) {
                ExifInterface exif = new ExifInterface(file.getPath());
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(ExifInterface.ORIENTATION_ROTATE_90)); exif.saveAttributes();
            }
        }
    }
}
