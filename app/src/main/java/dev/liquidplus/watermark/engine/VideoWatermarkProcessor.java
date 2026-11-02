package dev.liquidplus.watermark.engine;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import dev.liquidplus.watermark.model.WatermarkConfig;

public class VideoWatermarkProcessor {

    public interface VideoProcessCallback {
        void onProgress(int progressPercent);
        void onComplete(Uri outputUri);
        void onError(String message);
    }

    public static void processVideo(Context context, Uri inputUri, WatermarkConfig config, Bitmap customLogo, VideoProcessCallback callback) {
        new Thread(() -> {
            MediaExtractor extractor = null;
            MediaMuxer muxer = null;
            File tempOutFile = null;

            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(context, inputUri);

                String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                int width = widthStr != null ? Integer.parseInt(widthStr) : 1280;
                int height = heightStr != null ? Integer.parseInt(heightStr) : 720;
                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;

                retriever.release();

                // Setup output file
                File cacheDir = context.getCacheDir();
                tempOutFile = new File(cacheDir, "watermarked_" + System.currentTimeMillis() + ".mp4");

                extractor = new MediaExtractor();
                extractor.setDataSource(context, inputUri, null);

                muxer = new MediaMuxer(tempOutFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                int videoTrackIndex = -1;
                int audioTrackIndex = -1;
                int muxerVideoTrack = -1;
                int muxerAudioTrack = -1;

                int trackCount = extractor.getTrackCount();
                for (int i = 0; i < trackCount; i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null) {
                        if (mime.startsWith("video/") && videoTrackIndex == -1) {
                            videoTrackIndex = i;
                            muxerVideoTrack = muxer.addTrack(format);
                        } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                            audioTrackIndex = i;
                            muxerAudioTrack = muxer.addTrack(format);
                        }
                    }
                }

                muxer.start();

                // Stream audio track directly
                if (audioTrackIndex != -1) {
                    extractor.selectTrack(audioTrackIndex);
                    ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    while (true) {
                        info.size = extractor.readSampleData(buffer, 0);
                        if (info.size < 0) break;
                        info.presentationTimeUs = extractor.getSampleTime();
                        info.flags = extractor.getSampleFlags();
                        info.offset = 0;
                        muxer.writeSampleData(muxerAudioTrack, buffer, info);
                        extractor.advance();
                    }
                    extractor.unselectTrack(audioTrackIndex);
                }

                // Process video track
                if (videoTrackIndex != -1) {
                    extractor.selectTrack(videoTrackIndex);
                    ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    long totalDurationUs = durationMs * 1000L;

                    while (true) {
                        info.size = extractor.readSampleData(buffer, 0);
                        if (info.size < 0) break;
                        info.presentationTimeUs = extractor.getSampleTime();
                        info.flags = extractor.getSampleFlags();
                        info.offset = 0;
                        muxer.writeSampleData(muxerVideoTrack, buffer, info);

                        if (totalDurationUs > 0) {
                            int progress = (int) Math.min(98, (info.presentationTimeUs * 100) / totalDurationUs);
                            callback.onProgress(progress);
                        }
                        extractor.advance();
                    }
                    extractor.unselectTrack(videoTrackIndex);
                }

                muxer.stop();
                muxer.release();
                muxer = null;

                extractor.release();
                extractor = null;

                // Save to gallery Movies/LiquidWatermark
                Uri finalUri = saveVideoToGallery(context, tempOutFile);
                if (tempOutFile.exists()) tempOutFile.delete();

                callback.onProgress(100);
                callback.onComplete(finalUri);

            } catch (Exception e) {
                e.printStackTrace();
                if (muxer != null) {
                    try { muxer.release(); } catch (Exception ignored) {}
                }
                if (extractor != null) {
                    try { extractor.release(); } catch (Exception ignored) {}
                }
                if (tempOutFile != null && tempOutFile.exists()) tempOutFile.delete();
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static Uri saveVideoToGallery(Context context, File sourceFile) {
        String filename = "LiquidWatermark_" + System.currentTimeMillis() + ".mp4";
        Uri videoUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LiquidWatermark");

                videoUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (videoUri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(videoUri);
                         java.io.FileInputStream in = new java.io.FileInputStream(sourceFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LiquidWatermark");
                if (!dir.exists()) dir.mkdirs();
                File destFile = new File(dir, filename);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(destFile);
                     java.io.FileInputStream in = new java.io.FileInputStream(sourceFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                videoUri = Uri.fromFile(destFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return videoUri;
    }
}
