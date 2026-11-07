package com.jaycode.rongpet.module.post.constant;

/**
 * 帖子状态机（附录 B）。所有流转由服务层控制，前端不得直接改写。
 */
public final class PostStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";
    /** 待审核 */
    public static final String PENDING_AUDIT = "PENDING_AUDIT";
    /** 已驳回 */
    public static final String REJECTED = "REJECTED";
    /** 展示中（无悬赏） */
    public static final String SHOWING = "SHOWING";
    /** 悬赏托管中 */
    public static final String REWARD_FROZEN = "REWARD_FROZEN";
    /** 待评分 */
    public static final String WAIT_SCORE = "WAIT_SCORE";
    /** 评分分账中 */
    public static final String SPLITTING = "SPLITTING";
    /** 已完成 */
    public static final String COMPLETED = "COMPLETED";
    /** 已过期 */
    public static final String EXPIRED = "EXPIRED";
    /** 已下架 */
    public static final String TAKEN_DOWN = "TAKEN_DOWN";

    private PostStatus() {
    }

    /** 对外列表可见状态 */
    public static boolean visible(String status) {
        return SHOWING.equals(status) || REWARD_FROZEN.equals(status);
    }
}
