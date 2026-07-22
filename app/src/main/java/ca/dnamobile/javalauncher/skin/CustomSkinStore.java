/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 */

package ca.dnamobile.javalauncher.skin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import java.io.File;

public final class CustomSkinStore {

    private final File skinsDir;

    public CustomSkinStore(@NonNull Context context) {
        this.skinsDir = new File(context.getApplicationContext().getFilesDir(), "custom_skins");
        if (!skinsDir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            skinsDir.mkdirs();
        }
    }

    @NonNull
    public File getSkinsDir() {
        return skinsDir;
    }

    /**
     * A valid Minecraft skin PNG is square-ish at 64x64 (modern) or the
     * legacy 64x32 format. Anything else gets rejected before it's saved.
     */
    public static boolean isSkinValid(@NonNull File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int width = options.outWidth;
        int height = options.outHeight;
        if (width <= 0 || height <= 0) return false;
        return (width == 64 && (height == 64 || height == 32));
    }

    /**
     * Heuristic slim/classic detection, matching the convention used by
     * Mojang's own skin system: in a 64x64 skin, the right-arm overlay
     * region (44-47, 16-19) is fully transparent for slim ("Alex") skins
     * because that arm is one pixel narrower, and opaque for classic
     * ("Steve") skins. Defaults to CLASSIC if the skin is legacy 64x32
     * (which has no slim variant) or the check is inconclusive.
     */
    @NonNull
    public static SkinModelType getSkinModel(@NonNull File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null || bitmap.getHeight() < 64) {
            return SkinModelType.CLASSIC;
        }
        try {
            // Column 46 sits inside the 4th arm-overlay pixel column (44-47).
            // Slim skins leave this column transparent; classic skins don't.
            int pixel = bitmap.getPixel(46, 52);
            int alpha = (pixel >>> 24) & 0xFF;
            return alpha < 16 ? SkinModelType.SLIM : SkinModelType.CLASSIC;
        } catch (Throwable ignored) {
            return SkinModelType.CLASSIC;
        } finally {
            bitmap.recycle();
        }
    }

    public void deleteSkin(@NonNull String fileName) {
        //noinspection ResultOfMethodCallIgnored
        new File(skinsDir, fileName).delete();
    }
}
