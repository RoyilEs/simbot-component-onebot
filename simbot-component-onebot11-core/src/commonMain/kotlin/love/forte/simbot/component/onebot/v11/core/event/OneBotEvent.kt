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

package love.forte.simbot.component.onebot.v11.core.event

import love.forte.simbot.common.id.LongID
import love.forte.simbot.common.time.Timestamp
import love.forte.simbot.event.Event

// public typealias OBSourceEvent // TODO

/**
 * OneBot11的[事件](https://github.com/botuniverse/onebot-11/tree/master/event)。
 *
 * @author ForteScarlet
 */
public interface OneBotEvent : Event {
    // TODO
    //  需要考虑一下到底是直接实现 Event，
    //  还是拆开实现

    /**
     * 事件发生的时间戳
     */
    public val timestamp: Timestamp
    // (既然是 `int64`, 那么原始数据应该是毫秒值)

    /**
     * 收到事件的机器人 QQ 号
     */
    public val selfId: LongID

    /**
     * 事件类型
     */
    public val postType: String
}