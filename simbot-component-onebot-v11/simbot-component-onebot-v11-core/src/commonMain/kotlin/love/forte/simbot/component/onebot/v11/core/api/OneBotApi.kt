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

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import love.forte.simbot.component.onebot.common.annotations.InternalOneBotAPI
import love.forte.simbot.component.onebot.v11.core.api.OneBotApiResult.Companion.RETCODE_SUCCESS


/**
 * 一个 OneBot 的 [API](https://github.com/botuniverse/onebot-11/tree/master/api)。
 *
 * API调用需要指定 [action] 和 [参数][body].
 *
 * @author ForteScarlet
 */
public interface OneBotApi<T : Any> {
    /**
     * API 的 action（要进行的动作）
     */
    public val action: String

    /**
     * API的参数。
     */
    public val body: Any?

    /**
     * 预期结果的反序列化器。
     */
    public val resultDeserializer: DeserializationStrategy<T>

    /**
     * 预期结果 [OneBotApi] 类型的反序列化器。
     */
    public val apiResultDeserializer: DeserializationStrategy<OneBotApiResult<T>>

    /**
     * 与 `action` 相关的公共、常量信息。
     */
    public object Actions {
        /**
         * [异步调用](https://github.com/botuniverse/onebot-11/blob/master/api/README.md#异步调用).
         *
         * 所有 API 都可以通过给 `action` 附加后缀 `_async` 来进行异步调用
         */
        public const val ASYNC_SUFFIX: String = "_async"

        /**
         * [限速调用](https://github.com/botuniverse/onebot-11/blob/master/api/README.md#限速调用)
         *
         * 所有 API 都可以通过给 `action` 附加后缀 `_rate_limited` 来进行限速调用。
         */
        public const val RATE_LIMITED_SUFFIX: String = "_rate_limited"
    }
}

/**
 * [响应](https://github.com/botuniverse/onebot-11/blob/master/api/README.md#响应)
 *
 * OneBot 会对每个 API 调用返回一个 JSON 响应（除非是 HTTP 状态码不为 200 的情况），响应中的 `data` 字段包含 API 调用返回的数据内容。
 * 在后面的 API 描述中，将只给出 `data` 字段的内容，放在「响应数据」小标题下，而不再赘述 `status`、`retcode` 字段。
 *
 * 在 [OneBotApiResult] 中，为了区分失败的结果和成功但结果为 `null` 的情况，
 * 那个本身成功结果就应该为 `null` 的 API 应当将结果 [T] 定义为 [Unit] 类型以此代替 `null`。
 * 同时在反序列化时，如果 `retcode` == [RETCODE_SUCCESS] 并且结果的预期类型是一个 [Unit]，则直接填充它。
 * 建议
 *
 * @property data 不为 `null` 代表结果成功。
 */
@Serializable
public data class OneBotApiResult<T : Any>(
    val retcode: Int,
    val status: String? = null,
    val data: T? = null,
) {
    /**
     * 当可以为其附加‘原始信息’（例如原始的JSON字符串）
     * 时，在反序列化完成后可以为其赋值。
     *
     * 此属性的setter应当仅由**内部使用**，
     * 且应当在反序列化完成后立刻使用。
     */
    @Transient
    @set:InternalOneBotAPI
    public var raw: String? = null

    public val isSuccess: Boolean
        get() = retcode == RETCODE_SUCCESS

    public val dataOrThrow: T
        get() = data
            ?: when (retcode) {
                RETCODE_ASYNC -> throw IllegalStateException(
                    "`data` is null, " +
                        "this result may mean a failure or the request is asynchronous: " +
                        "retcode=$retcode, status=$status, raw=$raw"
                )

                else -> throw IllegalStateException(
                    "`data` is null, " +
                        "this result may mean a failure: retcode=$retcode, status=$status, raw=$raw"
                )
            }

    public companion object {
        /**
         * `status` 为 `ok` 表示操作成功，同时 `retcode` （返回码）会等于 `0`
         */
        public const val RETCODE_SUCCESS: Int = 0

        /**
         * `status` 为 `async` 表示请求已提交异步处理，此时 `retcode` 为 `1`，具体成功或失败将无法获知
         */
        public const val RETCODE_ASYNC: Int = 1

        /**
         * 获取一个以代表空成功结果的 [Unit] 作为结果类型的 [OneBotApiResult] 序列化器。
         */
        public fun emptySerializer(): KSerializer<OneBotApiResult<Unit>> = EmptyOneBotApiResultSerializer
    }
}

/**
 * 以代表空成功结果的 [Unit] 作为结果类型的 [OneBotApiResult] 序列化器。
 */
private object EmptyOneBotApiResultSerializer : KSerializer<OneBotApiResult<Unit>> {
    private val DEFAULT_EMPTY_SUCCESS_RESULT = OneBotApiResult(retcode = RETCODE_SUCCESS, status = "ok", data = Unit)
    private val EMPTY_RESULT = OneBotApiResult.serializer(Unit.serializer())

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): OneBotApiResult<Unit> {
        return decoder.decodeStructure(descriptor) {
            if (decodeSequentially()) {
                val retcode = decodeIntElement(descriptor, 0)
                val status = decodeNullableSerializableElement(descriptor, 1, String.serializer())
                decodeNullableSerializableElement(descriptor, 2, Unit.serializer())

                if (retcode == DEFAULT_EMPTY_SUCCESS_RESULT.retcode && status == DEFAULT_EMPTY_SUCCESS_RESULT.status) {
                    DEFAULT_EMPTY_SUCCESS_RESULT
                } else {
                    OneBotApiResult(
                        retcode = retcode,
                        status = status,
                        data = if (retcode == RETCODE_SUCCESS) Unit else null
                    )
                }

            } else {
                var retcodeValue = false
                var retcode = 0
                var status: String? = null
                var data = false

                loop@ while (true) {
                    when (val i = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break@loop
                        0 -> {
                            retcode = decodeIntElement(descriptor, i)
                            retcodeValue = true
                            if (retcode == RETCODE_SUCCESS) {
                                data = true
                            }
                        }

                        1 -> status = decodeNullableSerializableElement(descriptor, i, String.serializer())
                        2 -> {
                            decodeNullableSerializableElement(descriptor, i, Unit.serializer())
                        }

                        else -> throw SerializationException("Unexpected index $i")
                    }
                }

                if (!retcodeValue) {
                    throw MissingFieldException("retcode", descriptor.serialName)
                }

                if (retcode == DEFAULT_EMPTY_SUCCESS_RESULT.retcode && status == DEFAULT_EMPTY_SUCCESS_RESULT.status) {
                    DEFAULT_EMPTY_SUCCESS_RESULT
                } else {
                    OneBotApiResult(
                        retcode = retcode,
                        status = status,
                        data = if (data) Unit else null
                    )
                }

            }
        }
    }

    override val descriptor: SerialDescriptor = EMPTY_RESULT.descriptor

    override fun serialize(encoder: Encoder, value: OneBotApiResult<Unit>) {
        EMPTY_RESULT.serialize(encoder, value)
    }
}
