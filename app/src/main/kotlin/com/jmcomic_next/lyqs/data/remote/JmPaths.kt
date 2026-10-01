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

    /** 首页最新列表（API_COMIC_LATEST），参数 `page`。 */
    const val LATEST = "latest"

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
