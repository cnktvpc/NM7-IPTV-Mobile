package com.liskovsoft.youtubeapi.chat

import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper
import com.google.gson.Gson
import java.util.UUID

internal object LiveChatApiParams {
    fun getLiveChatQuery(chatKey: String): String {
        val chatData = String.format("\"continuation\":\"%s\"", chatKey)
        return PostDataHelper.createQueryTV(chatData)
    }

    fun getSendMessageQuery(params: String, message: String): String {
        val gson = Gson()
        val sendData = "\"params\":" + gson.toJson(params) +
                ",\"clientMessageId\":" + gson.toJson(UUID.randomUUID().toString()) +
                ",\"richMessage\":{\"textSegments\":[{\"text\":" +
                gson.toJson(message) + "}]}"
        return PostDataHelper.createQueryTV(sendData)
    }
}