package me.pntutorial.pnrtcblog;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import me.kevingleason.pnwebrtc.PnPeer;
import me.kevingleason.pnwebrtc.PnRTCClient;
import me.kevingleason.pnwebrtc.PnRTCListener;
import me.pntutorial.pnrtcblog.util.Constants;

import static org.webrtc.VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;



public class VideoChatActivity extends Activity {
    public static final String VIDEO_TRACK_ID = "videoPN";
    public static final String AUDIO_TRACK_ID = "audioPN";
    public static final String LOCAL_MEDIA_STREAM_ID = "localStreamPN";
    private final boolean mirror = true;

    private PnRTCClient pnRTCClient;
    private VideoSource localVideoSource;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private GLSurfaceView mVideoView;

    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_chat);
        // VideoChatActivity#onCreate()
        Bundle extras = getIntent().getExtras();
        if (extras == null || !extras.containsKey(Constants.USER_NAME)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            Toast.makeText(this, "Need to pass username to VideoChatActivity in intent extras (Constants.USER_NAME).",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        this.username  = extras.getString(Constants.USER_NAME, "");

        PeerConnectionFactory.initializeAndroidGlobals(
                this,  // Context
                true,  // Audio Enabled
                true,  // Video Enabled
                true,  // Hardware Acceleration Enabled
                null); // Render EGL Context

        PeerConnectionFactory pcFactory = new PeerConnectionFactory();
        this.pnRTCClient = new PnRTCClient(Constants.PUB_KEY, Constants.SUB_KEY, this.username);


        // VideoChatActivity#onCreate()
// Returns the number of cams & front/back face device name
        int camNumber = VideoCapturerAndroid.getDeviceCount();
        String frontFacingCam =  VideoCapturerAndroid.getNameOfFrontFacingDevice();
        String backFacingCam  = VideoCapturerAndroid.getNameOfBackFacingDevice();

// Creates a VideoCapturerAndroid instance for the device name
        VideoCapturer capturer = VideoCapturerAndroid.create(frontFacingCam);

// First create a Video Source, then we can make a Video Track
        localVideoSource = pcFactory.createVideoSource(capturer, this.pnRTCClient.videoConstraints());
        VideoTrack localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);

// First we create an AudioSource then we can create our AudioTrack
        AudioSource audioSource = pcFactory.createAudioSource(this.pnRTCClient.audioConstraints());
        AudioTrack localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);




        // To create our VideoRenderer, we can use the included VideoRendererGui for simplicity
        // First we need to set the GLSurfaceView that it should render to
        this.mVideoView = (GLSurfaceView) findViewById(R.id.gl_surface);

        // Then we set that view, and pass a Runnable to run once the surface is ready
        VideoRendererGui.setView(mVideoView, null);

        // Now that VideoRendererGui is ready, we can get our VideoRenderer.
// IN THIS ORDER. Effects which is on top or bottom
        remoteRender = VideoRendererGui.create(0, 0, 100, 100, SCALE_ASPECT_FILL, false);
        localRender = VideoRendererGui.create(0, 0, 100, 100, SCALE_ASPECT_FILL, true);



        MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);



// Now we can add our tracks.
        mediaStream.addTrack(localVideoTrack);
        mediaStream.addTrack(localAudioTrack);


        // First attach the RTC Listener so that callback events will be triggered
        this.pnRTCClient.attachRTCListener(new MyRTCListener());



        // VideoChatActivity#onCreate()

        this.pnRTCClient.attachLocalMediaStream(mediaStream);

// Listen on a channel. This is your "phone number," also set the max chat users.
        this.pnRTCClient.listenOn(this.username);
        this.pnRTCClient.setMaxConnections(1);

// If Constants.CALL_USER is in the intent extras, auto-connect them.
        if (extras.containsKey(Constants.CALL_USER)) {
            String callUser = extras.getString(Constants.CALL_USER, "");
            connectToUser(callUser);
        }

    }

    public void connectToUser(String user) {
        this.pnRTCClient.connect(user);
    }

    public void hangup(View view) {
        this.pnRTCClient.closeAllConnections();
        startActivity(new Intent(VideoChatActivity.this, MainActivity.class));
    }

    private class MyRTCListener extends PnRTCListener {
        private  final String LOG_TAG = getClass().getSimpleName();
        // Override methods you plan on using

        @Override
        public void onLocalStream(final MediaStream localStream) {
            super.onLocalStream(localStream);
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(localStream.videoTracks.size()==0) return;
                    localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
                    Log.d(LOG_TAG,"onLocalStream");

                }
            });
        }

        @Override
        public void onAddRemoteStream(final MediaStream remoteStream, final PnPeer peer) {
            super.onAddRemoteStream(remoteStream, peer); // Will log values
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VideoChatActivity.this,"Connected to " + peer.getId(), Toast.LENGTH_SHORT).show();
                    try {
                        if(remoteStream.audioTracks.size()==0 || remoteStream.videoTracks.size()==0) return;
                        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
                        VideoRendererGui.update(remoteRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                        VideoRendererGui.update(localRender, 72, 72, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                        Log.d(LOG_TAG,"onAddRemoteStream");
                    }
                    catch (Exception e){ e.printStackTrace(); }
                }
            });
        }

        @Override
        public void onPeerConnectionClosed(PnPeer peer) {
            Intent intent = new Intent(VideoChatActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }



    }
}
