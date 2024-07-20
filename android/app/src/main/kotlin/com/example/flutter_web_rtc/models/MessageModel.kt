package com.example.flutter_web_rtc.models

data class MessageModel(
     val type: String,
     val name: String? = null,
     val target: String? = null,
     val data:Any?=null
)
