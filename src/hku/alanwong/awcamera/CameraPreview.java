package hku.alanwong.awcamera;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.FaceDetector;
import android.util.Log;
import android.view.*;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
	private SurfaceHolder mHolder;
    private Camera mCamera;
    
    //from MonkeyCam
    private Bitmap mWorkBitmap;
    private Bitmap mMonkeyImage;
    
    private FaceDetector mFaceDetector;
    private FaceDetector.Face[] mFaces = new FaceDetector.Face[16]; //max 64
    private FaceDetector.Face face = null;
    
    private PointF eyesMidPts[] = new PointF[16];
    private float  eyesDistance[] = new float[16];

    private Paint tmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pOuterBullsEye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pInnerBullsEye = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private int picWidth, picHeight;
    private float ratio, xRatio, yRatio;
    //-----MonkeyCam 

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        mHolder = getHolder();
        mHolder.addCallback(this);
      
        //from MonkeyCam
        pInnerBullsEye.setStyle(Paint.Style.FILL);
        pInnerBullsEye.setColor(Color.RED);
       
        pOuterBullsEye.setStyle(Paint.Style.STROKE);
        pOuterBullsEye.setColor(Color.RED);
       
        tmpPaint.setStyle(Paint.Style.STROKE);

        mMonkeyImage = BitmapFactory.decodeResource(getResources(), R.drawable.monkey_head);

        picWidth = mMonkeyImage.getWidth();
        picHeight = mMonkeyImage.getHeight();
        //-----MonkeyCam
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            String TAG = null;
			Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
	//from MonkeyCam
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    //-----MonkeyCam
    }
    
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
    	if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
        	Camera.Parameters parameters = mCamera.getParameters();

            List<Size> sizes = parameters.getSupportedPreviewSizes();
            Size optimalSize = getOptimalPreviewSize(sizes, w, h);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            mCamera.setParameters(parameters);

            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            
         // Setup the objects for the face detection
            mWorkBitmap = Bitmap.createBitmap(optimalSize.width, optimalSize.height, Bitmap.Config.RGB_565);
            mFaceDetector = new FaceDetector(optimalSize.width, optimalSize.height, 16);

            int bufSize = optimalSize.width * optimalSize.height *
                ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
            byte[] cbBuffer = new byte[bufSize];
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.addCallbackBuffer(cbBuffer);

        } catch (Exception e){
        	String TAG = null;
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
	//from MonkeyCam
		
		// face detection: first convert the image from NV21 to RGB_565
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
                mWorkBitmap.getWidth(), mWorkBitmap.getHeight(), null);
        Rect rect = new Rect(0, 0, mWorkBitmap.getWidth(),
                mWorkBitmap.getHeight());	// TODO: make rect a member and use it for width and height values above

        // TODO: use a threaded option or a circular buffer for converting streams?  see http://ostermiller.org/convert_java_outputstream_inputstream.html
        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        if (!yuv.compressToJpeg(rect, 100, baout)) {
            Log.e("Preview", "compressToJpeg failed");
        }
        BitmapFactory.Options bfo = new BitmapFactory.Options();
        bfo.inPreferredConfig = Bitmap.Config.RGB_565;
        mWorkBitmap = BitmapFactory.decodeStream(
            new ByteArrayInputStream(baout.toByteArray()), null, bfo);

        // Dev only, save the bitmap to a file for visual inspection
        // Also remove the WRITE_EXTERNAL_STORAGE permission from the manifest
        /*String path = Environment.getExternalStorageDirectory().toString() + "/monkeyCam";
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdir();
            }

            FileOutputStream out = new FileOutputStream(path + "/monkeyCamCapture" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".jpg");
            mWorkBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        Arrays.fill(mFaces, null);	// use arraycopy instead?
        Arrays.fill(eyesMidPts, null);	// use arraycopy instead?
        mFaceDetector.findFaces(mWorkBitmap, mFaces);

        for (int i = 0; i < mFaces.length; i++)
        {
            face = mFaces[i];
            try {
                PointF eyesMP = new PointF();
                face.getMidPoint(eyesMP);
                eyesDistance[i] = face.eyesDistance();
                eyesMidPts[i] = eyesMP;

                Log.i("Face",
                        i +  " " + face.confidence() + " " + face.eyesDistance() + " "
                        + "Pose: ("+ face.pose(FaceDetector.Face.EULER_X) + ","
                        + face.pose(FaceDetector.Face.EULER_Y) + ","
                        + face.pose(FaceDetector.Face.EULER_Z) + ")"
                        + "Eyes Midpoint: ("+eyesMidPts[i].x + "," + eyesMidPts[i].y +")"
                );
            }
            catch (Exception e)
            {
                //if (DEBUG) Log.e("Face", i + " is null");
            }
        }
        
        invalidate(); // use a dirty Rect?

        // Requeue the buffer so we get called again
        mCamera.addCallbackBuffer(data);
    //-----MonkeyCam
	}
	
	@Override
    protected void onDraw(Canvas canvas){
	//from MonkeyCam
		// Log.d(TAG, "onDraw: frame size=(" + mWorkBitmap.getWidth() + ", " + mWorkBitmap.getHeight() + ") display size=(" + getWidth() + ", " + getHeight() + ")");
        Log.d("Preview","onDraw");
    	super.onDraw(canvas);
    	if(mWorkBitmap != null){
	        xRatio = getWidth() * 1.0f / mWorkBitmap.getWidth();
	        yRatio = getHeight() * 1.0f / mWorkBitmap.getHeight();
	
	        for (int i = 0; i < eyesMidPts.length; i++){
	            if (eyesMidPts[i] != null){
	                ratio = eyesDistance[i] * 4.0f / picWidth;
	                RectF scaledRect = new RectF((eyesMidPts[i].x - picWidth * ratio / 2.0f) * xRatio,
	                                             (eyesMidPts[i].y - picHeight * ratio / 2.0f) * yRatio,
	                                             (eyesMidPts[i].x + picWidth * ratio / 2.0f) * xRatio,
	                                             (eyesMidPts[i].y + picHeight * ratio / 2.0f) * yRatio);
	
	                canvas.drawBitmap(mMonkeyImage, null , scaledRect, tmpPaint);
	            }
	        }
    	}
    //-----MonkeyCam
    }
}