/*
 * Copyright (c) 2026 Proton AG.
 * This file is part of Proton Core.
 *
 * Proton Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Core.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.drive.drivelink.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.feature.flag.domain.entity.FeatureFlagId
import me.proton.core.drive.feature.flag.domain.extension.on
import me.proton.core.drive.feature.flag.domain.usecase.GetFeatureFlag
import me.proton.core.drive.link.domain.entity.LinkId
import me.proton.core.drive.link.domain.extension.userId
import me.proton.core.drive.volume.domain.entity.Volume.Type
import javax.inject.Inject

class CheckSdkFeatureFlag @Inject constructor(
    private val getVolumeType: GetVolumeType,
    private val getFeatureFlag: GetFeatureFlag,
) {
    suspend operator fun invoke(
        linkId: LinkId,
        main: (UserId) -> FeatureFlagId,
        photo: (UserId) -> FeatureFlagId,
    ): Result<Boolean> = coRunCatching {
        when (getVolumeType(linkId).getOrThrow()) {
            null, Type.UNKNOWN -> false
            Type.REGULAR -> getFeatureFlag(main(linkId.userId)).on
            Type.PHOTO -> getFeatureFlag(photo(linkId.userId)).on
        }
    }
}
