package com.example.screenrecord

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.example.screenrecord.services.MediaProjectorService
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.ScreenCapturerAndroid
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.experimental.and
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

var remoteSdp = ""

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HomePage(){
    val context = LocalContext.current

    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val mediaProjectionIntent = mediaProjectionManager.createScreenCaptureIntent()

    val serviceIntent = Intent(context, MediaProjectorService::class.java)

    var mediaProjection: MediaProjection? by remember { mutableStateOf(null) }

    val rootEglBase = EglBase.create()

    val LOCAL_TRACK_ID = "local_track"

    val LOCAL_STREAM_ID = "local_track"

    val options = PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(true)
        .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
        .createInitializationOptions()
    PeerConnectionFactory.initialize(options)

    val peerConnectionFactory = PeerConnectionFactory
        .builder()
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
        .setOptions(PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = true
        })
        .createPeerConnectionFactory()

    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:numb.viagenie.ca")
            .setUsername("sultan1640@gmail.com")
            .setPassword("98376683")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:numb.viagenie.ca")
            .setUsername("sultan1640@gmail.com")
            .setPassword("98376683")
            .createIceServer()
    )

    val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

    val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)

    val observer = MyPeerConnectionObserver()

    val peerConnection = peerConnectionFactory.createPeerConnection(
        rtcConfig,
        observer
    )

    var sdpValue by remember { mutableStateOf("None") }

    val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {isGranted ->
            if(!isGranted){
                if(ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.READ_EXTERNAL_STORAGE)){
//                    openDialog.value = true
                }else{
                    Toast.makeText(
                        context,
                        "Please enable 'Microphone' in settings before you may proceed",
                        Toast.LENGTH_LONG
                    ).show()

                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+ BuildConfig.APPLICATION_ID))

                    context.startActivity(i)
                }
            }
        }
    )

    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Get the MediaProjection from the Intent
            mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode,
                result.data!!
            )

            val windowManager: WindowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics: android.view.WindowMetrics = windowManager.getMaximumWindowMetrics()
            val bounds = metrics.bounds
            val width: Int = bounds.width()
            val height: Int = bounds.height()

            val videoCapturer = ScreenCapturerAndroid(result.data!!, mediaProjectionCallback)

            val localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast())

            val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)

            videoCapturer.initialize(surfaceTextureHelper, context, localVideoSource.capturerObserver)

            videoCapturer.startCapture(width, height, 60)

            val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)

            localStream.addTrack(localVideoTrack)

            peerConnection?.addStream(localStream)

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            }

            peerConnection?.createOffer(object: CustomSdpObserver(){
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    println("Create offer success ${sessionDescription}")

                    val json = JSONObject()

                    json.put("type", "offer")

                    json.put("sdp", sessionDescription?.description)

                    sdpValue = json.toString()

                    peerConnection.setLocalDescription(object : CustomSdpObserver() {
                        override fun onSetSuccess() {
                            println("Set local description success")
                        }

                        override fun onSetFailure(error: String?) {
                            println("Set local description failed: $error")
                        }
                    }, sessionDescription)
                }
                override fun onCreateFailure(error: String?) {
                    println("Create offer failed: $error")
                }
            }, constraints)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Button(
            onClick = {
                val hasRecordPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                }

                if(!hasRecordPermission){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }else{
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }else{
                    startForegroundService(context, serviceIntent)

                    resultLauncher.launch(mediaProjectionIntent)
                }
            }
        ) {
            Text(text = "Start Screen Record")
        }
        Button(
            onClick = {
                mediaProjection?.stop()

                context.stopService(serviceIntent)
            }
        ) {
            Text(text = "Stop Screen Record")
        }

        Text(
            "Session Description",
            modifier= Modifier.padding(20.dp)
        )

        SelectionContainer{
            Text(
                text= sdpValue,
                modifier= Modifier.padding(20.dp)
            )
        }

        Text(
            "Remote Description",
            modifier= Modifier.padding(20.dp)
        )

        RemoteDescriptionField()

        TextButton(onClick = {
            val jsonString = remoteSdp

            val json = JSONObject(jsonString)

            val type = json.getString("type")

            val sdp = json.getString("description")

            val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {

                }

                override fun onSetSuccess() {
                    println("remote description has been set")
                }

                override fun onCreateFailure(p0: String?) {

                }

                override fun onSetFailure(p0: String?) {

                }

            }, sessionDescription)
        }) {
            Text("Set Remote Description")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
//            videoCapturer.stopCapture()
            surfaceTextureHelper.dispose()
            context.stopService(serviceIntent)
        }
    }
}

@Composable
fun RemoteDescriptionField(){
    var remoteDescriptionValue by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ){
        TextField(
            value= remoteDescriptionValue,
            onValueChange = {
                remoteDescriptionValue = it
                remoteSdp = it
            }
        )
    }
}

class MyPeerConnectionObserver : PeerConnection.Observer {
    override fun onIceCandidate(candidate: IceCandidate) {
        // Called when an ICE candidate is gathered
        println("ICE candidate received")

        println(candidate)
    }

    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
        // Called when ICE candidates have been removed
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        // Called when the ICE connection state changes
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        // Called when the PeerConnection state changes
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        // Called when the ICE connection receiving state changes
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
//            if(p0 === PeerConnection.IceGatheringState.COMPLETE){
//                println("Ice gathering complete")
//            }
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
        // Called when the selected ICE candidate pair changes
    }

    override fun onAddStream(stream: MediaStream) {
        // Called when a new MediaStream is added
//            println("Streams are passing by")
//
//            println(stream.videoTracks)

//        stream.videoTracks?.get(0)?.addSink(remoteVideoOutput)
    }

    override fun onRemoveStream(stream: MediaStream) {
        // Called when a MediaStream is removed
    }

    override fun onDataChannel(channel: DataChannel) {
        // Called when a new DataChannel is created
    }

    override fun onRenegotiationNeeded() {
        // Called when renegotiation is needed (for example, when adding or removing tracks)
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        // Called when a new track is added
    }
}

abstract class CustomSdpObserver : SdpObserver {
    override fun onSetFailure(p0: String?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onCreateSuccess(p0: SessionDescription?) {}
}