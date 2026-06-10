package com.yhx.notices.data.local

import androidx.room.TypeConverter
import com.yhx.notices.domain.model.AttachmentType
import com.yhx.notices.domain.model.ReminderTarget
import com.yhx.notices.domain.model.RepeatRule

class Converters {
    @TypeConverter fun toAttachmentType(v: String): AttachmentType = AttachmentType.valueOf(v)
    @TypeConverter fun fromAttachmentType(t: AttachmentType): String = t.name

    @TypeConverter fun toRepeatRule(v: String): RepeatRule = RepeatRule.valueOf(v)
    @TypeConverter fun fromRepeatRule(r: RepeatRule): String = r.name

    @TypeConverter fun toReminderTarget(v: String): ReminderTarget = ReminderTarget.valueOf(v)
    @TypeConverter fun fromReminderTarget(t: ReminderTarget): String = t.name
}
