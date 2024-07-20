package com.example.flutter_web_rtc.utils

import com.example.flutter_web_rtc.models.MessageModel

interface NewMessageInterface {
    fun onNewMessage(message: MessageModel)
}