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

    /** 随机推荐（API_COMIC_RANDOM_RECOMMEND）。 */
    const val RANDOM_RECOMMEND = "random_recommend"

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
