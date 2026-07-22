/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * DroidBridge / FlintLauncher fork addition.
 *
 * Uploads a skin PNG to the official, publicly documented Mojang endpoint:
 * POST https://api.minecraftservices.com/minecraft/profile/skins
 * (multipart/form-data with "variant" and "file" fields). Same endpoint
 * used by the official Minecraft launcher.
 */

package ca.dnamobile.javalauncher.skin;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MicrosoftSkinUploader {

    private static final String UPLOAD_URL = "https://api.minecraftservices.com/minecraft/profile/skins";

    private MicrosoftSkinUploader() {
    }

    public static void uploadSkin(@NonNull String minecraftAccessToken, @NonNull File skinFile, @NonNull SkinModelType model)
            throws Exception {
        String boundary = "----FlintLauncherBoundary" + UUID.randomUUID();
        String variant = model == SkinModelType.SLIM ? "slim" : "classic";

        HttpURLConnection connection = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + minecraftAccessToken);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        try (OutputStream output = connection.getOutputStream()) {
            writeFormField(output, boundary, "variant", variant);
            writeFilePart(output, boundary, "file", skinFile);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            String errorBody = readErrorBody(connection);
            throw new IOException("Skin upload failed (HTTP " + status + ")"
                    + (errorBody.isEmpty() ? "" : ": " + errorBody));
        }
    }

    private static void writeFormField(@NonNull OutputStream output, @NonNull String boundary,
                                        @NonNull String name, @NonNull String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(@NonNull OutputStream output, @NonNull String boundary,
                                       @NonNull String fieldName, @NonNull File file) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"skin.png\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    private static String readErrorBody(@NonNull HttpURLConnection connection) {
        try (InputStream stream = connection.getErrorStream()) {
            if (stream == null) return "";
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
