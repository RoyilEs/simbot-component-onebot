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

package love.forte.simbot.component.onebot.v11.core.event.internal.message

import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.actor.OneBotGroup
import love.forte.simbot.component.onebot.v11.core.actor.OneBotMember
import love.forte.simbot.component.onebot.v11.core.actor.internal.toGroup
import love.forte.simbot.component.onebot.v11.core.actor.internal.toMember
import love.forte.simbot.component.onebot.v11.core.bot.internal.OneBotBotImpl
import love.forte.simbot.component.onebot.v11.core.bot.requestDataBy
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotAnonymousGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNoticeGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.internal.message.OneBotMessageContentImpl
import love.forte.simbot.component.onebot.v11.core.internal.message.toReceipt
import love.forte.simbot.component.onebot.v11.core.utils.sendGroupMsgApi
import love.forte.simbot.component.onebot.v11.core.utils.sendGroupTextMsgApi
import love.forte.simbot.component.onebot.v11.event.message.GroupMessageEvent
import love.forte.simbot.component.onebot.v11.message.OneBotMessageContent
import love.forte.simbot.component.onebot.v11.message.OneBotMessageReceipt
import love.forte.simbot.component.onebot.v11.message.resolveToOneBotSegmentList
import love.forte.simbot.message.Message
import love.forte.simbot.message.MessageContent

internal abstract class OneBotGroupMessageEventImpl(
    sourceEvent: GroupMessageEvent,
    final override val bot: OneBotBotImpl
) : OneBotGroupMessageEvent {

    override val id: ID
        get() = "${sourceEvent.groupId}-${sourceEvent.messageId}".ID

    override val authorId: ID
        get() = sourceEvent.userId

    override val messageContent: MessageContent = OneBotMessageContentImpl(
        sourceEvent.messageId,
        sourceEvent.message,
        bot
    )

    override suspend fun content(): OneBotGroup {
        return sourceEvent.toGroup(bot)
    }

    override suspend fun reply(text: String): OneBotMessageReceipt {
        val api = sendGroupTextMsgApi(
            target = sourceEvent.groupId,
            text,
            reply = sourceEvent.messageId
        )

        return api.requestDataBy(bot).toReceipt(bot)
    }

    override suspend fun reply(messageContent: MessageContent): OneBotMessageReceipt {
        if (messageContent is OneBotMessageContent) {
            return sendGroupMsgApi(
                target = sourceEvent.groupId,
                message = sourceEvent.message,
                reply = sourceEvent.messageId
            ).requestDataBy(bot).toReceipt(bot)
        }

        return reply(messageContent.messages)
    }

    override suspend fun reply(message: Message): OneBotMessageReceipt {
        return sendGroupMsgApi(
            target = sourceEvent.groupId,
            message = message.resolveToOneBotSegmentList(),
            reply = sourceEvent.messageId
        ).requestDataBy(bot).toReceipt(bot)
    }
}

internal class OneBotNormalGroupMessageEventImpl(
    override val sourceEventRaw: String?,
    override val sourceEvent: GroupMessageEvent,
    bot: OneBotBotImpl,
) : OneBotGroupMessageEventImpl(sourceEvent, bot),
    OneBotNormalGroupMessageEvent {
    override suspend fun author(): OneBotMember {
        return sourceEvent.sender.toMember(bot)
    }
}


internal class OneBotAnonymousGroupMessageEventImpl(
    override val sourceEventRaw: String?,
    override val sourceEvent: GroupMessageEvent,
    bot: OneBotBotImpl,
) : OneBotGroupMessageEventImpl(sourceEvent, bot),
    OneBotAnonymousGroupMessageEvent {
    override suspend fun author(): OneBotMember {
        return sourceEvent.sender.toMember(bot)
    }
}

internal class OneBotNoticeGroupMessageEventImpl(
    override val sourceEventRaw: String?,
    override val sourceEvent: GroupMessageEvent,
    bot: OneBotBotImpl,
) : OneBotGroupMessageEventImpl(sourceEvent, bot),
    OneBotNoticeGroupMessageEvent


internal class OneBotDefaultGroupMessageEventImpl(
    override val sourceEventRaw: String?,
    override val sourceEvent: GroupMessageEvent,
    bot: OneBotBotImpl,
) : OneBotGroupMessageEventImpl(sourceEvent, bot)