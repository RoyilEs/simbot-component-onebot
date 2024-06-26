/*
 * Copyright (c) 2022-2024. ForteScarlet.
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

package util

import Env
import isSnapshot

data class PublishConfigurableResult(
    val isSnapshotOnly: Boolean,
    val isReleaseOnly: Boolean,
    val isPublishConfigurable: Boolean = when {
        isSnapshotOnly -> isSnapshot()
        isReleaseOnly -> !isSnapshot()
        else -> true
    },
)


fun checkPublishConfigurable(): PublishConfigurableResult {
    val isSnapshotOnly =
        (System.getProperty("snapshotOnly") ?: System.getenv(Env.SNAPSHOT_ONLY))?.equals("true", true) == true
    val isReleaseOnly =
        (System.getProperty("releaseOnly") ?: System.getenv(Env.RELEASES_ONLY))?.equals("true", true) == true

    return PublishConfigurableResult(isSnapshotOnly, isReleaseOnly)
}

inline fun checkPublishConfigurable(block: PublishConfigurableResult.() -> Unit) {
    val v = checkPublishConfigurable()
    if (v.isPublishConfigurable) {
        v.block()
    }
}


inline val isCi: Boolean get() = systemProp(Env.IS_CI).toBoolean()

inline val isLinux: Boolean get() = systemProp("os.name")?.contains("Linux", true) ?: false
