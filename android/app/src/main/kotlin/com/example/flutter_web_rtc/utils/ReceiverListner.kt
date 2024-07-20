package com.example.flutter_web_rtc.utils

import org.webrtc.DataChannel

interface ReceiverListner{
    fun onDataRecived(it: DataChannel.Buffer)
}