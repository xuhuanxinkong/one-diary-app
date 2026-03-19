package com.xinkong.diary.ui.screen.tag

const val DEFAULT_TAG_FOLDER = "我的笔记"
const val UNCLASSIFIED_TAG_NAME = "未分类"

data class TagIdentity(
    val folder: String,
    val name: String
)

fun normalizeFolder(folder: String?): String {
    val value = folder?.trim().orEmpty()
    return if (value.isEmpty()) DEFAULT_TAG_FOLDER else value
}

fun normalizeTagName(name: String?): String {
    val value = name?.trim().orEmpty()
    return if (value.isEmpty()) UNCLASSIFIED_TAG_NAME else value
}

fun normalizeTagIdentity(folder: String?, name: String?): TagIdentity {
    return TagIdentity(
        folder = normalizeFolder(folder),
        name = normalizeTagName(name)
    )
}
