/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 */

package ca.dnamobile.javalauncher.skin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.dnamobile.javalauncher.R;
import ca.dnamobile.javalauncher.data.AccountStore;

public final class PlayerHeadLoader {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int HEAD_RENDER_SIZE_PX = 128;

    private PlayerHeadLoader() {
    }

    /**
     * Loads a rendered player head into the given ImageView. Resolves the
     * skin source from the account (local file for offline, remote URL for
     * Microsoft/server-auth), falls back to the placeholder drawable on any
     * failure or when account is null.
     */
    public static void loadInto(
            @NonNull android.content.Context context,
            @NonNull ImageView imageView,
            @Nullable AccountStore.Account account,
            @Nullable Runnable onLoaded
    ) {
        imageView.setImageResource(R.drawable.ic_player_head_placeholder);
        if (account == null) return;

        EXECUTOR.submit(() -> {
            Bitmap head = null;
            try {
                if (account.isOfflineAccount() && account.hasOfflineSkin()) {
                    head = renderHead(BitmapFactory.decodeFile(account.offlineSkinPath));
                } else if (account.skinUrl != null && !account.skinUrl.trim().isEmpty()) {
                    head = renderHead(downloadBitmap(account.skinUrl));
                }
            } catch (Throwable ignored) {
            }

            Bitmap finalHead = head;
            MAIN_HANDLER.post(() -> {
                if (finalHead != null) {
                    imageView.setImageBitmap(finalHead);
                }
                if (onLoaded != null) onLoaded.run();
            });
        });
    }

    /**
     * Synchronous head extraction for an already-decoded local skin file,
     * used for immediate dialog previews when picking a skin.
     */
    @Nullable
    public static Bitmap loadHeadFromSkinFile(@NonNull File file) {
        try {
            return renderHead(BitmapFactory.decodeFile(file.getAbsolutePath()));
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Bitmap renderHead(@Nullable Bitmap skin) {
        if (skin == null) return null;
        try {
            // Base face is at (8,8)-(16,16); hat overlay is at (40,8)-(48,16).
            Bitmap output = Bitmap.createBitmap(HEAD_RENDER_SIZE_PX, HEAD_RENDER_SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            Rect faceSrc = new Rect(8, 8, 16, 16);
            Rect fullDst = new Rect(0, 0, HEAD_RENDER_SIZE_PX, HEAD_RENDER_SIZE_PX);
            canvas.drawBitmap(skin, faceSrc, fullDst, null);

            if (skin.getHeight() >= 64) {
                Rect hatSrc = new Rect(40, 8, 48, 16);
                canvas.drawBitmap(skin, hatSrc, fullDst, null);
            }

            return output;
        } finally {
            skin.recycle();
        }
    }

    @Nullable
    private static Bitmap downloadBitmap(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                byte[] bytes = buffer.toByteArray();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
