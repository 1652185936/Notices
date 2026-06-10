package com.yhx.notices.domain.model

import kotlinx.serialization.Serializable

enum class AttachmentType { IMAGE, AUDIO, SKETCH, FILE, SCAN }

@Serializable
enum class RepeatRule { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, WEEKDAYS }

enum class ReminderTarget { NOTE, TODO }

enum class NoteSort { UPDATED, CREATED, TITLE }

enum class ListLayout { GRID, LIST }
