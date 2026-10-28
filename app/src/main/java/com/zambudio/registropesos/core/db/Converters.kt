package com.zambudio.registropesos.core.db

import androidx.room.TypeConverter
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.domain.model.SessionStatus

/** Enums persistidos como TEXT con su nombre (las consultas SQL comparan con esos literales). */
class Converters {
    @TypeConverter
    fun sessionStatusToText(value: SessionStatus): String = value.name

    @TypeConverter
    fun textToSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun tombstoneTypeToText(value: TombstoneType): String = value.name

    @TypeConverter
    fun textToTombstoneType(value: String): TombstoneType = TombstoneType.valueOf(value)
}
