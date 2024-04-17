package com.nayan.nayancamv2.extcam.media;

import com.nayan.nayancamv2.extcam.common.BlackFrameUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class to invoke native methods
 */
public class NativeHelper {
    private boolean shouldCheckForBlackFrames = true;
    public void setCheckForBlackFrames(boolean value){
        shouldCheckForBlackFrames = value;
    }

    public static final String TAG = NativeHelper.class.getSimpleName();

    public interface NativeDataListener {
        /**
         * Callback method for receiving the frame data from NativeHelper.
         * Note that this method will be invoke in framing thread, which means time consuming
         * processing should not in this thread, or the framing process will be blocked.
         *
         * @param data       raw video bytes
         * @param size       frame size
         * @param frameNum   frame number
         * @param isKeyFrame whether it is a key frame or not
         * @param width      width of one frame
         * @param height     height of one frame
         */
        void onDataReceived(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height);
    }

    private List<NativeDataListener> dataListener = new ArrayList<>();

    public void addDataListener(NativeDataListener dataListener) {
        this.dataListener.add(dataListener);
    }
    public void removeDataListener(NativeDataListener dataListener) throws Exception{
        this.dataListener.remove(dataListener);
    }
    //JNI

    /**
     * Test the ffmpeg.
     *
     * @return
     */
    public native String codecinfotest();

    /**
     * Initialize the ffmpeg.
     *
     * @return
     */
    public native boolean init();

    /**
     * Framing the raw data from camera
     *
     * @param buf
     * @param size
     * @return
     */
    public native boolean parse(byte[] buf, int size);


    /**
     * Release the ffmpeg
     *
     * @return
     */
    public native boolean release();

    static {
        try {
            System.loadLibrary("ffmpeg");
            System.loadLibrary("djivideojni");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static NativeHelper instance;

    public static NativeHelper getInstance() {
        if (instance == null) {
            instance = new NativeHelper();
        }
        return instance;
    }

    private NativeHelper() {
    }

    /**
     * Invoke by JNI
     * Callback the frame data.
     *
     * @param buf
     * @param size
     * @param frameNum
     * @param isKeyFrame
     * @param width
     * @param height
     */
    public void onFrameDataReceived(byte[] buf, int size, int frameNum, boolean isKeyFrame, int width, int height) {
        for (NativeDataListener nativeDataListener : dataListener
        ) {
            if(shouldCheckForBlackFrames && width!=0 && height!=0){
                shouldCheckForBlackFrames=false;
                BlackFrameUtil.INSTANCE.updateUI(false);
            }
            if (nativeDataListener != null) {
                nativeDataListener.onDataReceived(buf, size, frameNum, isKeyFrame, width, height);
            }

        }

    }
}
