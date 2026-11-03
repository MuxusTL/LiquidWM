package dev.liquidplus.watermark.engine;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.OptIn;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import dev.liquidplus.watermark.model.WatermarkConfig;

public class VideoWatermarkProcessor {

    public interface VideoProcessCallback {
        void onProgress(int progressPercent);
        void onComplete(Uri outputUri);
        void onError(String message);
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void processVideo(Context context, Uri inputUri, WatermarkConfig config, Bitmap customLogo, VideoProcessCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(context, inputUri);

                String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

                int rawWidth = widthStr != null ? Integer.parseInt(widthStr) : 1280;
                int rawHeight = heightStr != null ? Integer.parseInt(heightStr) : 720;
                int rotation = rotationStr != null ? Integer.parseInt(rotationStr) : 0;
                retriever.release();

                // Swapped dimensions for rotated videos so watermark matches upright orientation
                int width = (rotation == 90 || rotation == 270) ? rawHeight : rawWidth;
                int height = (rotation == 90 || rotation == 270) ? rawWidth : rawHeight;

                // Render transparent overlay with authentic Liquid Glass watermark
                Bitmap overlayBitmap = LiquidGlassRenderer.renderWatermarkOverlay(context, width, height, config, customLogo);
                if (overlayBitmap == null) {
                    mainHandler.post(() -> callback.onError("Không thể tạo lớp phủ Watermark"));
                    return;
                }

                File cacheDir = context.getCacheDir();
                File tempOutFile = new File(cacheDir, "watermarked_" + System.currentTimeMillis() + ".mp4");

                mainHandler.post(() -> {
                    try {
                        TextureOverlay textureOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
                        OverlayEffect overlayEffect = new OverlayEffect(ImmutableList.of(textureOverlay));
                        Effect effect = overlayEffect;

                        List<AudioProcessor> audioProcessors = ImmutableList.of();
                        List<Effect> videoEffects = ImmutableList.of(effect);

                        EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                                .setEffects(new Effects(audioProcessors, videoEffects))
                                .build();

                        ProgressHolder progressHolder = new ProgressHolder();
                        final Runnable[] progressPollRef = new Runnable[1];

                        Transformer.Listener listener = new Transformer.Listener() {
                            @Override
                            public void onCompleted(Composition composition, ExportResult exportResult) {
                                if (progressPollRef[0] != null) {
                                    mainHandler.removeCallbacks(progressPollRef[0]);
                                }
                                new Thread(() -> {
                                    Uri finalUri = saveVideoToGallery(context, tempOutFile);
                                    if (tempOutFile.exists()) tempOutFile.delete();
                                    if (!overlayBitmap.isRecycled()) overlayBitmap.recycle();
                                    mainHandler.post(() -> {
                                        callback.onProgress(100);
                                        callback.onComplete(finalUri);
                                    });
                                }).start();
                            }

                            @Override
                            public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                                if (progressPollRef[0] != null) {
                                    mainHandler.removeCallbacks(progressPollRef[0]);
                                }
                                if (tempOutFile.exists()) tempOutFile.delete();
                                if (!overlayBitmap.isRecycled()) overlayBitmap.recycle();
                                mainHandler.post(() -> callback.onError(exportException != null ? exportException.getMessage() : "Export error"));
                            }
                        };

                        Transformer transformer = new Transformer.Builder(context)
                                .addListener(listener)
                                .build();

                        progressPollRef[0] = new Runnable() {
                            @Override
                            public void run() {
                                int state = transformer.getProgress(progressHolder);
                                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                    callback.onProgress(progressHolder.progress);
                                }
                                mainHandler.postDelayed(this, 300);
                            }
                        };
                        mainHandler.post(progressPollRef[0]);

                        transformer.start(editedMediaItem, tempOutFile.getAbsolutePath());

                    } catch (Exception ex) {
                        if (tempOutFile.exists()) tempOutFile.delete();
                        if (!overlayBitmap.isRecycled()) overlayBitmap.recycle();
                        callback.onError(ex.getMessage());
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    public static Uri saveVideoToGallery(Context context, File sourceFile) {
        String filename = "LiquidWatermark_" + System.currentTimeMillis() + ".mp4";
        Uri videoUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LiquidWatermark");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);

                videoUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (videoUri != null) {
                    try (OutputStream fos = context.getContentResolver().openOutputStream(videoUri);
                         InputStream fis = new FileInputStream(sourceFile)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        fos.flush();
                    }
                    values.clear();
                    values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    context.getContentResolver().update(videoUri, values, null, null);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LiquidWatermark");
                if (!dir.exists()) dir.mkdirs();
                File videoFile = new File(dir, filename);
                try (OutputStream fos = new FileOutputStream(videoFile);
                     InputStream fis = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                    fos.flush();
                }
                videoUri = Uri.fromFile(videoFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videoUri;
    }
}
