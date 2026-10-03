package com.jmcomic_next.lyqs.data.remote

/**
 * 业务接口路径。
 *
 * 直接取自 `JMComic_SRC` 的 `api/apiPaths.ts`（原始键名一并保留在注释里，便于对账），
 * 完整地址 = 会话的 API 主机 + 这里的路径。
 *
 * 只收录本应用实际使用的接口；官方客户端还有大量会员/论坛/小说/影片接口未纳入。
 */
object JmPaths {
    /** 首页主推荐列表（API_COMIC_PROMOTE），`data` 为纯数组。 */
    const val PROMOTE = "promote"

    /** 首页最新列表（API_COMIC_LATEST），参数 `page`（**0 起算**）。 */
    const val LATEST = "latest"

    /**
     * 首页某个推荐分区的完整列表（API_COMIC_PROMOTE_LIST），参数 `id`、`page`（**0 起算**）。
     *
     * 首页 `promote` 的每个分区只给一小段内容（十几条），点「更多」才用这个接口把该分区铺开。
     * 实测响应 `{total:"133", list:[...]}`（`total` 是字符串），每页 30 条，
     * 总页数 = `ceil(total / 30)`；把 `page` 传成 1 起算会整段跳过第一页。
     */
    const val PROMOTE_LIST = "promote_list"

    /**
     * 连载更新表（API_COMIC_SER_MORE_LIST）。
     *
     * 参数 `type`（`all` 全部 / `manga` 漫画 / `hanman` 韩漫）、
     * `date`（**0 完结，1..7 周一..周日**）、`page`（**1 起算**）。
     *
     * 两处与别的列表接口不同：响应**没有 `total`**，且末页返回的是
     * `{"error":"没有资料"}` 而不是空列表 —— 因此「还有没有下一页」只能按
     * 「本页是否为空」判断，不能按总数推算。
     */
    const val SERIALIZATION = "serialization"

    /**
     * 通知列表（API_NOTIFICATIONS）：`GET {type, subType, page}` → `{code, data:{list,total}}`。
     * `type` 取 `all` / `comic_follow`（追更）/ `site_notice`（站内通知）。
     */
    const val NOTIFICATIONS = "notifications"

    /** 未读数量（API_NOTIFICATIONS_UNREAD）：`GET`，无参数。 */
    const val NOTIFICATIONS_UNREAD = "notifications/unreadCount"

    /**
     * 每日签到（API_DAILY）：查询当前签到活动，返回 `daily_id`、活动名与按周分组的日历。
     * 需要登录，参数 `user_id`。
     */
    const val DAILY = "daily"

    /**
     * 每日签到打卡（API_DAILY_CHECK）：**POST**，参数 `user_id` + `daily_id`。
     * 重复打卡不算错误 —— 响应 `msg` 里会写「已經簽到過了」，见 [com.jmcomic_next.lyqs.data.Daily]。
     */
    const val DAILY_CHECK = "daily_chk"

    /** 搜索（API_COMIC_SEARCH），参数 `search_query`、`page`、`o`、`search_type`、`y`、`m`。 */
    const val SEARCH = "search"

    /** 热门标签（API_COMIC_HOT_TAGS）。 */
    const val HOT_TAGS = "hot_tags"

    /** 漫画详情（API_COMIC_DETAIL），参数 `id`。 */
    const val ALBUM = "album"

    /** 漫画阅读内容（API_COMIC_READ），参数 `id`、可选 `express`。 */
    const val COMIC_READ = "comic_read"

    /** 分类列表（API_CATEGORIES_LIST）。 */
    const val CATEGORIES = "categories"

    /** 分类筛选（API_CATEGORIES_FILTER_LIST）。 */
    const val CATEGORIES_FILTER = "categories/filter"

    /** 应用配置（API_APP_SETTING），参数 `app_img_shunt`、`lang`、`t`。 */
    const val SETTING = "setting"

    // ---- 账号（API_MEMBER_*）----

    /** 登录：POST `{username, password}`，响应 `data.jwttoken` + 会员信息。 */
    const val LOGIN = "login"

    /** 注册：POST `{username, password, password_confirm, email, gender}`。 */
    const val REGISTER = "register"

    /** 忘记密码：POST `{email}`。 */
    const val FORGOT = "forgot"

    /** 登出：POST 无参。 */
    const val LOGOUT = "logout"

    // ---- 收藏与历史 ----

    /**
     * 收藏（API_FAVORITE_LIST）。
     * GET 取列表（`page` / `folder_id` / `o`）；**POST 是切换**（`aid`），
     * 增还是删由响应里的 `type` 告知。
     */
    const val FAVORITE = "favorite"

    /**
     * 收藏夹编辑（API_FAVORITE_FOLDER）：POST `{type, folder_id, folder_name, aid}`。
     * `type` 取值：`add` 新建 / `edit` 改名 / `move` 归类 / `del` 删除。
     */
    const val FAVORITE_FOLDER = "favorite_folder"

    /** 点赞（API_LIKE_DATA）：POST `{id, like_type?}`。 */
    const val LIKE = "like"

    /** 观看历史（API_HISTORY_LIST）：GET `{page}` 取列表，POST `{id}` **删除**一条历史。 */
    const val WATCH_LIST = "watch_list"

    /**
     * 评论 / 论坛（API_FORUM_LIST）：GET。
     * 详情页的评论用 `{mode: "all", page, aid}`，响应 `data` 为 `{total, list}`。
     */
    const val FORUM = "forum"

    // ---- 期刊（周刊）与随机推荐 ----

    /**
     * 期刊列表（API_WEEK）。
     *
     * 响应 `{categories:[{id,title,time}], type:[{id,name}]}` ——
     * `categories` 是**刊期**（`time` 形如「2026第258期09.25 - 09.18」，而 `title` 实测为空串），
     * `type` 是作品类型（`manga` 日漫 / `another` 其他 / `hanman` 韩漫）。
     * 两者都得**先取列表、再用列表里的 id 去筛**，不能自己编。
     */
    const val WEEK = "week"

    /** 某期刊某类型的作品（API_WEEK__FILTER_LIST），参数 `id`（刊期）、`type`、`page`（1 起算）。 */
    const val WEEK_FILTER = "week/filter"

    /** 随机推荐（API_COMIC_RANDOM_RECOMMEND），无参数，`data` 为**裸数组**。 */
    const val RANDOM_RECOMMEND_LIST = "random_recommend"

    // ---- 创作者库（API_CREATOR_*）：画师与其作品的浏览入口 ----

    /** 画师列表（API_CREATOR_AUTHOR），参数 `page`、`search_query`。 */
    const val CREATOR_AUTHOR = "creator_author"

    /** 作品列表（API_CREATOR_WORK），参数 `page`、`search_value`、`lang`、`source`。 */
    const val CREATOR_WORK = "creator_work"

    /** 某画师名下的作品（API_CREATOR_WORK_DETAIL），参数 `id`、`lang`、`source`。 */
    const val CREATOR_WORK_DETAIL = "creator_work_detail"

    /** 作品信息（API_CREATOR_WORK_INFO），参数 `id`：作者、日期与一组相关作品。 */
    const val CREATOR_WORK_INFO = "creator_work_info"

    /** 作品内容（API_CREATOR_WORK_INFO_DETAIL），参数 `id`：`images` 与正文，形态接近阅读数据。 */
    const val CREATOR_WORK_INFO_DETAIL = "creator_work_info_detail"

    /** 画师头像路径模板：`/media/library/artists/<id>/icon/<file>`。 */
    const val ARTIST_ICON_TEMPLATE = "media/library/artists/%s/icon/%s"

    /** 画师横幅路径模板：`/media/library/artists/<id>/banner/<file>`。 */
    const val ARTIST_BANNER_TEMPLATE = "media/library/artists/%s/banner/%s"

    // ---- 需要登录的漫画侧功能 ----

    /**
     * 追更（API_NOTIFICATIONS_SERTRACK）：GET `{id}` 查状态，POST `{id}` **切换**。
     *
     * 与「收藏」同一套路：POST 既是关注也是取关，响应里带一句结果文案。
     */
    const val SERTRACKING = "album_sertracking"

    /** 追更列表（API_NOTIFICATIONS_TRACK_LIST）：**POST** `{page}`，注意是 POST 不是 GET。 */
    const val TRACKING_LIST = "album_tracking"

    /** 收藏的标签（API_TAGS_FAVORITE）：GET 取列表，上限 50。 */
    const val TAGS_FAVORITE = "tags_favorite"

    /** 收藏标签的增删（API_TAGS_FAVORITE_UPDATE）：POST `{type: add|remove, tags}`（逗号分隔）。 */
    const val TAGS_FAVORITE_UPDATE = "tags_favorite_update"

    /**
     * 整部作品的下载（API_ALBUM_DOWNLOAD = `album_download_2`）。
     *
     * 用法是 `album_download_2/<作品id>`，**需要登录**，而且失败**不是 401**：
     * 实测未登录时是 HTTP 200 + `{"status":"0","msg":"請先登入"}`，
     * 所以判断得落在业务字段上。成功时给的是一个 `download_url`。
     */
    const val ALBUM_DOWNLOAD = "album_download_2"

    /** 发表评论（API_COMMENT_SEND）：POST `{comment, aid, bid?, comment_id?}`。 */
    const val COMMENT_SEND = "comment"

    /** 删除自己发的评论（API_COMMENT_DETELE）：POST `{comment_id, aid?}`。 */
    const val COMMENT_DELETE = "comment_delete"

    /**
     * 封面图路径模板。
     *
     * 依据 `ComicList.tsx`：`${img_host}/media/albums/${item.id}_3x4.jpg?v=${item.update_at}`。
     * 后缀 `_3x4` 表示 3:4 竖版裁切，另有其它比例由服务端按需生成。
     */
    const val COVER_TEMPLATE = "media/albums/%s_3x4.jpg"

    /** 头像路径模板，依据 `RelatedListCarousel.tsx`。 */
    const val AVATAR_TEMPLATE = "media/users/%s"
}
