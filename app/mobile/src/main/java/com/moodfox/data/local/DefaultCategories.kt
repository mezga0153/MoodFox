package com.moodfox.data.local

import com.moodfox.data.local.db.CauseCategory

object DefaultCategories {

    val all = listOf(
        CauseCategory(name = "Work",             emoji = "💼", sortOrder = 0,  isDefault = true),
        CauseCategory(name = "Family",           emoji = "🏠", sortOrder = 1,  isDefault = true),
        CauseCategory(name = "Relationship",     emoji = "💑", sortOrder = 2,  isDefault = true),
        CauseCategory(name = "Friends / Social", emoji = "👥", sortOrder = 3,  isDefault = true),
        CauseCategory(name = "Sleep",            emoji = "😴", sortOrder = 4,  isDefault = true),
        CauseCategory(name = "Health",           emoji = "🏥", sortOrder = 5,  isDefault = true),
        CauseCategory(name = "Exercise",         emoji = "🏃", sortOrder = 6,  isDefault = true),
        CauseCategory(name = "Food",             emoji = "🍎", sortOrder = 7,  isDefault = true),
        CauseCategory(name = "Weather",          emoji = "🌤", sortOrder = 8,  isDefault = true),
        CauseCategory(name = "Money",            emoji = "💶", sortOrder = 9,  isDefault = true),
        CauseCategory(name = "Routine",          emoji = "🔄", sortOrder = 10, isDefault = true),
        CauseCategory(name = "Stress / Anxiety", emoji = "😰", sortOrder = 11, isDefault = true),
        CauseCategory(name = "Achievement",      emoji = "🏆", sortOrder = 12, isDefault = true),
        CauseCategory(name = "Rest",             emoji = "🛋", sortOrder = 13, isDefault = true),
        CauseCategory(name = "Nothing specific", emoji = "🤷", sortOrder = 14, isDefault = true),
        CauseCategory(name = "Other",            emoji = "➕", sortOrder = 15, isDefault = true),
    )
}
