package dev.zerokotlin.system.entity

/**
 * 公告生命周期状态，枚举名用于数据库存储和管理列表。
 *
 * - [DRAFT] 表示未发布的草稿，可以修改内容或发布。
 * - [PUBLISHED] 表示已发布，在用户收件箱可见，可以撤回。
 * - [REVOKED] 表示已撤回，从收件箱隐藏并保留发布时间。
 */
enum class AnnouncementStatus { DRAFT, PUBLISHED, REVOKED }
