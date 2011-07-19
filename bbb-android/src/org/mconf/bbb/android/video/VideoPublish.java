package org.mconf.bbb.android.video;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.mconf.bbb.BigBlueButtonClient;
import org.mconf.bbb.video.IVideoPublishListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.hardware.Camera;

import com.flazr.rtmp.RtmpReader;
import com.flazr.rtmp.message.Metadata;
import com.flazr.rtmp.message.Video;

public class VideoPublish extends Thread implements RtmpReader {
	private class VideoPublishHandler extends IVideoPublishListener {
		
		public VideoPublishHandler(int userId, String streamName, RtmpReader reader, BigBlueButtonClient context) {			
			super(userId, streamName, reader, context);
		}
				
	}
	
	public Camera mCamera;
	public int bufSize;
    public static final int DEFAULT_FRAME_RATE = 15;
    public static final int DEFAULT_WIDTH = 320;
    public static final int DEFAULT_HEIGHT = 240;
    public static final int DEFAULT_BIT_RATE = 512000;
    public static final int DEFAULT_GOP = 5;
    public int frameRate = DEFAULT_FRAME_RATE;
    public int width = DEFAULT_WIDTH;
    public int height = DEFAULT_HEIGHT;
    public int bitRate = DEFAULT_BIT_RATE;
    public int GOP = DEFAULT_GOP;
	
	private static final Logger log = LoggerFactory.getLogger(VideoPublish.class);
   
    private byte[] sharedBuffer;
    
    public boolean isCapturing = false;
    public boolean nativeEncoderInitialized = false;
    
    private int firstTimeStamp = 0;
	private int lastTimeStamp = 0;
	private String streamId;
	private int userId;
	
	private final List<Video> framesList = new ArrayList<Video>();
	
	private VideoPublishHandler videoPublishHandler;
	
	private BigBlueButtonClient context;
	        
    public VideoPublish(BigBlueButtonClient context, int userId) {
    	this.context = context;    	 
    	this.userId = userId;
    }
    
    public void initNativeEncoder(){
    	sharedBuffer = new byte[bufSize]; // the encoded frame will never be bigger than the not encoded
    	
    	initEncoder(width, height, frameRate, bitRate, GOP);
    	  	
    	streamId = width+"x"+height+userId; 
    	
    	nativeEncoderInitialized = true;
    }
    
    public void startPublisher(){
    	videoPublishHandler = new VideoPublishHandler(userId, streamId, this, context);
    	videoPublishHandler.start();
    }
    
    @Override
    public void run() {
       	isCapturing = true;
    	initSenderLoop();
    }
       
    public byte[] assignJavaBuffer()
	{
    	return sharedBuffer;
	}	
    
    public int onReadyFrame (int bufferSize, int timeStamp)
    {    	
    	if(firstTimeStamp == 0){
    		firstTimeStamp = timeStamp;
    	}    	
    	timeStamp = timeStamp - firstTimeStamp;
    	int interval = timeStamp - lastTimeStamp;
    	lastTimeStamp = timeStamp;
    	
    	byte[] aux = new byte[bufferSize];
    	System.arraycopy(sharedBuffer, 0, aux, 0, bufferSize); //\TODO see if we can avoid this copy
    	
       	Video video = new Video(timeStamp, aux, bufferSize);
   	    video.getHeader().setDeltaTime(interval);
		video.getHeader().setStreamId(this.videoPublishHandler.videoConnection.streamId);
   				 
		framesList.add(video);
		
    	return 0;
    }
    
    public void stopPublisher(){
    	videoPublishHandler.stop(context);
    }
    
    public int endNativeEncoding(){
    	isCapturing = false;
    	nativeEncoderInitialized = false;
        	
    	endEncoder();
    	return 0;
    }

	@Override
	public void close() {		
	}

	@Override
	public Metadata getMetadata() {
		return null;
	}

	@Override
	public Video[] getStartMessages() {
		Video[] startMessages = new Video[0];
        return startMessages;
	}

	@Override
	public long getTimePosition() {
		return 0;
	}

	@Override
	public boolean hasNext() {
		while(framesList.isEmpty() && isCapturing){
			try {
				this.wait(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(isCapturing){
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Video next() {
		return framesList.remove(0);
	}

	@Override
	public long seek(long timePosition) {
		return 0;
	}

	@Override
	public void setAggregateDuration(int targetDuration) {
	}
	
	private native int initEncoder(int width, int height, int frameRate, int bitRate, int GOP);
	private native int endEncoder();
    private native int initSenderLoop();
}