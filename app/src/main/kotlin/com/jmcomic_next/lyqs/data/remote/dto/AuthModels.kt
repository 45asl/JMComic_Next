package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 会员信息。
 *
 * `login` 响应的 `data` 就是这个结构再加上 `jwttoken`，因此登录与「读取本地会员信息」
 * 共用同一个模型 —— 少一层映射，也避免两处字段不同步。
 *
 * 字段取自渲染层的实际访问点（`Desc.tsx` / `CenterCard.tsx` / `MemberModal.tsx`）。
 */
@Serializable
data class MemberInfo(
    @Serializable(with = FlexStringOrNull::class) val uid: String? = null,
    val username: String? = null,
    val email: String? = null,
    @Serializable(with = FlexInt::class) val level: Int = 0,
    @SerialName("level_name") val levelName: String? = null,
    /** 金币余额。服务端可能回字符串或数字，统一按字符串展示。 */
    @Serializable(with = FlexStringOrNull::class) val coin: String? = null,
    /** 是否免广告会员。注意：本应用本来就没有广告，此字段只用于展示会员状态。 */
    @SerialName("ad_free")
    @Serializable(with = FlexBool::class) val adFree: Boolean = false,
    @Serializable(with = FlexStringOrNull::class) val charge: String? = null,
    @SerialName("invited_cnt")
    @Serializable(with = FlexInt::class) val invitedCount: Int = 0,
    @SerialName("invitation_url") val invitationUrl: String? = null,
    @SerialName("invitation_qrcode") val invitationQrcode: String? = null,
    /** 仅登录响应里存在。 */
    @SerialName("jwttoken") val jwtToken: String? = null,
) {
    /** 用于界面展示的称呼，缺失时回退到 uid。 */
    val displayName: String get() = username?.takeIf { it.isNotBlank() } ?: (uid ?: "未命名")
}

/**
 * 通用动作结果。
 *
 * `favorite`（收藏切换）与 `favorite_folder`（收藏夹编辑）都返回这个结构。
 * 收藏切换的语义很巧：**同一个 POST 既是收藏也是取消**，由响应里的 [type] 告知实际发生了什么
 * （`add` 新收藏 / `remove` 已取消 / `move`·`edit` 移动了分组），
 * 因此客户端不必自己维护「当前是否已收藏」的状态再去决定调用哪个接口。
 */
@Serializable
data class ActionResult(
    /** `"ok"` 表示业务成功。注意它和封套的 `code == 200` 是两层判断，两者都要看。 */
    val status: String? = null,
    /** 实际发生的动作：add / remove / move / edit。 */
    val type: String? = null,
    val msg: String? = null,
    @Serializable(with = FlexInt::class) val code: Int = 0,
) {
    val isOk: Boolean get() = status == "ok"
}

/**
 * 收藏夹。
 *
 * 字段名是 **`FID`** 而不是 `id` —— 依据 `MarkList.tsx` 的
 * `favoriteList.folder_list.map((d) => ... d.FID ... d.name)`。
 * 直接用 `id` 会静默拿到空串，表现为「收藏夹列表全是无名项」。
 */
@Serializable
data class FavoriteFolder(
    @SerialName("FID")
    @Serializable(with = FlexString::class) val folderId: String = "",
    val name: String? = null,
)

/**
 * 收藏列表（`favorite` 接口 GET）。
 *
 * `list` 与历史、搜索等共用同一个 [ListItem] —— 依据是它们都交给同一个 `ComicList` 渲染。
 * 每页 20 条（`MarkList.tsx` 的 `Math.ceil(favoriteList.total / 20)`）。
 */
@Serializable
data class FavoriteListPayload(
    val list: List<ListItem> = emptyList(),
    @SerialName("folder_list") val folderList: List<FavoriteFolder> = emptyList(),
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    @Serializable(with = FlexStringOrNull::class) val count: String? = null,
) {
    val totalCount: Int get() = total?.toIntOrNull() ?: list.size
}

/** 观看历史（`watch_list` 接口 GET）。 */
@Serializable
data class HistoryPayload(
    val list: List<ListItem> = emptyList(),
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
) {
    val totalCount: Int get() = total?.toIntOrNull() ?: list.size
}
