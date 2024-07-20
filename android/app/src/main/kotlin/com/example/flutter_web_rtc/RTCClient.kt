package com.example.flutter_web_rtc

import android.app.Application
import android.util.Log
import com.example.flutter_web_rtc.models.MessageModel
import com.example.flutter_web_rtc.utils.ReceiverListner
import org.webrtc.AudioTrack
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack


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

    var reciverListener : ReceiverListner? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var videoCapturer: CameraVideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private val dataChannelObserver = object : DataChannel.Observer{
        override fun onBufferedAmountChange(p0: Long) {
            TODO("Not yet implemented")
        }

        override fun onStateChange() {
            val state =
                peerConnection!!.createDataChannel("dataChannelLabel", DataChannel.Init()).state()
            when (state) {
                DataChannel.State.CONNECTING -> Log.d("Data_Channel", "State: Connecting")
                DataChannel.State.OPEN -> Log.d("Data_Channel", "State: Open")
                DataChannel.State.CLOSING -> Log.d("Data_Channel", "State: Closing")
                DataChannel.State.CLOSED -> Log.d("Data_Channel", "State: Closed")
            }
        }

        override fun onMessage(p0: DataChannel.Buffer?) {
            p0?.let { reciverListener?.onDataRecived(it) }
        }

    }

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
                disableEncryption = true
                disableNetworkMonitor = true
            }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(iceServer, observer)
    }

    private fun createDataChannel() {
        Log.d("RTCClient", "Creating data channel")

        // Initialize the data channel
        val initDataChannel = DataChannel.Init()
        Log.d("RTCClient", "DataChannel.Init() created")

        // Create the data channel
        val dataChannel = peerConnection?.createDataChannel("dataChannelLabel", initDataChannel)

        if (dataChannel != null) {
            Log.d("RTCClient", "Data channel created successfully: ${dataChannel.label()}")
            dataChannel.registerObserver(dataChannelObserver)
            Log.d("RTCClient", "Data channel observer registered")
        } else {
            Log.e("RTCClient", "Failed to create data channel: DataChannel is null")
        }
    }




    fun call(target: String) {
        if (peerConnection == null) {
            Log.e("RTCClient", "PeerConnection is not initialized.")
            return
        }
        createDataChannel()
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("RtpDataChannel","true"))

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
        constraints.mandatory.add(MediaConstraints.KeyValuePair("RtpDataChannel","true"))

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



    fun checkConnectionStatus(){
        if (peerConnection == null) {
            Log.e("PeerConnection", "PeerConnection is not initialized.");
            return;
        }
        // Check signaling state
        val signalingState: SignalingState = peerConnection!!.signalingState()
        Log.d("PeerConnection", "Signaling state: $signalingState")

        // Check ICE connection state
        val iceConnectionState: IceConnectionState = peerConnection!!.iceConnectionState()
        Log.d("PeerConnection", "ICE connection state: $iceConnectionState")

    }


}