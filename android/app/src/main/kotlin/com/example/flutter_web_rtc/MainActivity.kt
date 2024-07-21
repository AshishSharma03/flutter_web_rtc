package com.example.flutter_web_rtc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.flutter_web_rtc.models.IceCandidateModel
import com.example.flutter_web_rtc.models.MessageModel

import com.example.flutter_web_rtc.utils.NewMessageInterface
import com.example.flutter_web_rtc.utils.PeerConnectionObserver

import com.example.flutter_web_rtc.utils.RTCAudioManager
import com.google.gson.Gson
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer


class MainActivity: FlutterActivity() , NewMessageInterface {
    private var granted = false
    private val CHANNEL = "SendToAndroid"
    private var PERMISSIONS_REQUEST_CODE = 1
    private var userName:String?=null
    private var socketRepository: SocketRepository?=null
    private var rtcClient : RTCClient?=null
    private val TAG = "WebRtc_native_calls"
    private var target:String = ""
    private val gson = Gson()
    private var isMute = false
    private var isCameraPause = false
    private val rtcAudioManager by lazy { RTCAudioManager.create(this) }
    private var isSpeakerMode = true
    private var CallerName:String = ""
    private var dataChannel : DataChannel? = null
    private var localView: SurfaceViewRenderer? = null
    private var remoteView: SurfaceViewRenderer? = null
    private val eglBase = EglBase.create()
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "handlePeerAction" -> {
                        val peerName = call.argument<String>("peerName")
                        val targetPeer = call.argument<String>("targetPeer")
                        val action = call.argument<String>("action")

                        // Handle the method call
                        handlePeerAction(peerName, targetPeer, action, result)
                    }
                    else -> result.notImplemented()
                }
            }
            }

    private fun handlePeerAction(peerName: String?, targetPeer: String?, action: String?, result: MethodChannel.Result) {
        when (action) {
            "addPeer" -> {
                // Handle the addPeer action
                if(granted && peerName != null){
                    userName = peerName
                    init();
                }
                result.success("Peer added: $peerName")
            }
            "connect" -> {
                // Handle the connect action
                if (granted && targetPeer != null) {
                    startCall(targetPeer)
                    target = targetPeer
                }
                result.success("Connected to peer: $targetPeer")
            }
            "p2p-check" -> {
                    Log.d("check p2p ","channel working..")

                    rtcClient?.checkConnectionStatus()
                    rtcClient?.sendMessage("Hello")

            }
            else -> result.error("UNAVAILABLE", "Action not supported", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check and request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ), PERMISSIONS_REQUEST_CODE)
        } else {
            // Initialize PeerConnectionFactory
            granted  = true;
            socketRepository = SocketRepository(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            var allPermissionsGranted = true

            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                // Initialize PeerConnectionFactory after permissions are granted
                granted  = true;
                socketRepository = SocketRepository(this)
            } else {
                Log.e("Permissions", "Permissions denied.")
            }
        }
    }

    private fun init(){

        userName?.let { socketRepository?.initSocket(it) }
        rtcClient = RTCClient(application,userName!!,socketRepository!!, object : PeerConnectionObserver() {
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                rtcClient?.addIceCandidate(p0)
                val candidate = hashMapOf(
                    "sdpMid" to p0?.sdpMid,
                    "sdpMLineIndex" to p0?.sdpMLineIndex,
                    "sdpCandidate" to p0?.sdp
                )

                socketRepository?.sendMessageToSocket(
                    MessageModel("ice_candidate",userName,target,candidate)
                )
            }
            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
//                p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
                Log.d(TAG, "onAddStream: $p0")

            }

            override fun onDataChannel(p0: DataChannel?) {
                super.onDataChannel(p0)
                dataChannel =  p0
                Log.d("DataChannel", p0.toString())
                dataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(p0: Long) {

                    }

                    override fun onStateChange() {

                    }

                    override fun onMessage(buffer: DataChannel.Buffer?) {
//                        val message = String(buffer?.data?.array() ?: ByteArray(0))
                        Log.d(TAG, "Message received on DataChannel: $buffer")
                    }
                })
            }

        })
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

    }

    private fun startCall(targetUserNameEt: String){
        socketRepository?.sendMessageToSocket(MessageModel(
            "start_call",userName,targetUserNameEt,null
        ))
    }




    override fun onNewMessage(message: MessageModel) {
        Log.d(TAG,message.toString());
        when(message.type){
            "call_response"-> {
                if (message.data == "user is not online") {
                    Log.d(TAG, "user is not reachable");

                } else {
                        rtcClient?.call(target)

                }
            }
            "offer_received" ->{

                val session = SessionDescription(
                    SessionDescription.Type.OFFER,
                    message.data.toString()
                )
                rtcClient?.onRemoteSessionReceived(session)
                rtcClient?.answer(message.name!!)
                target = message.name!!
                }

            "answer_received" ->{
                val session = SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString()
                )
                rtcClient?.onRemoteSessionReceived(session)

            }

            "ice_candidate"->{
                    try {
                        val receivingCandidate = gson.fromJson(gson.toJson(message.data),
                            IceCandidateModel::class.java)
                        rtcClient?.addIceCandidate(IceCandidate(receivingCandidate.sdpMid,
                            Math.toIntExact(receivingCandidate.sdpMLineIndex.toLong()),receivingCandidate.sdpCandidate))
                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }

            }

        }



}

