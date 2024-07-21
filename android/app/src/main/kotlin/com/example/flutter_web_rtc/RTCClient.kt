package com.example.flutter_web_rtc

import android.app.Application
import android.util.Log
import com.example.flutter_web_rtc.models.MessageModel

import org.webrtc.*
import java.nio.ByteBuffer

class RTCClient(
    private val application: Application,
    private val username: String,
    private val socketRepository: SocketRepository,
    private val observer: PeerConnection.Observer
) {

    init {
        initPeerConnectionFactory(application)
    }

    private val eglContext = EglBase.create()
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:iphone-stun.strato-iphone.de:3478").createIceServer(),
        PeerConnection.IceServer("stun:openrelay.metered.ca:80"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:80","openrelayproject","openrelayproject"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:443","openrelayproject","openrelayproject"),
        PeerConnection.IceServer("turn:openrelay.metered.ca:443?transport=tcp","openrelayproject","openrelayproject"),

        )
    private val peerConnection by lazy { createPeerConnection(observer) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var dataChannel : DataChannel? = null

    private fun initPeerConnectionFactory(application: Application) {
        val peerConnectionOption = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(peerConnectionOption)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglContext.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
//                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun createDataChannel(): DataChannel? {
        val init = DataChannel.Init()
        return peerConnection?.createDataChannel("dataChannel", init)
    }
//    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
//        return peerConnectionFactory.createPeerConnection(iceServer, observer)
//    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val pc = peerConnectionFactory.createPeerConnection(iceServer, observer)

        pc?.let {
            // Set the DataChannel observer
            val init = DataChannel.Init()
            dataChannel = it.createDataChannel("dataChannel",init )
            dataChannel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}

                override fun onStateChange() {
                    Log.d("DataChannel", "State: ${dataChannel?.state()}")
                }

                override fun onMessage(buffer: DataChannel.Buffer?) {

//                    val message = String(buffer?.data?.array() ?: ByteArray(0))
                    Log.d("DataChannel", "Message received on DataChannel: $buffer")
                    // Handle received message
                }
            })
        }

        return pc
    }


    fun initializeSurfaceView(surface: SurfaceViewRenderer) {
        surface.run {
            setEnableHardwareScaler(true)
            setMirror(true)
            init(eglContext.eglBaseContext, null)
        }
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglContext.eglBaseContext)
        videoCapturer = getVideoCapturer(application)
        videoCapturer?.initialize(
            surfaceTextureHelper,
            surface.context, localVideoSource.capturerObserver
        )
        videoCapturer?.startCapture(320, 240, 30)
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_track", localVideoSource)
        localVideoTrack?.addSink(surface)
        localAudioTrack =
            peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource)
        val localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream.addTrack(localAudioTrack)
        localStream.addTrack(localVideoTrack)

        peerConnection?.addStream(localStream)

    }

    private fun getVideoCapturer(application: Application): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw
            IllegalStateException()
        }
    }

    fun call(target: String) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))


        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {

                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )

                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                "create_offer", username, target, offer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)

            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }
        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(session: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {

            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, session)

    }

    fun answer(target: String) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }


                    override fun onSetSuccess() {
                        val answer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        socketRepository.sendMessageToSocket(
                            MessageModel(
                                "create_answer", username, target, answer
                            )
                        )
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                    }

                }, desc)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
            }

        }, constraints)
    }

    fun addIceCandidate(p0: IceCandidate?) {
        peerConnection?.addIceCandidate(p0)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleCamera(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun endCall() {
        peerConnection?.close()
    }

    fun checkConnectionStatus(){
        if (peerConnection == null) {
            Log.e("PeerConnection", "PeerConnection is not initialized.");
            return;
        }
        // Check signaling state
        val signalingState: PeerConnection.SignalingState = peerConnection!!.signalingState()
        Log.d("PeerConnection", "Signaling state: $signalingState")

        // Check ICE connection state
        val iceConnectionState: PeerConnection.IceConnectionState = peerConnection!!.iceConnectionState()
        Log.d("PeerConnection", "ICE connection state: $iceConnectionState")

    }

    fun sendMessage(message: String) {
        val buffer = ByteBuffer.wrap(message.toByteArray())
        val dataBuffer = DataChannel.Buffer(buffer, false)
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataChannel?.send(dataBuffer)
            Log.d("DataChannel", "Message sent: $message")
        } else {
            Log.e("DataChannel", "DataChannel is not open")
        }
    }
}