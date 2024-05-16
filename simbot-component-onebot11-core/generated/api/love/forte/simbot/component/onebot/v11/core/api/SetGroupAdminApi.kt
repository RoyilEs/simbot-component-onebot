package love.forte.simbot.component.onebot.v11.core.api

import kotlin.Any
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import love.forte.simbot.common.id.ID

/**
 * [`set_group_admin`-群组设置管理员](https://github.com/botuniverse/onebot-11/blob/master/api/public.md#set_group_admin-群组设置管理员)
 *
 * @author ForteScarlet
 */
public class SetGroupAdminApi private constructor(
    override val body: Any,
) : OneBotApi<Unit> {
    override val action: String
        get() = ACTION

    override val resultDeserializer: DeserializationStrategy<Unit>
        get() = Unit.serializer()

    override val apiResultDeserializer: DeserializationStrategy<OneBotApiResult<Unit>>
        get() = OneBotApiResult.emptySerializer()

    public companion object Factory {
        private const val ACTION: String = "set_group_admin"

        /**
         * 构建一个 [SetGroupAdminApi].
         *
         * @param groupId 群号
         * @param userId 要设置管理员的 QQ 号
         * @param enable true 为设置，false 为取消
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            groupId: ID,
            userId: ID,
            enable: Boolean? = null,
        ): SetGroupAdminApi = SetGroupAdminApi(Body(groupId, userId, enable))
    }

    /**
     * @param groupId 群号
     * @param userId 要设置管理员的 QQ 号
     * @param enable true 为设置，false 为取消
     */
    @Serializable
    internal data class Body(
        @SerialName("group_id")
        internal val groupId: ID,
        @SerialName("user_id")
        internal val userId: ID,
        internal val enable: Boolean? = null,
    )
}
