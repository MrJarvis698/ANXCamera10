package com.ss.android.medialib;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Pair;
import android.view.Surface;
import com.google.android.gms.common.Scopes;
import com.ss.android.medialib.common.TextureDrawer;
import com.ss.android.medialib.log.VEMonitorKeys;
import com.ss.android.ttve.monitor.TEMonitor;
import com.ss.android.ttve.monitor.TEMonitorNewKeys;
import com.ss.android.vesdk.VELogUtil;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;

@TargetApi(18)
public class AVCEncoder {
    private static final String[] BITRATE_MODES = {"BITRATE_MODE_CQ", "BITRATE_MODE_VBR", "BITRATE_MODE_CBR"};
    private static final boolean DEBUG = true;
    private static final String TAG = "AVCEncoder";
    private static int TIMEOUT_USEC = 5000;
    static AVCEncoderInterface mEncoderCaller = null;
    private byte[] codec_config;
    BufferedOutputStream fileWriter = null;
    ByteBuffer[] inputBuffers;
    MediaCodec.BufferInfo mBufferInfo = null;
    private String mCodecName = "video/avc";
    private int mColorFormat;
    private int mDrawCount = 0;
    private int mEncodeCount = 0;
    private boolean mFlag = false;
    int mFrameRate = 30;
    private int mHeight;
    private boolean mIsError = false;
    private MediaCodec mMediaCodec = null;
    private MediaCodecInfo mMediaCodecInfo = null;
    private Queue<Pair<Integer, Integer>> mPTSQueue = new LinkedList();
    private int mProfile = 1;
    private Surface mSurface;
    private TextureDrawer mTextureDrawer;
    private int mWidth;
    ByteBuffer[] outputBuffers;
    int status = 0;

    private interface Status {
        public static final int INITED = 1;
        public static final int STARTED = 2;
        public static final int STOPPED = 3;
        public static final int UNSET = 0;
    }

    static {
        new Thread(new Runnable() {
            public void run() {
                synchronized (AVCEncoder.class) {
                    MediaCodecList.getCodecCount();
                }
            }
        }).start();
    }

    private MediaCodecInfo getMediaCodecInfo() {
        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; i++) {
            MediaCodecInfo codecInfoAt = MediaCodecList.getCodecInfoAt(i);
            if (codecInfoAt.isEncoder()) {
                String name = codecInfoAt.getName();
                if (!name.startsWith("OMX.google.") && !name.startsWith("OMX.Nvidia.") && !name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                    String[] supportedTypes = codecInfoAt.getSupportedTypes();
                    for (String equalsIgnoreCase : supportedTypes) {
                        if (equalsIgnoreCase.equalsIgnoreCase(this.mCodecName)) {
                            return codecInfoAt;
                        }
                    }
                    continue;
                }
            }
        }
        return null;
    }

    @TargetApi(21)
    private MediaCodecInfo getMediaCodecInfo21() {
        MediaCodecInfo[] codecInfos = new MediaCodecList(1).getCodecInfos();
        if (!(codecInfos == null || codecInfos.length == 0)) {
            for (MediaCodecInfo mediaCodecInfo : codecInfos) {
                if (mediaCodecInfo != null && mediaCodecInfo.isEncoder()) {
                    String name = mediaCodecInfo.getName();
                    if (!name.startsWith("OMX.google.") && !name.startsWith("OMX.Nvidia.") && !name.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                        String[] supportedTypes = mediaCodecInfo.getSupportedTypes();
                        for (String equalsIgnoreCase : supportedTypes) {
                            if (equalsIgnoreCase.equalsIgnoreCase(this.mCodecName)) {
                                return mediaCodecInfo;
                            }
                        }
                        continue;
                    }
                }
            }
        }
        return null;
    }

    private int getOneColorFormat() {
        int[] colorFormats = getColorFormats();
        if (colorFormats == null) {
            return -1;
        }
        for (int i = 0; i < colorFormats.length; i++) {
            if (colorFormats[i] == 2130708361) {
                VELogUtil.i(TAG, "====== mColorFormat support COLOR_FormatSurface ======");
                return colorFormats[i];
            }
        }
        return -1;
    }

    public static void setDrainWaitTimeout(int i) {
        TIMEOUT_USEC = i;
    }

    private void testCode(boolean z) {
        ByteBuffer order = ByteBuffer.allocateDirect(this.mWidth * this.mHeight * 4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, this.mWidth, this.mHeight, 6408, 5121, order);
        try {
            if (this.fileWriter == null) {
                this.fileWriter = new BufferedOutputStream(new FileOutputStream("/storage/emulated/0/xzw/rgbaBig.rgba"));
            }
            this.fileWriter.write(order.array());
            try {
                if (this.fileWriter != null) {
                    this.fileWriter.close();
                }
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        } catch (IOException e3) {
            e3.printStackTrace();
            if (this.fileWriter != null) {
                this.fileWriter.close();
            }
        } catch (Throwable th) {
            try {
                if (this.fileWriter != null) {
                    this.fileWriter.close();
                }
            } catch (IOException e4) {
                e4.printStackTrace();
            }
            throw th;
        }
        Bitmap createBitmap = Bitmap.createBitmap(this.mWidth, this.mHeight, Bitmap.Config.ARGB_8888);
        createBitmap.copyPixelsFromBuffer(order);
        if (z) {
            saveBitmap(createBitmap, "/sdcard/aweme/picture/record_e.jpeg");
        } else {
            saveBitmap(createBitmap, "/sdcard/aweme/picture/record_s.jpeg");
        }
        createBitmap.recycle();
    }

    public synchronized void createEncoder() {
        if (this.status == 0) {
            this.mColorFormat = getOneColorFormat();
            if (this.mColorFormat >= 0) {
                try {
                    this.mMediaCodec = MediaCodec.createEncoderByType(this.mCodecName);
                    MediaCodecInfo codecInfo = this.mMediaCodec.getCodecInfo();
                    if (!codecInfo.getName().startsWith("OMX.google.")) {
                        for (String str : codecInfo.getSupportedTypes()) {
                            VELogUtil.i(TAG, "CodecNames: " + str);
                        }
                        this.status = 1;
                    }
                } catch (IOException e2) {
                    VELogUtil.e(TAG, "createEncoderByTyp: " + e2.getMessage());
                }
            }
        }
    }

    /* JADX WARNING: Can't wrap try/catch for region: R(6:22|23|24|25|26|27) */
    /* JADX WARNING: Code restructure failed: missing block: B:53:0x0137, code lost:
        if (r9 >= 0) goto L_0x0147;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:54:0x0139, code lost:
        com.ss.android.vesdk.VELogUtil.d(TAG, "encode: error.");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:58:0x0146, code lost:
        return 0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:60:0x0149, code lost:
        if (android.os.Build.VERSION.SDK_INT < 21) goto L_0x0152;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:61:0x014b, code lost:
        r4 = r1.mMediaCodec.getOutputBuffer(r9);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:62:0x0152, code lost:
        r4 = r1.outputBuffers[r9];
     */
    /* JADX WARNING: Code restructure failed: missing block: B:63:0x0156, code lost:
        r11 = r4;
        r11.position(r1.mBufferInfo.offset);
        r11.limit(r1.mBufferInfo.offset + r1.mBufferInfo.size);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:64:0x016f, code lost:
        if ((r1.mBufferInfo.flags & 2) == 0) goto L_0x0187;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:65:0x0171, code lost:
        com.ss.android.vesdk.VELogUtil.d(TAG, "mEncoderCaller.onSetCodecConfig");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:66:0x017a, code lost:
        if (mEncoderCaller == null) goto L_0x0181;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:67:0x017c, code lost:
        mEncoderCaller.onSetCodecConfig(r11);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:68:0x0181, code lost:
        r1.mBufferInfo.size = 0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:70:0x018c, code lost:
        if ((r1.mBufferInfo.flags & 1) == 0) goto L_0x0191;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:71:0x018e, code lost:
        r17 = 1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:72:0x0191, code lost:
        r17 = 0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:73:0x0193, code lost:
        com.ss.android.vesdk.VELogUtil.d(TAG, "mEncoderCaller.onWriteFile");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:74:0x019c, code lost:
        if (mEncoderCaller == null) goto L_0x0200;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:75:0x019e, code lost:
        com.ss.android.vesdk.VELogUtil.d(TAG, "encode: pts queue size = " + r1.mPTSQueue.size());
     */
    /* JADX WARNING: Code restructure failed: missing block: B:76:0x01c0, code lost:
        if (r1.mPTSQueue.size() <= 0) goto L_0x01f8;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:77:0x01c2, code lost:
        r1.mEncodeCount++;
        r4 = r1.mPTSQueue.poll();
        r14 = 0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:78:0x01d7, code lost:
        if (r1.mBufferInfo.presentationTimeUs <= 0) goto L_0x01dd;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:79:0x01d9, code lost:
        r14 = r1.mBufferInfo.presentationTimeUs;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:80:0x01dd, code lost:
        mEncoderCaller.onWriteFile(r11, r14 / 1000, (long) ((java.lang.Integer) r4.first).intValue(), ((java.lang.Integer) r4.second).intValue(), r17);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:81:0x01f8, code lost:
        com.ss.android.vesdk.VELogUtil.w(TAG, "encode: no available pts!!!");
     */
    /* JADX WARNING: Code restructure failed: missing block: B:82:0x0200, code lost:
        com.ss.android.vesdk.VELogUtil.d(TAG, "encode: no output.");
     */
    /* JADX WARNING: Missing exception handler attribute for start block: B:24:0x00b7 */
    public int encode(int i, int i2, int i3, boolean z) {
        int i4;
        int dequeueOutputBuffer;
        int i5 = i;
        boolean z2 = z;
        VELogUtil.d(TAG, "encodeTexture::texID: " + i5 + " pts: " + i4 + " duration:" + i3 + "  isEndStream = " + z2);
        synchronized (this) {
            if (this.status == 2) {
                if (this.mMediaCodec != null) {
                    if (i5 > 0) {
                        if (i4 >= 0) {
                            if (this.mTextureDrawer == null && !initEGLCtx()) {
                                return -1;
                            }
                            this.mPTSQueue.offer(Pair.create(Integer.valueOf(i2), Integer.valueOf(i3)));
                            GLES20.glViewport(0, 0, this.mWidth, this.mHeight);
                            VELogUtil.d(TAG, "encode: width = " + this.mWidth + " height = " + this.mHeight);
                            this.mTextureDrawer.drawTexture(i5);
                            GLES20.glFinish();
                            this.mDrawCount = this.mDrawCount + 1;
                            mEncoderCaller.onSwapGlBuffers();
                            if (this.mFlag) {
                                testCode(z2);
                                this.mFlag = false;
                            }
                            if (z2) {
                                this.mMediaCodec.signalEndOfInputStream();
                                this.mIsError = true;
                                return -2;
                            }
                            loop0:
                            while (true) {
                                int i6 = 0;
                                while (true) {
                                    i6++;
                                    try {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            this.outputBuffers = this.mMediaCodec.getOutputBuffers();
                                        }
                                        dequeueOutputBuffer = this.mMediaCodec.dequeueOutputBuffer(this.mBufferInfo, (long) TIMEOUT_USEC);
                                        VELogUtil.d(TAG, "outputBufferIndex = " + dequeueOutputBuffer);
                                        VELogUtil.d(TAG, "mBufferInfo.flags = " + this.mBufferInfo.flags);
                                        if (dequeueOutputBuffer != -1) {
                                            if (dequeueOutputBuffer != -3) {
                                                if (dequeueOutputBuffer != -2) {
                                                    break;
                                                }
                                                VELogUtil.d(TAG, "encode: output format change!");
                                            } else {
                                                this.outputBuffers = this.mMediaCodec.getOutputBuffers();
                                            }
                                        } else {
                                            if (z2 && TIMEOUT_USEC < 5000) {
                                                TIMEOUT_USEC = 10000;
                                            }
                                            if (!z2 || this.mDrawCount == this.mEncodeCount || i6 >= 10) {
                                                break loop0;
                                            }
                                        }
                                    } catch (Throwable unused) {
                                        this.mIsError = true;
                                        return -3;
                                    }
                                }
                                this.mMediaCodec.releaseOutputBuffer(dequeueOutputBuffer, false);
                            }
                            if (z2) {
                                releaseEGLCtx();
                            }
                        }
                    }
                    VELogUtil.e(TAG, "encode: invalidate params: texID = " + i5 + ", pts = " + i4);
                    return -1;
                }
            }
            VELogUtil.w(TAG, "encode: codec is not ready.");
            return -1;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:63:0x01ab, code lost:
        return 0;
     */
    public int encode(byte[] bArr, int i, boolean z) {
        byte[] bArr2 = bArr;
        int i2 = i;
        boolean z2 = z;
        synchronized (this) {
            if (this.status == 2) {
                if (this.mMediaCodec != null) {
                    VELogUtil.d(TAG, "encodeBuffer pts: " + i2 + "  isEndStream = " + z2);
                    if (Build.VERSION.SDK_INT >= 21) {
                        int dequeueInputBuffer = this.mMediaCodec.dequeueInputBuffer(-1);
                        if (dequeueInputBuffer >= 0) {
                            ByteBuffer inputBuffer = this.mMediaCodec.getInputBuffer(dequeueInputBuffer);
                            inputBuffer.clear();
                            inputBuffer.put(bArr2, 0, bArr2.length);
                            this.mMediaCodec.queueInputBuffer(dequeueInputBuffer, 0, bArr2.length, (long) i2, z2 ? 4 : 0);
                        }
                        VELogUtil.d(TAG, "outputBufferIndex = " + r0);
                        VELogUtil.d(TAG, "mBufferInfo.flags = " + this.mBufferInfo.flags);
                        for (int dequeueOutputBuffer = this.mMediaCodec.dequeueOutputBuffer(this.mBufferInfo, (long) TIMEOUT_USEC); dequeueOutputBuffer >= 0; dequeueOutputBuffer = this.mMediaCodec.dequeueOutputBuffer(this.mBufferInfo, 0)) {
                            ByteBuffer outputBuffer = this.mMediaCodec.getOutputBuffer(dequeueOutputBuffer);
                            outputBuffer.position(this.mBufferInfo.offset);
                            outputBuffer.limit(this.mBufferInfo.offset + this.mBufferInfo.size);
                            if ((this.mBufferInfo.flags & 2) != 0) {
                                VELogUtil.d(TAG, "mEncoderCaller.onSetCodecConfig");
                                if (mEncoderCaller != null) {
                                    mEncoderCaller.onSetCodecConfig(outputBuffer);
                                }
                                this.mBufferInfo.size = 0;
                            } else {
                                int i3 = (int) this.mBufferInfo.presentationTimeUs;
                                int i4 = (this.mBufferInfo.flags & 1) != 0 ? 1 : 0;
                                VELogUtil.d(TAG, "mEncoderCaller.onWriteFile");
                                if (mEncoderCaller != null) {
                                    mEncoderCaller.onWriteFile(outputBuffer, i3, 0, i4);
                                }
                            }
                            this.mMediaCodec.releaseOutputBuffer(dequeueOutputBuffer, false);
                        }
                    } else {
                        int dequeueInputBuffer2 = this.mMediaCodec.dequeueInputBuffer(-1);
                        if (dequeueInputBuffer2 >= 0) {
                            ByteBuffer byteBuffer = this.inputBuffers[dequeueInputBuffer2];
                            byteBuffer.clear();
                            byteBuffer.put(bArr2, 0, bArr2.length);
                            this.mMediaCodec.queueInputBuffer(dequeueInputBuffer2, 0, bArr2.length, (long) i2, z2 ? 4 : 0);
                        }
                        for (int dequeueOutputBuffer2 = this.mMediaCodec.dequeueOutputBuffer(this.mBufferInfo, (long) TIMEOUT_USEC); dequeueOutputBuffer2 >= 0; dequeueOutputBuffer2 = this.mMediaCodec.dequeueOutputBuffer(this.mBufferInfo, 0)) {
                            if (dequeueOutputBuffer2 == -3) {
                                this.outputBuffers = this.mMediaCodec.getOutputBuffers();
                            } else if (dequeueOutputBuffer2 != -2) {
                                ByteBuffer byteBuffer2 = this.outputBuffers[dequeueOutputBuffer2];
                                byteBuffer2.position(this.mBufferInfo.offset);
                                byteBuffer2.limit(this.mBufferInfo.offset + this.mBufferInfo.size);
                                if ((this.mBufferInfo.flags & 2) != 0) {
                                    VELogUtil.d(TAG, "mEncoderCaller.onSetCodecConfig");
                                    if (mEncoderCaller != null) {
                                        mEncoderCaller.onSetCodecConfig(byteBuffer2);
                                    }
                                    this.mBufferInfo.size = 0;
                                } else {
                                    int i5 = (int) this.mBufferInfo.presentationTimeUs;
                                    int i6 = (this.mBufferInfo.flags & 1) != 0 ? 1 : 0;
                                    VELogUtil.d(TAG, "mEncoderCaller.onWriteFile");
                                    if (mEncoderCaller != null) {
                                        mEncoderCaller.onWriteFile(byteBuffer2, i5, 0, i6);
                                    }
                                }
                                this.mMediaCodec.releaseOutputBuffer(dequeueOutputBuffer2, false);
                            }
                        }
                    }
                }
            }
            return -1;
        }
    }

    public int[] getColorFormats() {
        VELogUtil.i(TAG, "start == ");
        this.mMediaCodecInfo = Build.VERSION.SDK_INT >= 21 ? getMediaCodecInfo21() : getMediaCodecInfo();
        VELogUtil.i(TAG, "end == ");
        if (this.mMediaCodecInfo == null) {
            return null;
        }
        VELogUtil.i(TAG, "mMediaCodecInfo name = " + this.mMediaCodecInfo.getName());
        MediaCodecInfo.CodecCapabilities capabilitiesForType = this.mMediaCodecInfo.getCapabilitiesForType(this.mCodecName);
        int length = capabilitiesForType.colorFormats.length;
        int[] iArr = new int[length];
        for (int i = 0; i < length; i++) {
            iArr[i] = capabilitiesForType.colorFormats[i];
        }
        return iArr;
    }

    public int getProfile() {
        return this.mProfile;
    }

    public Surface initAVCEncoder(int i, int i2, int i3, int i4) {
        return initAVCEncoder(i, i2, i3, 1, 8, i4, true);
    }

    /* JADX WARNING: Code restructure failed: missing block: B:36:0x0118, code lost:
        if (r13.profile < 8) goto L_0x011a;
     */
    /* JADX WARNING: Removed duplicated region for block: B:41:0x0120 A[Catch:{ Exception -> 0x0321 }, LOOP:0: B:24:0x00c7->B:41:0x0120, LOOP_END] */
    /* JADX WARNING: Removed duplicated region for block: B:92:0x011f A[SYNTHETIC] */
    public Surface initAVCEncoder(int i, int i2, int i3, int i4, int i5, int i6, boolean z) {
        int i7;
        float f2;
        float f3;
        int i8 = i;
        int i9 = i2;
        int i10 = i3;
        int i11 = i4;
        int i12 = i5;
        if (!z || Build.VERSION.SDK_INT < 18) {
            return null;
        }
        this.mDrawCount = 0;
        this.mEncodeCount = 0;
        VELogUtil.i(TAG, "initAVCEncoder == enter");
        if (i11 < 0 || i11 > 2) {
            VELogUtil.i(TAG, "Do not support bitrate mode " + i11 + ", set VBR mode");
            i11 = 1;
        }
        if (i12 < 1 || i12 > 64) {
            VELogUtil.i(TAG, "Do not support profile " + i12 + ", use baseline");
            i12 = 1;
        }
        VELogUtil.i(TAG, "width + " + i8 + "\theight = " + i9 + "\tbitrate = " + i10 + "\tuseTextureInput = " + r6);
        if (i8 <= 0 || i9 <= 0) {
            return null;
        }
        this.mWidth = i8;
        this.mHeight = i9;
        synchronized (this) {
            try {
                createEncoder();
                if (mEncoderCaller != null) {
                    mEncoderCaller.setColorFormat(this.mColorFormat);
                }
                MediaFormat createVideoFormat = MediaFormat.createVideoFormat(this.mCodecName, i8, i9);
                MediaCodecInfo.CodecCapabilities capabilitiesForType = this.mMediaCodec.getCodecInfo().getCapabilitiesForType(this.mCodecName);
                MediaCodecInfo.CodecProfileLevel[] codecProfileLevelArr = capabilitiesForType.profileLevels;
                int length = codecProfileLevelArr.length;
                MediaCodecInfo.CodecProfileLevel codecProfileLevel = null;
                int i13 = 0;
                while (true) {
                    if (i13 >= length) {
                        break;
                    }
                    MediaCodecInfo.CodecProfileLevel codecProfileLevel2 = codecProfileLevelArr[i13];
                    VELogUtil.i(TAG, "Profile = " + codecProfileLevel2.profile + ", Level = " + codecProfileLevel2.level);
                    if (codecProfileLevel2.profile == 1) {
                        VELogUtil.i(TAG, "Support Baseline Profile!");
                    } else if (codecProfileLevel2.profile == 2) {
                        VELogUtil.i(TAG, "Support Main Profile!");
                        if (codecProfileLevel.profile < 2) {
                        }
                        if (codecProfileLevel2.profile == i12) {
                            break;
                        }
                        i13++;
                    } else {
                        if (codecProfileLevel2.profile == 8) {
                            VELogUtil.i(TAG, "Support High Profile!");
                        }
                        if (codecProfileLevel2.profile == i12) {
                        }
                    }
                    codecProfileLevel = codecProfileLevel2;
                    if (codecProfileLevel2.profile == i12) {
                    }
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    MediaCodecInfo.EncoderCapabilities encoderCapabilities = capabilitiesForType.getEncoderCapabilities();
                    for (int i14 = 0; i14 < 3; i14++) {
                        VELogUtil.d(TAG, BITRATE_MODES[i14] + ": " + encoderCapabilities.isBitrateModeSupported(i14));
                    }
                }
                if (Build.VERSION.SDK_INT < 24 || codecProfileLevel == null) {
                    VELogUtil.w(TAG, "Do not support profile " + i12 + ", use baseline");
                    TEMonitor.perfLong(0, TEMonitorNewKeys.TE_MEDIACODEC_PROFILE, 1);
                    TEMonitor.perfLong(VEMonitorKeys.IESMMTRACKER_KEY_RECORD_MEDIACODEC_PROFILE, 1);
                    i7 = i10;
                } else {
                    VELogUtil.i(TAG, "Set Profile: " + codecProfileLevel.profile + ", Level = " + codecProfileLevel.level);
                    this.mProfile = codecProfileLevel.profile;
                    createVideoFormat.setInteger(Scopes.PROFILE, codecProfileLevel.profile);
                    createVideoFormat.setInteger("level", codecProfileLevel.level);
                    int i15 = codecProfileLevel.profile;
                    if (i15 == 2) {
                        VELogUtil.i(TAG, "Set Main Profile");
                        f3 = (float) i10;
                        f2 = 0.85f;
                    } else if (i15 != 8) {
                        i7 = i10;
                        TEMonitor.perfLong(0, TEMonitorNewKeys.TE_MEDIACODEC_PROFILE, (long) codecProfileLevel.profile);
                        TEMonitor.perfLong(VEMonitorKeys.IESMMTRACKER_KEY_RECORD_MEDIACODEC_PROFILE, (long) codecProfileLevel.profile);
                    } else {
                        VELogUtil.i(TAG, "Set High Profile");
                        f3 = (float) i10;
                        f2 = 0.75f;
                    }
                    i7 = (int) (f3 * f2);
                    TEMonitor.perfLong(0, TEMonitorNewKeys.TE_MEDIACODEC_PROFILE, (long) codecProfileLevel.profile);
                    TEMonitor.perfLong(VEMonitorKeys.IESMMTRACKER_KEY_RECORD_MEDIACODEC_PROFILE, (long) codecProfileLevel.profile);
                }
                if (i7 > 12000000) {
                    i7 = 12000000;
                }
                VELogUtil.i(TAG, "bitrate = " + ((((float) i7) * 1.0f) / 1000000.0f) + "Mb/s");
                StringBuilder sb = new StringBuilder();
                sb.append("speed = ");
                sb.append(i6);
                VELogUtil.i(TAG, sb.toString());
                createVideoFormat.setInteger("bitrate", i7);
                if (Build.VERSION.SDK_INT >= 21) {
                    MediaCodecInfo.EncoderCapabilities encoderCapabilities2 = capabilitiesForType.getEncoderCapabilities();
                    for (int i16 = 0; i16 < 3; i16++) {
                        VELogUtil.i(TAG, BITRATE_MODES[i16] + ": " + encoderCapabilities2.isBitrateModeSupported(i16));
                    }
                    createVideoFormat.setInteger("bitrate-mode", i11);
                    VELogUtil.i(TAG, "Bitrate mode = " + i11);
                    TEMonitor.perfLong(0, TEMonitorNewKeys.TE_RECORD_MEDIACODEC_RATE_CONTROL, (long) i11);
                    createVideoFormat.setInteger("max-bitrate", i10);
                    VELogUtil.i(TAG, "Encoder ComplexityRange: " + encoderCapabilities2.getComplexityRange().toString());
                }
                createVideoFormat.setInteger("color-format", this.mColorFormat);
                createVideoFormat.setInteger("frame-rate", this.mFrameRate);
                createVideoFormat.setInteger("i-frame-interval", 1);
                TEMonitor.perfLong(0, TEMonitorNewKeys.TE_RECORD_VIDEO_ENCODE_GOP, (long) (this.mFrameRate * 1));
                VELogUtil.i(TAG, "initAVCEncoder: format = " + createVideoFormat);
                this.mMediaCodec.configure(createVideoFormat, (Surface) null, (MediaCrypto) null, 1);
                this.mSurface = this.mMediaCodec.createInputSurface();
                this.mMediaCodec.start();
                this.status = 2;
                if (Build.VERSION.SDK_INT < 21) {
                    this.inputBuffers = this.mMediaCodec.getInputBuffers();
                    this.outputBuffers = this.mMediaCodec.getOutputBuffers();
                }
                this.mBufferInfo = new MediaCodec.BufferInfo();
                if (this.mSurface == null) {
                    return null;
                }
                VELogUtil.i(TAG, "initAVCEncoder == exit");
                return this.mSurface;
            } catch (Exception e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    public Surface initAVCEncoder(int i, int i2, int i3, int i4, boolean z) {
        return initAVCEncoder(i, i2, i3, 1, 1, i4, z);
    }

    public boolean initEGLCtx() {
        if (this.mSurface == null) {
            VELogUtil.e(TAG, "initEGLCtx: MediaCodec should initialized ahead.");
            return false;
        }
        this.mTextureDrawer = TextureDrawer.create();
        this.mTextureDrawer.setRotation(0.0f);
        this.mTextureDrawer.setFlipScale(1.0f, -1.0f);
        return true;
    }

    public boolean isError() {
        return this.mIsError;
    }

    public void releaseEGLCtx() {
        TextureDrawer textureDrawer = this.mTextureDrawer;
        if (textureDrawer != null) {
            textureDrawer.release();
            this.mTextureDrawer = null;
        }
    }

    public synchronized void releaseEncoder() {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            this.mMediaCodec.release();
        } catch (Exception unused) {
        }
        this.mMediaCodec = null;
        this.status = 0;
        VELogUtil.i(TAG, "time cost: " + (System.currentTimeMillis() - currentTimeMillis));
    }

    /* JADX WARNING: Removed duplicated region for block: B:31:0x0070 A[SYNTHETIC, Splitter:B:31:0x0070] */
    /* JADX WARNING: Removed duplicated region for block: B:36:0x007a A[SYNTHETIC, Splitter:B:36:0x007a] */
    /* JADX WARNING: Removed duplicated region for block: B:41:0x0085 A[SYNTHETIC, Splitter:B:41:0x0085] */
    /* JADX WARNING: Removed duplicated region for block: B:46:0x008f A[SYNTHETIC, Splitter:B:46:0x008f] */
    /* JADX WARNING: Removed duplicated region for block: B:52:? A[RETURN, SYNTHETIC] */
    public void saveBitmap(Bitmap bitmap, String str) {
        FileOutputStream fileOutputStream;
        BufferedOutputStream bufferedOutputStream;
        VELogUtil.i(TAG, "saving Bitmap : " + str);
        BufferedOutputStream bufferedOutputStream2 = null;
        try {
            fileOutputStream = new FileOutputStream(str);
            try {
                bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            } catch (IOException e2) {
                e = e2;
                try {
                    VELogUtil.e(TAG, "Err when saving bitmap...");
                    e.printStackTrace();
                    if (bufferedOutputStream2 != null) {
                        try {
                            bufferedOutputStream2.close();
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                    }
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                            return;
                        } catch (IOException e4) {
                            e4.printStackTrace();
                            return;
                        }
                    } else {
                        return;
                    }
                } catch (Throwable th) {
                    th = th;
                    if (bufferedOutputStream2 != null) {
                        try {
                            bufferedOutputStream2.close();
                        } catch (IOException e5) {
                            e5.printStackTrace();
                        }
                    }
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e6) {
                            e6.printStackTrace();
                        }
                    }
                    throw th;
                }
            }
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bufferedOutputStream);
                bufferedOutputStream.flush();
                try {
                    bufferedOutputStream.close();
                } catch (IOException e7) {
                    e7.printStackTrace();
                }
                try {
                    fileOutputStream.close();
                } catch (IOException e8) {
                    e8.printStackTrace();
                }
                VELogUtil.i(TAG, "Bitmap " + str + " saved!");
            } catch (IOException e9) {
                e = e9;
                bufferedOutputStream2 = bufferedOutputStream;
                VELogUtil.e(TAG, "Err when saving bitmap...");
                e.printStackTrace();
                if (bufferedOutputStream2 != null) {
                }
                if (fileOutputStream != null) {
                }
            } catch (Throwable th2) {
                th = th2;
                bufferedOutputStream2 = bufferedOutputStream;
                if (bufferedOutputStream2 != null) {
                }
                if (fileOutputStream != null) {
                }
                throw th;
            }
        } catch (IOException e10) {
            e = e10;
            fileOutputStream = null;
            VELogUtil.e(TAG, "Err when saving bitmap...");
            e.printStackTrace();
            if (bufferedOutputStream2 != null) {
            }
            if (fileOutputStream != null) {
            }
        } catch (Throwable th3) {
            th = th3;
            fileOutputStream = null;
            if (bufferedOutputStream2 != null) {
            }
            if (fileOutputStream != null) {
            }
            throw th;
        }
    }

    @TargetApi(21)
    public MediaFormat setBitrateMode(MediaFormat mediaFormat) {
        mediaFormat.setInteger("bitrate-mode", 0);
        return mediaFormat;
    }

    public void setEncoderCaller(AVCEncoderInterface aVCEncoderInterface) {
        mEncoderCaller = aVCEncoderInterface;
    }

    public void setFrameRate(int i) {
        this.mFrameRate = i;
    }

    public void uninitAVCEncoder() {
        VELogUtil.i(TAG, "uninitAVCEncoder == enter");
        synchronized (this) {
            if (this.status != 0) {
                if (this.mMediaCodec != null) {
                    if (this.status == 2) {
                        try {
                            this.mMediaCodec.stop();
                        } catch (Exception unused) {
                            VELogUtil.e(TAG, "MediaCodec Exception");
                        }
                    }
                    this.status = 3;
                    if (this.mSurface != null) {
                        this.mSurface.release();
                    }
                    releaseEncoder();
                    VELogUtil.i(TAG, "uninitAVCEncoder == exit");
                }
            }
        }
    }
}
