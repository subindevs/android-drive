/*
 * Copyright (c) 2026 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.drive.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import me.proton.core.auth.domain.usecase.GetPrimaryUser
import me.proton.core.drive.base.domain.extension.toResult
import me.proton.core.drive.base.domain.formatter.DateTimeFormatter
import me.proton.core.drive.base.domain.log.LogTag.DRIVE_SDK
import me.proton.core.drive.base.domain.usecase.ReportError
import me.proton.core.drive.base.domain.util.coRunCatching
import me.proton.core.drive.crypto.domain.usecase.DecryptLinkXAttr
import me.proton.core.drive.file.base.domain.extension.toXAttr
import me.proton.core.drive.link.domain.entity.FileId
import me.proton.core.drive.link.domain.usecase.GetLink
import me.proton.core.drive.share.domain.entity.ShareId
import me.proton.core.drive.volume.domain.entity.VolumeId
import me.proton.core.drive.volume.domain.usecase.GetVolume
import me.proton.drive.sdk.entity.LegacyNodeUid
import me.proton.drive.sdk.telemetry.DecryptionErrorEvent
import me.proton.drive.sdk.telemetry.EncryptedField
import javax.inject.Inject

class ReportExtraInfo @Inject constructor(
    private val getPrimaryUser: GetPrimaryUser,
    private val getVolume: GetVolume,
    private val getLink: GetLink,
    private val decryptLinkXAttr: DecryptLinkXAttr,
    private val dateTimeFormatter: DateTimeFormatter,
    private val reportError: ReportError,
) {
    suspend operator fun invoke(decryptionErrorEvent: DecryptionErrorEvent) = coRunCatching {
        if (decryptionErrorEvent.field != EncryptedField.NODE_EXTENDED_ATTRIBUTES) {
            return@coRunCatching
        }

        val userId = checkNotNull(getPrimaryUser()) { "No primary user" }.userId
        val nodeUid = LegacyNodeUid(decryptionErrorEvent.uid)
        val volume = getVolume(userId, VolumeId(nodeUid.volumeId)).toResult().getOrThrow()
        val linkId = FileId(ShareId(userId, volume.shareId), nodeUid.linkId)
        val link = getLink(linkId).toResult().getOrThrow()
        val xAttr = link.xAttr
        if (xAttr == null) {
            reportError(
                tag = DRIVE_SDK,
                message = "Decryption error: extended attributes null\n$decryptionErrorEvent"
            )
            return@coRunCatching
        }
        if (xAttr.isEmpty()) {
            reportError(
                tag = DRIVE_SDK,
                message = "Decryption error: extended attributes empty\n$decryptionErrorEvent"
            )
            return@coRunCatching
        }
        val decryptXAttr = decryptLinkXAttr(link).getOrThrow()
        val xAttrResult = decryptXAttr.text.toXAttr()
        if (xAttrResult.isFailure) {
            reportError(
                tag = DRIVE_SDK,
                error = xAttrResult.exceptionOrNull()!!,
                message = "Decryption error: json parsing (${decryptXAttr.text.length})\n$decryptionErrorEvent"
            )
            return@coRunCatching
        }
        val json = Json.parseToJsonElement(decryptXAttr.text) as? JsonObject ?: run {
            reportError(
                tag = DRIVE_SDK,
                message = "Decryption error: unexpected JSON shape\n$decryptionErrorEvent"
            )
            return@coRunCatching
        }

        val modificationTime = xAttrResult.getOrNull()?.common?.modificationTime
        val fieldInfo = if (
            decryptionErrorEvent.error?.contains("ModificationTime") == true
            && modificationTime != null
        ) {
            dateTimeFormatter.parseFromIso8601String(modificationTime).exceptionOrNull()
                ?.let { error ->
                    reportError(
                        tag = DRIVE_SDK,
                        error = error,
                        message = "Decryption error: json structure: ${json.describe()}\n$decryptionErrorEvent"
                    )
                    return@coRunCatching
                }
            "modificationTime: ${modificationTime.replace(Regex("\\d"), "#")}, "
        } else {
            ""
        }

        reportError(
            tag = DRIVE_SDK,
            message = "Decryption error: $fieldInfo json structure: ${json.describe()}\n$decryptionErrorEvent"
        )

    }.onFailure { error ->
        reportError(
            tag = DRIVE_SDK,
            error = error,
            message = "Decryption error: extra info error\n$decryptionErrorEvent"
        )
    }
}

private fun JsonElement.describeValue(): String = when (this) {
    is JsonNull -> "null"
    is JsonPrimitive -> when {
        isString -> "String(${content.length})"
        booleanOrNull != null -> "Boolean(${content.length})"
        else -> "Number(${content.length})"
    }

    is JsonArray -> "Array(${size})"
    is JsonObject -> describe()
}

private fun JsonObject.describe(): String =
    entries.joinToString(
        separator = ", ",
        prefix = "{",
        postfix = "}"
    ) { (key, value) -> "$key=${value.describeValue()}" }
