package com.demo.features.mainframe.data

import kotlinx.coroutines.delay

/**
 * 业务 Mock 数据源。
 *
 * 真实场景由 RemoteDataSource + LocalDB + Repository 组合实现，
 * 本工程为 AssembleKit 三屏滑动主框架样例，故用静态 Mock 数据演示。
 *
 * 接口大多声明为 suspend：
 * - 让 Shell ViewModel 通过 `suspend { repository.load() }.execute { copy(notes = it) }`
 *   把结果包装成 Mavericks `Async<T>`（Uninitialized → Loading → Success/Fail）。
 * - 比同步直返更贴近真实网络/DB 场景，便于演示 Loading 状态。
 */
object MockRepository {

    // ==================== Feed ====================
    data class FeedNote(
        val id: String,
        val title: String,
        val author: String,
        val likes: Int,
        /** 卡片随机高度比例（瀑布流用） */
        val aspectRatio: Float,
        val coverColor: Int,
        val avatarColor: Int,
        val tag: String?
    )

    private val titles = listOf(
        "今日穿搭｜春日通勤怎么穿才能不会出错",
        "Moon song (original)｜温柔的月光治愈系民谣",
        "Toronto | The Brick Room 复古咖啡探店",
        "Mendocino 3 Day 2 Nights 加州海岸自驾全攻略",
        "Trader Joe's Candle - A WTF Scent? 新品测评",
        "京都赏樱地图 ｜ 小众路线 + 拍照机位整理",
        "20 平米小户型也能拥有衣帽间 ｜ 改造前后对比",
        "周末可复刻 ｜ 香煎三文鱼配芦笋健身餐",
        "iPad mini 7 入手两周｜真实使用感受",
        "夏日清凉饮品 ｜ 芒果西米露在家也能做",
        "深圳周末徒步路线 ｜ 大鹏半岛 12 公里",
        "护肤新发现｜油皮亲妈级精华大公开",
        "复古 Vintage 穿搭 ｜ 二手店淘货实录",
        "上海宝藏咖啡馆地图 ｜ 武康路必去 5 家",
        "宝宝辅食 ｜ 8 个月营养食谱合集",
        "桌面好物 ｜ 100 元打造极简工作台",
        "京阪奈 7 日 ｜ 樱花季最美打卡点",
        "iPhone 摄影｜手持长曝光的 5 个技巧",
        "新手健身 ｜ 我是这样三个月减 20 斤",
        "周末烘焙 ｜ 不需要烤箱的奶酪蛋糕"
    )
    private val authors = listOf(
        "Savannah Vinson", "mwdii", "Summer Flowers", "33nevermind",
        "Lina Vonti", "Drachenrunesage", "Travel.Lin", "FoodieCat",
        "DesignDaily", "GreenLeaf", "巴恩", "小鹿", "阿兰", "翼德"
    )
    private val coverColors = listOf(
        0xFFFFD9DC.toInt(), 0xFFD9E8FF.toInt(), 0xFFE3FFD9.toInt(),
        0xFFFFEFD9.toInt(), 0xFFEEDDFF.toInt(), 0xFFFFE6D9.toInt(),
        0xFFD9F0FF.toInt(), 0xFFFFF1A8.toInt(), 0xFFFFCAD4.toInt()
    )
    private val avatarColors = listOf(
        0xFFFFB3C1.toInt(), 0xFF9DC8FF.toInt(), 0xFFB6E2A1.toInt(),
        0xFFFFDC9C.toInt(), 0xFFD0B7FF.toInt()
    )
    private val tags = listOf(null, "推荐", "广告", "视频", null, null, null)
    private val ratios = listOf(0.75f, 1.0f, 1.25f, 1.5f, 0.85f, 1.1f, 1.3f, 0.95f)

    suspend fun loadFeed(count: Int = 40): List<FeedNote> {
        delay(LOAD_DELAY_MS)
        return buildFeedSync(count, "n_")
    }

    /** Home 顶部分类 Tab。量很小，不必 suspend。 */
    fun loadHomeTabs(): List<String> = listOf(
        "关注", "发现", "穿搭", "美食", "户外", "美甲", "数码", "摄影", "健身", "宠物"
    )

    // ==================== Profile ====================
    data class UserProfile(
        val name: String,
        val handle: String,
        val location: String,
        val bio: String,
        val following: Int,
        val followers: Int,
        val likesAndSaves: Int,
        val notes: List<FeedNote>
    )

    suspend fun loadCurrentUser(): UserProfile {
        delay(LOAD_DELAY_MS)
        return UserProfile(
            name = "巴恩",
            handle = "@piggydance",
            location = "上海",
            bio = "Android 工程师 · 摄影爱好者 · 偶尔写写代码笔记 ✍️\n关注我，一起聊聊客户端架构与设计美学",
            following = 687,
            followers = 7822,
            likesAndSaves = 82102,
            notes = buildFeedSync(20, "p_")
        )
    }

    /** 内部同步版本，复用 FeedNote 构造逻辑。 */
    private fun buildFeedSync(count: Int, idPrefix: String): List<FeedNote> =
        (0 until count).map { i ->
            FeedNote(
                id = "$idPrefix$i",
                title = titles[i % titles.size],
                author = authors[i % authors.size],
                likes = 88 + (i * 37) % 9999,
                aspectRatio = ratios[i % ratios.size],
                coverColor = coverColors[i % coverColors.size],
                avatarColor = avatarColors[i % avatarColors.size],
                tag = tags[i % tags.size]
            )
        }

    // ==================== Messages ====================
    data class MessageEntry(
        val id: String,
        val type: Type,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val unreadCount: Int,
        val avatarColor: Int
    ) {
        enum class Type { LIKE, COMMENT, FOLLOW, SYSTEM, CHAT }
    }

    suspend fun loadMessages(): List<MessageEntry> {
        delay(LOAD_DELAY_MS)
        return listOf(
            MessageEntry("m_top_1", MessageEntry.Type.LIKE, "赞和收藏", "Lina Vonti 等 12 人赞了你的笔记", "1 分钟前", 12, 0xFFFFB3C1.toInt()),
            MessageEntry("m_top_2", MessageEntry.Type.COMMENT, "新评论", "33nevermind：拍得真好看！", "5 分钟前", 3, 0xFF9DC8FF.toInt()),
            MessageEntry("m_top_3", MessageEntry.Type.FOLLOW, "新增关注", "FoodieCat 等 4 人关注了你", "1 小时前", 4, 0xFFB6E2A1.toInt()),
            MessageEntry("m_top_4", MessageEntry.Type.SYSTEM, "系统通知", "你的笔记《今日穿搭》已通过审核", "2 小时前", 1, 0xFFFFDC9C.toInt()),
            MessageEntry("c_1", MessageEntry.Type.CHAT, "Savannah Vinson", "在的话方便发我那张原图吗～", "刚刚", 2, 0xFFFFB3C1.toInt()),
            MessageEntry("c_2", MessageEntry.Type.CHAT, "mwdii", "好嘞，明天见！", "10:42", 0, 0xFF9DC8FF.toInt()),
            MessageEntry("c_3", MessageEntry.Type.CHAT, "Summer Flowers", "[图片]", "昨天", 0, 0xFFB6E2A1.toInt()),
            MessageEntry("c_4", MessageEntry.Type.CHAT, "Lina Vonti", "Thanks for sharing! 收藏了 ❤️", "昨天", 1, 0xFFD0B7FF.toInt()),
            MessageEntry("c_5", MessageEntry.Type.CHAT, "DesignDaily", "你笔记里的字体是什么呀", "周三", 0, 0xFFFFDC9C.toInt()),
            MessageEntry("c_6", MessageEntry.Type.CHAT, "GreenLeaf", "约个咖啡？", "周二", 0, 0xFFFFCAD4.toInt()),
            MessageEntry("c_7", MessageEntry.Type.CHAT, "翼德", "群里有个新玩法你看看", "上周", 0, 0xFFB3D9FF.toInt()),
            MessageEntry("c_8", MessageEntry.Type.CHAT, "小鹿", "[语音通话]", "上周", 0, 0xFFFFE0B2.toInt()),
            MessageEntry("c_9", MessageEntry.Type.CHAT, "阿兰", "周末一起去爬山吗", "10/12", 0, 0xFFC8E6C9.toInt()),
            MessageEntry("c_10", MessageEntry.Type.CHAT, "Travel.Lin", "京都那篇笔记我翻译成日文了", "10/05", 0, 0xFFE1BEE7.toInt())
        )
    }

    /** Mock 加载耗时，模拟一次网络/DB 读取。 */
    private const val LOAD_DELAY_MS = 200L
}
