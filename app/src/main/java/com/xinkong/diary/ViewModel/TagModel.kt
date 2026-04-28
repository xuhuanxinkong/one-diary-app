package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatTag
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.repository.DiaryTag
import com.xinkong.diary.repository.TagFolder
import com.xinkong.diary.ui.screen.tag.DEFAULT_TAG_FOLDER
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME
import com.xinkong.diary.ui.screen.tag.normalizeTagIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 标签显示项数据类
 */
data class TagDisplayItem(
    val folder: String,
    val name: String,
    val colorInt: Int,
    val bg2Int: Int,
    val border2Int: Int,
    val displayName: String = name,
    val itemCount: Int = 0
)

data class TagGroupedResult(
    val groupedTags: Map<String, List<TagDisplayItem>>,
    val availableFolders: List<String>,
    val hiddenItemCount: Int = 0
)

/**
 * 未分类标签的配色方案
 * 分别为 Diary 和 Chat 定义不同的配色，确保视觉上易于区分
 */
object UnclassifiedColors {
    data class ColorScheme(
        val colorInt: Int,
        val bg2Int: Int,
        val border2Int: Int
    )
    
    val DIARY = ColorScheme(
        colorInt = 0xFF8B5F65.toInt(),    // 深紫红
        bg2Int = 0xFFFFF8E1.toInt(),
        border2Int = 0xFFC8E6C9.toInt()
    )
    
    val CHAT = ColorScheme(
        colorInt = 0xFF8B5F65.toInt(),    // 深蓝色
        bg2Int = 0xFFFFF8F8.toInt(),      // 浅靛蓝背景
        border2Int = 0xFFFFB6C1.toInt()   // 靛蓝边框
    )
}

// Unified TagModel combining Diary, Chat and Folder tag functionalities
class TagModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val tagDao = db.tagDao()
    private val diaryDao = db.diaryDao()
    private val chatDao = db.chatDao()

    private val _diaryTags = MutableStateFlow(listOf<DiaryTag>())
    val diaryTags: StateFlow<List<DiaryTag>> = _diaryTags.asStateFlow()

    private val _chatTags = MutableStateFlow(listOf<ChatTag>())
    val chatTags: StateFlow<List<ChatTag>> = _chatTags.asStateFlow()

    private val _tagFolders = MutableStateFlow(listOf<TagFolder>())
    val tagFolders: StateFlow<List<TagFolder>> = _tagFolders.asStateFlow()

    init {
        viewModelScope.launch {
            // 确保初始文件夹存在
            val ensureDefaultFolders = suspend {
                val currentFolders = tagDao.getAllTagFolders().first()
                if (currentFolders.none { it.name == "我的笔记" && it.type == "Diary" }) {
                    tagDao.insertTagFolder(TagFolder(name = "我的笔记", type = "Diary"))
                }
            }
            ensureDefaultFolders()

            launch {
                tagDao.getAllDiaryTags().collect { tags ->
                    _diaryTags.update { tags }
                }
            }
            launch {
                tagDao.getAllChatTags().collect { tags ->
                    _chatTags.update { tags }
                }
            }
            launch {
                tagDao.getAllTagFolders().collect { folders ->
                    _tagFolders.update { folders }
                }
            }
        }
    }

    fun addDiaryTag(tag: DiaryTag) {
        viewModelScope.launch { tagDao.insertDiaryTag(tag) }
    }

    fun deleteDiaryTag(tag: DiaryTag) {
        viewModelScope.launch { tagDao.deleteDiaryTag(tag) }
    }

    fun addChatTag(tag: ChatTag) {
        viewModelScope.launch { tagDao.insertChatTag(tag) }
    }

    fun deleteChatTag(tag: ChatTag) {
        viewModelScope.launch { tagDao.deleteChatTag(tag) }
    }

    fun addTagFolder(folder: TagFolder) {
        viewModelScope.launch {
            // 设置新的订单索引
            val maxOrder = _tagFolders.value.filter { it.type == folder.type }.maxOfOrNull { it.orderIndex } ?: 0
            tagDao.insertTagFolder(folder.copy(orderIndex = maxOrder + 1))
        }
    }

    fun reorderFolder(folderName: String, folderType: String, moveUp: Boolean) {
        viewModelScope.launch {
            val folders = _tagFolders.value.filter { it.type == folderType && it.name != DEFAULT_TAG_FOLDER }
                .sortedWith(compareBy({ it.orderIndex }, { it.name }))
                .toMutableList()

            val index = folders.indexOfFirst { it.name == folderName }
            if (index < 0) return@launch

            if (moveUp && index > 0) {
                val current = folders.removeAt(index)
                folders.add(index - 1, current)
            } else if (!moveUp && index < folders.size - 1) {
                val current = folders.removeAt(index)
                folders.add(index + 1, current)
            } else {
                return@launch
            }

            // 更新所有顺序以保持连续
            folders.forEachIndexed { i, f ->
                tagDao.insertTagFolder(f.copy(orderIndex = i + 1))
            }
        }
    }

    fun deleteTagFolder(folder: TagFolder) {
        viewModelScope.launch { tagDao.deleteTagFolder(folder) }
    }

    /**
     * 【重要】原子性重命名文件夹 - 防止中间状态出现新文件夹
     * @param oldFolderName 旧文件夹名称
     * @param newFolderName 新文件夹名称
     * @param folderType "Diary" 或 "Chat"
     * @param updateDiariesFn 更新日记的函数
     * @param updateChatsFn 更新对话的函数
     * @return 操作是否成功
     */
    fun renameFolderAtomic(
        oldFolderName: String,
        newFolderName: String,
        folderType: String,
        updateDiariesFn: (List<Diary>) -> Unit = {},
        updateChatsFn: (List<Chat>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val trimmedNewFolderName = newFolderName.trim()
                
                // 1. 检查是否名称相同
                if (trimmedNewFolderName == oldFolderName) return@launch
                
                // 2. 获取旧文件夹信息
                val oldFolderEntity = _tagFolders.value.firstOrNull {
                    it.name == oldFolderName && it.type == folderType
                } ?: return@launch
                
                // 3. 检查新文件夹是否已存在
                val newFolderExists = _tagFolders.value.any {
                    it.name == trimmedNewFolderName && it.type == folderType
                }

                // 文件夹名唯一：若目标已存在，直接拒绝本次重命名
                if (newFolderExists) return@launch
                
                // 4. 如果新文件夹不存在，创建它
                tagDao.insertTagFolder(
                    TagFolder(
                        name = trimmedNewFolderName,
                        type = folderType,
                        isHidden = oldFolderEntity.isHidden,
                        isAiBound = oldFolderEntity.isAiBound,
                        orderIndex = oldFolderEntity.orderIndex // 保留刚才的位置
                    )
                )

                // 5. 先迁移内容实体，确保不会因为旧数据引用导致旧文件夹“删不掉”
                if (folderType == "Diary") {
                    val diariesToMove = diaryDao.getDiariesByFolder(oldFolderName)
                    if (diariesToMove.isNotEmpty()) {
                        updateDiariesFn(diariesToMove.map { it.copy(tagFolder = trimmedNewFolderName) })
                    }
                } else {
                    val chatsToMove = chatDao.getChatsByFolder(oldFolderName)
                    if (chatsToMove.isNotEmpty()) {
                        updateChatsFn(chatsToMove.map { it.copy(tagFolder = trimmedNewFolderName) })
                    }
                }
                
                // 6. 迁移标签
                if (folderType == "Diary") {
                    val tagsToMigrate = tagDao.getDiaryTagsByFolder(oldFolderName)
                    tagsToMigrate.forEach { oldTag ->
                        val targetExists = tagDao.getDiaryTagsByFolder(trimmedNewFolderName)
                            .any { it.name == oldTag.name }
                        if (!targetExists) {
                            tagDao.insertDiaryTag(oldTag.copy(folder = trimmedNewFolderName))
                        }
                        tagDao.deleteDiaryTag(oldTag)
                    }
                } else {
                    val tagsToMigrate = tagDao.getChatTagsByFolder(oldFolderName)
                    tagsToMigrate.forEach { oldTag ->
                        val targetExists = tagDao.getChatTagsByFolder(trimmedNewFolderName)
                            .any { it.name == oldTag.name }
                        if (!targetExists) {
                            tagDao.insertChatTag(oldTag.copy(folder = trimmedNewFolderName))
                        }
                        tagDao.deleteChatTag(oldTag)
                    }
                }
                
                // 7. 删除旧文件夹（注意：必须在迁移之后）
                tagDao.deleteTagFolder(oldFolderEntity)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 【重要】原子性删除文件夹 - 将其所有内容移至默认文件夹
     * @param folderName 要删除的文件夹名称
     * @param folderType "Diary" 或 "Chat"
     * @param updateDiariesFn 更新日记的函数
     * @param updateChatsFn 更新对话的函数
     */
    fun deleteFolderAtomic(
        folderName: String,
        folderType: String,
        updateDiariesFn: (List<Diary>) -> Unit = {},
        updateChatsFn: (List<Chat>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val targetFolder = DEFAULT_TAG_FOLDER
                if (folderName == targetFolder) return@launch

                // 1. 获取文件夹信息
                val folderEntity = _tagFolders.value.firstOrNull {
                    it.name == folderName && it.type == folderType
                } ?: return@launch

                // 2. 确保默认文件夹存在
                val targetExists = _tagFolders.value.any {
                    it.name == targetFolder && it.type == folderType
                }
                if (!targetExists) {
                    tagDao.insertTagFolder(
                        TagFolder(
                            name = targetFolder,
                            type = folderType,
                            isHidden = false,
                            isAiBound = false
                        )
                    )
                }
                
                // 3. 先迁移内容实体到默认文件夹
                if (folderType == "Diary") {
                    val diariesToMove = diaryDao.getDiariesByFolder(folderName)
                    if (diariesToMove.isNotEmpty()) {
                        updateDiariesFn(diariesToMove.map { it.copy(tagFolder = targetFolder) })
                    }
                } else {
                    val chatsToMove = chatDao.getChatsByFolder(folderName)
                    if (chatsToMove.isNotEmpty()) {
                        updateChatsFn(chatsToMove.map { it.copy(tagFolder = targetFolder) })
                    }
                }

                // 4. 迁移标签到默认文件夹
                if (folderType == "Diary") {
                    val tagsToMigrate = tagDao.getDiaryTagsByFolder(folderName)
                    tagsToMigrate.forEach { oldTag ->
                        val targetExistsTag = tagDao.getDiaryTagsByFolder(targetFolder)
                            .any { it.name == oldTag.name }
                        if (!targetExistsTag) {
                            tagDao.insertDiaryTag(oldTag.copy(folder = targetFolder))
                        }
                        tagDao.deleteDiaryTag(oldTag)
                    }
                } else {
                    val tagsToMigrate = tagDao.getChatTagsByFolder(folderName)
                    tagsToMigrate.forEach { oldTag ->
                        val targetExistsTag = tagDao.getChatTagsByFolder(targetFolder)
                            .any { it.name == oldTag.name }
                        if (!targetExistsTag) {
                            tagDao.insertChatTag(oldTag.copy(folder = targetFolder))
                        }
                        tagDao.deleteChatTag(oldTag)
                    }
                }
                
                // 5. 删除文件夹
                tagDao.deleteTagFolder(folderEntity)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun buildDiaryGroupedTags(contentList: List<Diary>, includeHidden: Boolean = false): TagGroupedResult {
        return buildGroupedTags(contentList, _diaryTags.value, _tagFolders.value, "Diary", "diary", includeHidden)
    }

    fun buildChatGroupedTags(chatList: List<Chat>, includeHidden: Boolean = false): TagGroupedResult {
        return buildGroupedTags(chatList, _chatTags.value, _tagFolders.value, "Chat", "chat", includeHidden)
    }

    /**
     * 通用的分组构建方法，减少代码重复
     * @param contentList 日记或聊天列表
     * @param currentTags 数据库中的标签列表
     * @param currentFolders 所有文件夹
     * @param folderType 文件夹类型过滤："Diary" 或 "Chat"
     * @param tagType 标签类型标识："diary" 或 "chat"，用于未分类配色
     * @param includeHidden 是否包含被隐藏的文件夹
     */
    private fun <T : Any> buildGroupedTags(
        contentList: T,
        currentTags: List<Any>,
        currentFolders: List<TagFolder>,
        folderType: String,
        tagType: String,
        includeHidden: Boolean
    ): TagGroupedResult {
        val tagIdentityCounts = mutableMapOf<Pair<String, String>, Int>()
        val tagIdentities = when (contentList) {
            is List<*> -> {
                contentList.mapNotNull { item ->
                    when (item) {
                        is Diary -> normalizeTagIdentity(item.tagFolder, item.tag)
                        is Chat -> normalizeTagIdentity(item.tagFolder, item.tag)
                        else -> null
                    }
                }.onEach { identity ->
                    val key = identity.folder to identity.name
                    tagIdentityCounts[key] = (tagIdentityCounts[key] ?: 0) + 1
                }.distinct()
            }
            else -> emptyList()
        }

        val hiddenFolders = currentFolders
            .asSequence()
            .filter { it.type == folderType && it.isHidden }
            .map { it.name }
            .toSet()

        var hiddenItemCount = 0
        tagIdentityCounts.forEach { (key, count) ->
            if (key.first in hiddenFolders) {
                hiddenItemCount += count
            }
        }

        val folderCandidates = (
            tagIdentities.map { it.folder } +
                currentTags.mapNotNull { (it as? Any)?.let { tag ->
                    when (tag) {
                        is DiaryTag -> tag.folder
                        is ChatTag -> tag.folder
                        else -> null
                    }
                } } +
                currentFolders.filter { it.type == folderType }.map { it.name } +
                tagIdentities.filter { it.name == UNCLASSIFIED_TAG_NAME }.map { it.folder } +
                DEFAULT_TAG_FOLDER
            ).toSet()

        val orderMap = currentFolders.filter { it.type == folderType }.associate { it.name to it.orderIndex }
        val orderedFolders = sortFolders(folderCandidates, orderMap)

        val customTagsList = currentTags.mapNotNull { tag ->
            when (tag) {
                is DiaryTag -> TagDisplayItem(
                    folder = tag.folder,
                    name = tag.name,
                    colorInt = tag.colorInt,
                    bg2Int = tag.bg2Int,
                    border2Int = tag.border2Int,
                    itemCount = tagIdentityCounts[tag.folder to tag.name] ?: 0
                )
                is ChatTag -> TagDisplayItem(
                    folder = tag.folder,
                    name = tag.name,
                    colorInt = tag.colorInt,
                    bg2Int = tag.bg2Int,
                    border2Int = tag.border2Int,
                    itemCount = tagIdentityCounts[tag.folder to tag.name] ?: 0
                )
                else -> null
            }
        }

        val grouped = linkedMapOf<String, List<TagDisplayItem>>()
        orderedFolders.forEach { folder ->
            if (includeHidden || folder !in hiddenFolders) {
                grouped[folder] = buildDisplayItemsForFolder(
                    folder = folder,
                    existingNames = tagIdentities
                        .filter { it.folder == folder }
                        .map { it.name },
                    customTags = customTagsList.filter { it.folder == folder },
                    tagType = tagType,
                    folderCounts = tagIdentityCounts.filterKeys { it.first == folder }
                )
            }
        }

        return TagGroupedResult(
            groupedTags = grouped,
            availableFolders = orderedFolders,
            hiddenItemCount = hiddenItemCount
        )
    }

    private fun buildDisplayItemsForFolder(
        folder: String,
        existingNames: List<String>,
        customTags: List<TagDisplayItem>,
        tagType: String,
        folderCounts: Map<Pair<String, String>, Int>
    ): List<TagDisplayItem> {
        val customByName = customTags.associateBy { it.name }
        val unclassifiedColor = if (tagType == "diary") UnclassifiedColors.DIARY else UnclassifiedColors.CHAT

        val fromExisting = existingNames.map { name ->
            val count = folderCounts[folder to name] ?: 0
            customByName[name] ?: if (name == UNCLASSIFIED_TAG_NAME) {
                // 未分类标签使用专门的配色方案
                TagDisplayItem(
                    folder = folder,
                    name = name,
                    colorInt = unclassifiedColor.colorInt,
                    bg2Int = unclassifiedColor.bg2Int,
                    border2Int = unclassifiedColor.border2Int,
                    itemCount = count
                )
            } else {
                // 其他标签使用生成的回退色
                TagDisplayItem(
                    folder = folder,
                    name = name,
                    colorInt = generateFallbackColorInt(folder, name),
                    bg2Int = 0xFFFFFFFF.toInt(),
                    border2Int = 0xFFD9D9D9.toInt(),
                    itemCount = count
                )
            }
        }

        val merged = (fromExisting + customTags)
            .distinctBy { it.folder to it.name }

        val withUnclassified = if (merged.any { it.name == UNCLASSIFIED_TAG_NAME }) {
            merged
        } else {
            listOf(
                TagDisplayItem(
                    folder = folder,
                    name = UNCLASSIFIED_TAG_NAME,
                    displayName = UNCLASSIFIED_TAG_NAME,
                    colorInt = unclassifiedColor.colorInt,
                    bg2Int = unclassifiedColor.bg2Int,
                    border2Int = unclassifiedColor.border2Int
                )
            ) + merged
        }

        return withUnclassified.sortedWith(
            compareBy<TagDisplayItem> { if (it.name == UNCLASSIFIED_TAG_NAME) 0 else 1 }
                .thenBy { it.name }
        )
    }

    private fun sortFolders(folders: Set<String>, orderMap: Map<String, Int>): List<String> {
        return folders.sortedWith { f1, f2 ->
            when {
                f1 == f2 -> 0
                f1 == DEFAULT_TAG_FOLDER -> -1
                f2 == DEFAULT_TAG_FOLDER -> 1
                else -> {
                    val idx1 = orderMap[f1] ?: Int.MAX_VALUE
                    val idx2 = orderMap[f2] ?: Int.MAX_VALUE
                    if (idx1 != idx2) idx1.compareTo(idx2)
                    else f1.compareTo(f2)
                }
            }
        }
    }

    private fun generateFallbackColorInt(folder: String, name: String): Int {
        val hash = abs((folder + "#" + name).hashCode())
        val red = 80 + hash % 120
        val green = 90 + (hash / 3) % 120
        val blue = 100 + (hash / 7) % 120
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
