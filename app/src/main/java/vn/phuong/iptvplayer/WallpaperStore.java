package vn.phuong.iptvplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Image copies and settings stay in app-private storage. */
final class WallpaperStore {
    static int style(Context context) { return context.getSharedPreferences("appearance", 0).getInt("wallpaper", 0); }
    static void setStyle(Context context, int value) { context.getSharedPreferences("appearance", 0).edit().putInt("wallpaper", value).apply(); }

    static Drawable load(Context context) {
        int style = style(context);
        if (style == 3) {
            Bitmap bitmap = BitmapFactory.decodeFile(new File(context.getFilesDir(), "wallpaper.jpg").getPath());
            if (bitmap != null) return new PhotoBackground(bitmap);
        }
        int[][] colors = {{0xff07111f, 0xff0d1c30}, {0xff062d39, 0xff071321}, {0xff291846, 0xff100e24}};
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[Math.max(0, Math.min(2, style))]);
    }

    static void importPhoto(Context context, Uri uri) throws Exception {
        File temp = File.createTempFile("wallpaper-import-", ".img", context.getCacheDir());
        Bitmap bitmap = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IllegalArgumentException("Không mở được ảnh");
                byte[] buffer = new byte[8192]; int read, total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > 32 * 1024 * 1024) throw new IllegalArgumentException("Ảnh lớn hơn 32 MB");
                    out.write(buffer, 0, read);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalArgumentException("Ảnh không hợp lệ");
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFile(temp.getPath(), options);
            if (bitmap == null) throw new IllegalArgumentException("Không giải mã được ảnh");
            try {
                int orientation = new ExifInterface(temp.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                Matrix matrix = new Matrix();
                if (orientation == 3 || orientation == 4) matrix.setRotate(180);
                if (orientation == 5 || orientation == 6) matrix.setRotate(90);
                if (orientation == 7 || orientation == 8) matrix.setRotate(270);
                if (orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7) matrix.postScale(-1, 1);
                if (!matrix.isIdentity()) {
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; }
                }
            } catch (java.io.IOException ignored) { }
            AtomicFile file = new AtomicFile(new File(context.getFilesDir(), "wallpaper.jpg"));
            FileOutputStream out = null;
            try {
                out = file.startWrite();
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw new IllegalArgumentException("Không lưu được ảnh");
                file.finishWrite(out);
            } catch (Exception error) { if (out != null) file.failWrite(out); throw error; }
            setStyle(context, 3);
        } finally { if (bitmap != null) bitmap.recycle(); temp.delete(); }
    }

    private static final class PhotoBackground extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        PhotoBackground(Bitmap bitmap) { this.bitmap = bitmap; }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.height() <= 0 || bounds.width() <= 0) return;
            float scale = Math.max((float) bounds.width() / bitmap.getWidth(), (float) bounds.height() / bitmap.getHeight());
            int width = Math.max(1, (int) (bounds.width() / scale)), height = Math.max(1, (int) (bounds.height() / scale));
            int left = (bitmap.getWidth() - width) / 2, top = (bitmap.getHeight() - height) / 2;
            canvas.drawBitmap(bitmap, new Rect(left, top, left + width, top + height), bounds, paint);
            canvas.drawColor(0xa607111f);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
