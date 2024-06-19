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

package love.forte.simbot.component.onebot.v11.core.bot.internal

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.modules.overwriteWith
import love.forte.simbot.annotations.FragileSimbotAPI
import love.forte.simbot.bot.JobBasedBot
import love.forte.simbot.common.collectable.Collectable
import love.forte.simbot.common.collectable.flowCollectable
import love.forte.simbot.common.coroutines.IOOrDefault
import love.forte.simbot.common.function.invokeWith
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.LongID.Companion.ID
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.OneBot11
import love.forte.simbot.component.onebot.v11.core.actor.OneBotFriend
import love.forte.simbot.component.onebot.v11.core.actor.OneBotGroup
import love.forte.simbot.component.onebot.v11.core.actor.OneBotMember
import love.forte.simbot.component.onebot.v11.core.actor.OneBotStranger
import love.forte.simbot.component.onebot.v11.core.actor.internal.toFriend
import love.forte.simbot.component.onebot.v11.core.actor.internal.toGroup
import love.forte.simbot.component.onebot.v11.core.actor.internal.toMember
import love.forte.simbot.component.onebot.v11.core.actor.internal.toStranger
import love.forte.simbot.component.onebot.v11.core.api.*
import love.forte.simbot.component.onebot.v11.core.bot.*
import love.forte.simbot.component.onebot.v11.core.component.OneBot11Component
import love.forte.simbot.component.onebot.v11.core.event.OneBotUnknownEvent
import love.forte.simbot.component.onebot.v11.core.event.OneBotUnsupportedEvent
import love.forte.simbot.component.onebot.v11.core.event.internal.message.*
import love.forte.simbot.component.onebot.v11.core.event.internal.meta.OneBotHeartbeatEventImpl
import love.forte.simbot.component.onebot.v11.core.event.internal.meta.OneBotLifecycleEventImpl
import love.forte.simbot.component.onebot.v11.core.event.internal.notice.*
import love.forte.simbot.component.onebot.v11.core.event.internal.request.OneBotFriendRequestEventImpl
import love.forte.simbot.component.onebot.v11.core.event.internal.request.OneBotGroupRequestEventImpl
import love.forte.simbot.component.onebot.v11.core.event.internal.stage.OneBotBotStartedEventImpl
import love.forte.simbot.component.onebot.v11.core.internal.message.OneBotMessageContentImpl
import love.forte.simbot.component.onebot.v11.core.utils.onEachErrorLog
import love.forte.simbot.component.onebot.v11.event.UnknownEvent
import love.forte.simbot.component.onebot.v11.event.message.RawGroupMessageEvent
import love.forte.simbot.component.onebot.v11.event.message.RawPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.event.meta.RawHeartbeatEvent
import love.forte.simbot.component.onebot.v11.event.meta.RawLifecycleEvent
import love.forte.simbot.component.onebot.v11.event.notice.*
import love.forte.simbot.component.onebot.v11.event.request.RawFriendRequestEvent
import love.forte.simbot.component.onebot.v11.event.request.RawGroupRequestEvent
import love.forte.simbot.component.onebot.v11.event.resolveEventSerializer
import love.forte.simbot.component.onebot.v11.event.resolveEventSubTypeFieldName
import love.forte.simbot.component.onebot.v11.message.OneBotMessageContent
import love.forte.simbot.event.Event
import love.forte.simbot.event.EventProcessor
import love.forte.simbot.logger.LoggerFactory
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import love.forte.simbot.component.onebot.v11.event.RawEvent as OBRawEvent


/**
 * [OneBotBot] 的实现
 * @author ForteScarlet
 */
internal class OneBotBotImpl(
    private val uniqueId: String,
    override val coroutineContext: CoroutineContext,
    override val job: Job,
    override val configuration: OneBotBotConfiguration,
    override val component: OneBot11Component,
    private val eventProcessor: EventProcessor,
    baseDecoderJson: Json,
) : OneBotBot, JobBasedBot() {
    override val apiClient: HttpClient
    private val wsClient: HttpClient

    init {
        apiClient = resolveHttpClient()
        wsClient = resolveWsClient()
        job.invokeOnCompletion { apiClient.close() }
    }

    override val decoderJson: Json = Json(baseDecoderJson) {
        configuration.serializersModule?.also { confMd ->
            serializersModule = serializersModule overwriteWith confMd
        }
    }

    override val subContext = coroutineContext.minusKey(Job)

    private val logger = LoggerFactory
        .getLogger(
            "love.forte.simbot.component.onebot.v11.core.bot.OneBotBot.$uniqueId"
        )

    private val eventServerHost = configuration.eventServerHost

    private val connectMaxRetryTimes = configuration.wsConnectMaxRetryTimes

    private val connectRetryDelay = max(configuration.wsConnectRetryDelayMillis, 0L).milliseconds

    private inline fun resolveHttpClient(crossinline block: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
        val apiClientConfigurer = configuration.apiClientConfigurer

        val engine = configuration.apiClientEngine
        val engineFactory = configuration.apiClientEngineFactory
        // 不能二者都有
        require(!(engine != null && engineFactory != null)) {
            "`apiClientEngine` and `apiClientEngineFactory` can only have one that is not null."
        }

        return when {
            engine == null && engineFactory == null -> HttpClient {
                apiClientConfigurer?.invokeWith(this)
                block()
            }

            engine != null -> HttpClient(engine) {
                apiClientConfigurer?.invokeWith(this)
                block()
            }

            engineFactory != null -> HttpClient(engineFactory) {
                apiClientConfigurer?.invokeWith(this)
                block()
            }

            else ->
                throw IllegalArgumentException("`engine` and `engineFactory` only need one.")
        }
    }

    private inline fun resolveWsClient(
        crossinline wsConfig: WebSockets.Config.() -> Unit = {},
        crossinline block: HttpClientConfig<*>.() -> Unit = {}
    ): HttpClient {
        val engine = configuration.wsClientEngine
        val engineFactory = configuration.wsClientEngineFactory
        // 不能二者都有
        require(!(engine != null && engineFactory != null)) {
            "`wsClientEngine` and `wsClientEngineFactory` can only have one that is not null."
        }

        return when {
            engine == null && engineFactory == null -> HttpClient {
                WebSockets {
                    wsConfig()
                }
                block()
            }

            engine != null -> HttpClient(engine) {
                WebSockets {
                    wsConfig()
                }
                block()
            }

            engineFactory != null -> HttpClient(engineFactory) {
                WebSockets {
                    wsConfig()
                }
                block()
            }

            else ->
                throw IllegalArgumentException("`engine` and `engineFactory` only need one.")
        }
    }

    override val apiHost: Url = configuration.apiServerHost

    override val accessToken: String? = configuration.accessToken

    override val id: ID = uniqueId.ID

    @Volatile
    private var _loginInfoResult: GetLoginInfoResult? = null

    override suspend fun queryLoginInfo(): GetLoginInfoResult {
        val result = GetLoginInfoApi.create().requestDataBy(this)
        _loginInfoResult = result
        return result
    }

    private val loginInfoResult: GetLoginInfoResult
        // LoginInfo尚未初始化
        get() = checkNotNull(_loginInfoResult) {
            "Login info has not been initialised"
        }

    override val userId: ID
        get() = loginInfoResult.userId

    override val name: String
        get() = loginInfoResult.nickname

    override fun isMe(id: ID): Boolean {
        if (id == this.id) return true
        return _loginInfoResult?.let { id == it.userId }
            ?: true
    }

    /**
     * 当前的 ws session
     *
     * 需要在 [startLock] 中进行修改
     */
    @Volatile
    private var wsSession: WsEventSession? = null
    private val startLock = Mutex()


    override suspend fun start(): Unit = startLock.withLock {
        job.ensureActive()

        // 更新个人信息
        val info = queryLoginInfo()
        logger.debug("Update bot login info: {}", info)

        wsSession = createEventSession().also { s ->
            // init it first
            val initialSession = s.createSessionWithRetry()
            launch { s.launch(initialSession) }
        }

        if (!isStarted) {
            isStarted = true
            launch {
                eventProcessor
                    .push(OneBotBotStartedEventImpl(this@OneBotBotImpl))
                    .onEachErrorLog(logger)
                    .collect()
            }
        }
    }

    private fun createEventSession(): WsEventSession {
        // Cancel current session if exists
        val currentSession = wsSession
        wsSession = null
        currentSession?.cancel()

        // OB11 似乎没有什么心跳之类乱七八糟的，似乎可以直接省略状态机
        // 直接连接、断线重连
        return WsEventSession()
    }


    private inner class WsEventSession {
        private val sessionJob = Job(this@OneBotBotImpl.job)
        private var session: DefaultWebSocketSession? = null

        suspend fun createSession(): DefaultWebSocketSession {
            return wsClient.webSocketSession {
                url {
                    takeFrom(eventServerHost)
                    accessToken?.also { bearerAuth(it) }
                }
            }
        }

        suspend fun createSessionWithRetry(): DefaultWebSocketSession? {
            var session: DefaultWebSocketSession? = null
            var retryTimes = 0

            while (session == null && retryTimes <= connectMaxRetryTimes) {
                try {
                    logger.debug("Connect to ws server {}", eventServerHost)
                    session = createSession()
                } catch (e: Exception) {
                    retryTimes++

                    @Suppress("ConvertTwoComparisonsToRangeCheck")
                    if (connectMaxRetryTimes > 0 && retryTimes > connectMaxRetryTimes) {
                        "Connect to ws server $eventServerHost failed in $retryTimes times.".also { msg ->
                            val ex = IllegalStateException(msg)
                            sessionJob.completeExceptionally(ex)

                            throw ex
                        }
                    }

                    if (logger.isWarnEnabled()) {
                        logger.warn(
                            "Connect to ws server {} failed: {}, retry in {}...",
                            eventServerHost,
                            e.message,
                            connectRetryDelay.toString(),
                            e,
                        )
                    }

                    delay(connectRetryDelay)
                    continue
                }
            }

            if (session == null || retryTimes >= connectMaxRetryTimes) {
                sessionJob.completeExceptionally(
                    IllegalStateException("Connect to ws server failed in $retryTimes times.")
                )

                return null
            }

            return session
        }

        /**
         * 开始创建连接并持续地接收事件。
         * 在接收过程中，只要 [sessionJob] 未被关闭，
         * 如果会话被断开（通常是被远端服务器断开）
         * 则会尝试重连（并顺便修改 [session] 的值）。
         *
         * 如果因为重试失败而被终结，
         * 也会连带着 [OneBotBotImpl.job] 一起结束。
         *
         */
        @OptIn(DelicateCoroutinesApi::class)
        suspend fun launch(initialSession: DefaultWebSocketSession? = null) {
            var session: DefaultWebSocketSession? = initialSession
            while (sessionJob.isActive) {
                if (session?.isActive != true) {
                    session = null
                }

                val currentSession = session ?: createSessionWithRetry().also {
                    session = it
                }

                if (currentSession == null) return

                logger.debug("Connected to ws server {}, session: {}", eventServerHost, currentSession)

                this@WsEventSession.session = currentSession

                val completionHandle = sessionJob.invokeOnCompletion {
                    if (currentSession.isActive) {
                        currentSession.cancel("Job is completed: ${it?.message}", it)
                    }
                }

                receiveEvent(currentSession)

                // The Session is done or dead,
                // or the job is done.

                // 如果会话仍然处于活跃状态，
                // 尝试关闭它，并首先尝试在异步中通过发送 close 数据包的形式进行关闭
                // 如果 5s 内无法完成此行为，则直接使用 cancel
                if (currentSession.isActive) {
                    GlobalScope.launch(Dispatchers.IOOrDefault) {
                        try {
                            withTimeout(5.seconds) {
                                currentSession.close()
                            }
                        } catch (timeout: TimeoutCancellationException) {
                            currentSession.cancel("Session close timeout: $timeout", timeout)
                        }
                    }
                } else {
                    // 否则，取消回调即可
                    // 不活跃的会话可能是被回调关闭的，也可能不是，但无所谓
                    completionHandle.dispose()
                }

                // 等待关闭完成
                val reason = currentSession.closeReason.await()
                logger.debug("Session {} done. The reason: {}", currentSession, reason)
            }

            logger.debug(
                "The EventSession is done.",
                isActive,
                sessionJob.isActive
            )
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private suspend fun receiveEvent(session: DefaultWebSocketSession) {
            with(session) {
                while (isActive && sessionJob.isActive) {
                    val frameResult = incoming.receiveCatching()

                    if (!frameResult.isSuccess) {
                        if (frameResult.isClosed) {
                            logger.debug("Session received Close frame result: {}", frameResult)
                            break
                        }
                        if (frameResult.isFailure) {
                            val ex = frameResult.exceptionOrNull()
                            logger.debug(
                                "Session received Failure frame result: {}, exception: {}",
                                frameResult,
                                ex,
                                ex
                            )

                            when (ex) {
                                is CancellationException -> break
                                else -> {
                                    continue
                                }
                            }
                        }

                        continue
                    }

                    val eventRaw = when (val frame = frameResult.getOrNull()) {
                        is Frame.Text -> frame.readText()
                        is Frame.Binary -> frame.readBytes().decodeToString()
                        else -> {
                            logger.debug(
                                "Received frame {}, but is not Text or Binary, skip resolve."
                            )

                            null
                        }
                    } ?: continue

                    val event = kotlin.runCatching {
                        resolveRawEvent(eventRaw)
                    }.getOrElse { e ->
                        val exMsg = "Failed to resolve raw event $eventRaw, " +
                            "session and bot will be closed exceptionally"

                        val ex = IllegalStateException(
                            exMsg,
                            e
                        )
                        // 接收的事件解析出现错误，
                        // 这应该是预期外的情况，
                        // 直接终止 session 和 Bot
                        session.closeExceptionally(ex)
                        job.cancel(exMsg, ex)

                        throw ex
                    }

                    pushEvent(resolveRawEventToEvent(eventRaw, event))
                }
            }
        }

        /**
         * 解析数据包字符串为 [Event]。
         */
        @OptIn(FragileSimbotAPI::class)
        private fun resolveRawEvent(text: String): OBRawEvent {
            val obj = OneBot11.DefaultJson.decodeFromString(
                JsonObject.serializer(),
                text
            )

            val postType = requireNotNull(obj["post_type"]?.jsonPrimitive?.content) {
                "Missing required event property 'post_type'"
            }

            val subTypeFieldName = resolveEventSubTypeFieldName(postType) ?: "${postType}_type"
            val subType = obj[subTypeFieldName]?.jsonPrimitive?.content

            fun toUnknown(): UnknownEvent {
                val time = obj["time"]?.jsonPrimitive?.long ?: -1L
                val selfId = obj["self_id"]?.jsonPrimitive?.long?.ID ?: 0L.ID
                return UnknownEvent(time, selfId, postType, text)
            }

            if (subType == null) {
                // 一个不规则的 unknown event
                return toUnknown()
            }

            resolveEventSerializer(postType, subType)?.let {
                return OneBot11.DefaultJson.decodeFromJsonElement(it, obj)
            } ?: run {
                return toUnknown()
            }
        }

        private fun pushEvent(event: Event): Job {
            return eventProcessor
                .push(event)
                .onEachErrorLog(logger)
                .launchIn(this@OneBotBotImpl)
        }

        fun cancel() {
            sessionJob.cancel()
        }
    }

    override val contactRelation: OneBotBotFriendRelation = FriendRelationImpl()

    private inner class FriendRelationImpl : OneBotBotFriendRelation {
        override val contacts: Collectable<OneBotFriend>
            get() = flowCollectable {
                val resultList = GetFriendListApi.create()
                    .requestDataBy(this@OneBotBotImpl)

                for (result in resultList) {
                    emit(result.toFriend(this@OneBotBotImpl))
                }
            }

        override suspend fun stranger(id: ID): OneBotStranger =
            GetStrangerInfoApi
                .create(userId = id)
                .requestDataBy(this@OneBotBotImpl)
                .toStranger(this@OneBotBotImpl)
    }

    // 与群聊相关的操作
    override val groupRelation: OneBotBotGroupRelation = GroupRelationImpl()

    private inner class GroupRelationImpl : OneBotBotGroupRelation {
        override val groups: Collectable<OneBotGroup>
            get() = flowCollectable {
                val resultList = GetGroupListApi.create()
                    .requestDataBy(this@OneBotBotImpl)

                for (groupInfoResult in resultList) {
                    emit(
                        groupInfoResult.toGroup(
                            this@OneBotBotImpl,
                            // TODO owner?
                        )
                    )
                }

            }

        override suspend fun group(id: ID): OneBotGroup {
            val result = GetGroupInfoApi.create(id)
                .requestResultBy(this@OneBotBotImpl)

            // TODO 如何检测不存在？

            return result.dataOrThrow.toGroup(
                this@OneBotBotImpl,
                // TODO owner?
            )
        }

        override suspend fun member(groupId: ID, memberId: ID): OneBotMember {
            // TODO 如何检测不存在？
            return GetGroupMemberInfoApi.create(groupId, userId)
                .requestDataBy(this@OneBotBotImpl).toMember(this@OneBotBotImpl)
        }
    }

    override suspend fun getCookies(domain: String?): GetCookiesResult =
        GetCookiesApi.create(domain).requestDataBy(this)

    override suspend fun getCredentials(domain: String?): GetCredentialsResult =
        GetCredentialsApi.create(domain).requestDataBy(this)

    override suspend fun getCsrfToken(): GetCsrfTokenResult =
        GetCsrfTokenApi.create().requestDataBy(this)

    override suspend fun getMessageContent(messageId: ID): OneBotMessageContent {
        val result = GetMsgApi.create(messageId).requestDataBy(this)
        return OneBotMessageContentImpl(
            result.messageId,
            result.message,
            this
        )
    }

    override fun toString(): String =
        "OneBotBot(uniqueId='$uniqueId', isStarted=$isStarted, isActive=$isActive)"
}


@OptIn(FragileSimbotAPI::class)
internal fun OneBotBotImpl.resolveRawEventToEvent(raw: String, event: OBRawEvent): Event {
    val bot = this

    fun unsupported(): OneBotUnsupportedEvent =
        OneBotUnsupportedEvent(raw, event)

    return when (event) {
        //region 消息事件
        // 群消息、匿名消息、系统消息
        is RawGroupMessageEvent -> when (event.subType) {
            RawGroupMessageEvent.SUB_TYPE_NORMAL ->
                OneBotNormalGroupMessageEventImpl(raw, event, bot)

            RawGroupMessageEvent.SUB_TYPE_ANONYMOUS ->
                OneBotAnonymousGroupMessageEventImpl(raw, event, bot)

            RawGroupMessageEvent.SUB_TYPE_NOTICE ->
                OneBotNoticeGroupMessageEventImpl(raw, event, bot)

            else -> OneBotDefaultGroupMessageEventImpl(raw, event, bot)
        }

        // 好友私聊消息、成员临时会话
        is RawPrivateMessageEvent -> when (event.subType) {
            RawPrivateMessageEvent.SUB_TYPE_FRIEND ->
                OneBotFriendMessageEventImpl(raw, event, bot)

            RawPrivateMessageEvent.SUB_TYPE_GROUP ->
                OneBotGroupPrivateMessageEventImpl(raw, event, bot)

            else -> OneBotDefaultPrivateMessageEventImpl(raw, event, bot)
        }
        //endregion

        //region 元事件
        is RawLifecycleEvent -> OneBotLifecycleEventImpl(raw, event, bot)
        is RawHeartbeatEvent -> OneBotHeartbeatEventImpl(raw, event, bot)
        //endregion

        //region 申请事件
        is RawFriendRequestEvent -> OneBotFriendRequestEventImpl(raw, event, bot)
        is RawGroupRequestEvent -> OneBotGroupRequestEventImpl(raw, event, bot)
        //endregion

        //region notice events
        is RawFriendAddEvent -> OneBotFriendAddEventImpl(raw, event, bot)
        is RawFriendRecallEvent -> OneBotFriendRecallEventImpl(raw, event, bot)
        is RawGroupAdminEvent -> OneBotGroupAdminEventImpl(raw, event, bot)
        is RawGroupBanEvent -> OneBotGroupBanEventImpl(raw, event, bot)
        is RawGroupIncreaseEvent -> OneBotGroupMemberIncreaseEventImpl(raw, event, bot)
        is RawGroupDecreaseEvent -> OneBotGroupMemberDecreaseEventImpl(raw, event, bot)
        is RawGroupRecallEvent -> OneBotGroupRecallEventImpl(raw, event, bot)
        is RawGroupUploadEvent -> OneBotGroupUploadEventImpl(raw, event, bot)
        is RawNotifyEvent -> when (event.subType) {
            RawNotifyEvent.SUB_TYPE_HONOR -> OneBotHonorEventImpl(raw, event, bot)
            RawNotifyEvent.SUB_TYPE_LUCKY_KING -> OneBotLuckyKingEventImpl(raw, event, bot)
            RawNotifyEvent.SUB_TYPE_POKE -> when {
                event.selfId.value == event.targetId?.value ->
                    OneBotBotSelfPokeEventImpl(raw, event, bot)

                else -> OneBotMemberPokeEventImpl(raw, event, bot)
            }

            // Unsupported
            else -> unsupported()
        }

        //endregion
        is UnknownEvent -> OneBotUnknownEvent(raw, event)
        else -> unsupported()
    }
}
