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

package love.forte.simbot.component.onebot.v11.core.bot

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import love.forte.simbot.annotations.InternalSimbotAPI
import love.forte.simbot.bot.SerializableBotConfiguration
import love.forte.simbot.component.onebot.v11.core.component.OneBot11Component


/**
 * 用于可序列化场景下的反序列化目标。
 *
 * 仅用于序列化，不保证代码内直接使用的兼容性与稳定性，
 * 请不要直接在代码中使用它。
 *
 * @author ForteScarlet
 */
@InternalSimbotAPI
@Serializable
@SerialName(OneBot11Component.ID_VALUE)
public data class OneBotBotSerializableConfiguration(
    val authorization: Authorization,
    val config: Config? = null,
) : SerializableBotConfiguration() {
    @Serializable
    public data class Authorization(
        val botUniqueId: String,
        val apiServerHost: String? = null,
        val eventServerHost: String? = null,
        val accessToken: String? = null,
    )

    @Serializable
    public data class Config(
        /**
         * @see OneBotBotConfiguration.apiHttpRequestTimeoutMillis
         */
        val apiHttpRequestTimeoutMillis: Long? = null,
        /**
         * @see OneBotBotConfiguration.apiHttpConnectTimeoutMillis
         */
        val apiHttpConnectTimeoutMillis: Long? = null,
        /**
         * @see OneBotBotConfiguration.apiHttpSocketTimeoutMillis
         */
        val apiHttpSocketTimeoutMillis: Long? = null,
        /**
         * @see OneBotBotConfiguration.wsConnectMaxRetryTimes
         */
        val wsConnectMaxRetryTimes: Int? = null,
        /**
         * @see OneBotBotConfiguration.wsConnectRetryDelayMillis
         */
        val wsConnectRetryDelayMillis: Long? = null
    )

    public fun toConfiguration(): OneBotBotConfiguration =
        OneBotBotConfiguration().also { conf ->
            conf.botUniqueId = authorization.botUniqueId
            authorization.apiServerHost?.also { conf.apiServerHost = Url(it) }
            authorization.eventServerHost?.also { conf.eventServerHost = Url(it) }
            authorization.accessToken?.also { conf.accessToken = it }

            config?.apply {
                apiHttpRequestTimeoutMillis?.also { conf.apiHttpRequestTimeoutMillis = it }
                apiHttpConnectTimeoutMillis?.also { conf.apiHttpConnectTimeoutMillis = it }
                apiHttpSocketTimeoutMillis?.also { conf.apiHttpSocketTimeoutMillis = it }
                wsConnectMaxRetryTimes?.also { conf.wsConnectMaxRetryTimes = it }
                wsConnectRetryDelayMillis?.also { conf.wsConnectRetryDelayMillis = it }
            }
        }
}
