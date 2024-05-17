/*
 * Copyright (c) 2024. ForteScarlet.
 *
 * This file is part of simbot-component-onebot.
 *
 * simbot-component-onebot is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * simbot-component-onebot is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with simbot-component-onebot.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package love.forte.simbot.component.onebot.v11.core.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.literal
import love.forte.simbot.component.onebot.v11.core.message.OneBotMessageElement
import love.forte.simbot.component.onebot.v11.core.message.OneBotMessageElementSerializer
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic


/**
 * [send_private_msg 发送私聊消息](https://github.com/botuniverse/onebot-11/blob/master/api/public.md#send_private_msg-发送私聊消息)
 *
 * @author ForteScarlet
 */
public class SendPrivateMsgApi private constructor(
    override val body: Any
) : OneBotApi<SendPrivateMsgResult> {

    override val action: String
        get() = ACTION

    override val resultDeserializer: DeserializationStrategy<SendPrivateMsgResult>
        get() = SendPrivateMsgResult.serializer()

    override val apiResultDeserializer: DeserializationStrategy<OneBotApiResult<SendPrivateMsgResult>>
        get() = RES_SER

    public companion object Factory {
        private const val ACTION = "send_private_msg"
        private val RES_SER = OneBotApiResult.serializer(SendPrivateMsgResult.serializer())

        /**
         * 构建一个 [SendPrivateMsgApi].
         * @param userId 对方 QQ 号
         * @param message 要发送的内容
         */
        @JvmStatic
        public fun create(
            userId: ID,
            message: List<OneBotMessageElement>,
        ): SendPrivateMsgApi = SendPrivateMsgApi(
            ListBody(userId.literal, message, false)
        )

        /**
         * 构建一个 [SendPrivateMsgApi].
         * @param userId 对方 QQ 号
         * @param message 要发送的内容
         * @param autoEscape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 message 字段是字符串时有效
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            userId: ID,
            message: String,
            autoEscape: Boolean = false,
        ): SendPrivateMsgApi = SendPrivateMsgApi(
            StringBody(userId.literal, message, autoEscape)
        )
    }

    @Serializable
    internal data class ListBody(
        @SerialName("user_id")
        val userId: String,
        @Serializable(OneBotMessageElementSerializer::class)
        val message: List<OneBotMessageElement>,
        @SerialName("auto_escape")
        val autoEscape: Boolean = false,
    )


    @Serializable
    internal data class StringBody(
        @SerialName("user_id")
        val userId: String,
        val message: String,
        @SerialName("auto_escape")
        val autoEscape: Boolean = false,
    )
}


/*
user_id	number	-	对方 QQ 号
message	message	-	要发送的内容
auto_escape	boolean	false	消息内容是否作为纯文本发送（即不解析 CQ 码），只在 message 字段是字符串时有效
 */

/**
 * [SendPrivateMsgApi] 的响应体。
 */
@Serializable
public data class SendPrivateMsgResult
@ApiResultType internal constructor(
    @SerialName("message_id")
    val messageId: ID
)