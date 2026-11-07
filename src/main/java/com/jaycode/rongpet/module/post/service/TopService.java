package com.jaycode.rongpet.module.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.TopType;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.entity.TopRecord;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.post.mapper.TopRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 置顶服务：付费/免费（截图人工核验）/人工赋能，共用前 3 个展示位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopService {

    private static final int MAX_TOP_SLOTS = 3;

    private final TopRecordMapper topRecordMapper;
    private final PostMapper postMapper;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;

    // ------------------------------------------------------------------ 付费置顶

    public long paidPrice(Post post) {
        return sysConfigService.getLong("top.price." + post.getType() + ".cents", 3000);
    }

    public int paidDays(Post post) {
        return sysConfigService.getInt("top.days." + post.getType(), 3);
    }

    /** 下单前校验是否可置顶，避免无展示位时仍发起支付 */
    public void checkCanTop(long postId) {
        ensureCanTop(requirePost(postId));
    }

    /** 支付成功后生效（由 PayService 调用） */
    @Transactional
    public void activatePaid(long postId, int days, String orderNo) {
        Post post = requirePost(postId);
        ensureCanTop(post);
        TopRecord record = new TopRecord();
        record.setPostId(postId);
        record.setType(TopType.PAID);
        record.setDays(days);
        record.setOrderNo(orderNo);
        record.setStatus("ACTIVE");
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now().plusDays(days));
        topRecordMapper.insert(record);
        applyTop(postId, TopType.PAID, record.getEndTime());
        messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "置顶已生效",
                "帖子《" + post.getTitle() + "》已置顶 " + days + " 天", "POST", String.valueOf(postId));
    }

    /** 退款时撤销置顶 */
    @Transactional
    public void deactivateByOrder(String orderNo) {
        List<TopRecord> records = topRecordMapper.selectList(new LambdaQueryWrapper<TopRecord>()
                .eq(TopRecord::getOrderNo, orderNo));
        for (TopRecord record : records) {
            TopRecord update = new TopRecord();
            update.setId(record.getId());
            update.setStatus("EXPIRED");
            topRecordMapper.updateById(update);
            clearTopIfMatches(record.getPostId());
        }
    }

    // ------------------------------------------------------------------ 免费置顶

    @Transactional
    public void applyFree(long userId, long postId, String screenshotUrl) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        ensureCanTop(post);
        long existing = topRecordMapper.selectCount(new LambdaQueryWrapper<TopRecord>()
                .eq(TopRecord::getPostId, postId)
                .eq(TopRecord::getType, TopType.FREE));
        if (existing > 0) {
            throw new BizException(PostErrorCode.TOP_REJECTED, "每个帖子仅可申请一次免费置顶");
        }
        long monthly = topRecordMapper.selectCount(new LambdaQueryWrapper<TopRecord>()
                .eq(TopRecord::getCreateBy, userId)
                .eq(TopRecord::getType, TopType.FREE)
                .ge(TopRecord::getCreateTime, LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        if (monthly >= sysConfigService.getLong("top.free.max.per.month", 2)) {
            throw new BizException(PostErrorCode.TOP_REJECTED, "本月免费置顶申请次数已达上限");
        }
        TopRecord record = new TopRecord();
        record.setPostId(postId);
        record.setType(TopType.FREE);
        record.setDays(sysConfigService.getInt("top.days." + post.getType(), 3));
        record.setScreenshotUrl(screenshotUrl);
        record.setStatus("PENDING");
        topRecordMapper.insert(record);
        messageService.send(userId, MessageService.TYPE_SYSTEM, "免费置顶已提交",
                "截图核验预计12小时内完成，请耐心等待", "POST", String.valueOf(postId));
    }

    @Transactional
    public void approveFree(long adminId, long recordId) {
        TopRecord record = requireRecord(recordId);
        if (!TopType.FREE.equals(record.getType()) || !"PENDING".equals(record.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "该申请已处理");
        }
        Post post = requirePost(record.getPostId());
        ensureCanTop(post);
        LocalDateTime end = LocalDateTime.now().plusDays(record.getDays());
        TopRecord update = new TopRecord();
        update.setId(recordId);
        update.setStatus("ACTIVE");
        update.setStartTime(LocalDateTime.now());
        update.setEndTime(end);
        topRecordMapper.updateById(update);
        applyTop(record.getPostId(), TopType.FREE, end);
        messageService.send(post.getUserId(), MessageService.TYPE_SYSTEM, "免费置顶已生效",
                "帖子《" + post.getTitle() + "》已置顶 " + record.getDays() + " 天", "POST", String.valueOf(post.getId()));
    }

    @Transactional
    public void rejectFree(long adminId, long recordId, String reason) {
        TopRecord record = requireRecord(recordId);
        TopRecord update = new TopRecord();
        update.setId(recordId);
        update.setStatus("REJECTED");
        update.setReason(reason);
        topRecordMapper.updateById(update);
        Post post = requirePost(record.getPostId());
        messageService.send(post.getUserId(), MessageService.TYPE_SYSTEM, "免费置顶未通过",
                "原因：" + reason, "POST", String.valueOf(post.getId()));
    }

    // ------------------------------------------------------------------ 人工赋能

    @Transactional
    public void manualTop(long adminId, long postId, int days, String reason) {
        if (days < 1 || days > 3) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "人工置顶最长为3天");
        }
        Post post = requirePost(postId);
        ensureCanTop(post);
        LocalDateTime end = LocalDateTime.now().plusDays(days);
        TopRecord record = new TopRecord();
        record.setPostId(postId);
        record.setType(TopType.MANUAL);
        record.setDays(days);
        record.setReason(reason);
        record.setStatus("ACTIVE");
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(end);
        topRecordMapper.insert(record);
        applyTop(postId, TopType.MANUAL, end);
        messageService.send(post.getUserId(), MessageService.TYPE_SYSTEM, "帖子获得运营置顶",
                "帖子《" + post.getTitle() + "》获得 " + days + " 天运营置顶", "POST", String.valueOf(postId));
    }

    // ------------------------------------------------------------------ 定时任务

    public PageResult<TopRecord> adminPage(String type, String status, int pageNum, int pageSize) {
        Page<TopRecord> page = topRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<TopRecord>()
                        .eq(type != null && !type.isBlank(), TopRecord::getType, type)
                        .eq(status != null && !status.isBlank(), TopRecord::getStatus, status)
                        .orderByAsc(TopRecord::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public int expireTops() {
        List<Post> expired = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .isNotNull(Post::getTopExpireTime)
                .lt(Post::getTopExpireTime, LocalDateTime.now())
                .last("limit 200"));
        for (Post post : expired) {
            Post update = new Post();
            update.setId(post.getId());
            update.setTopType(null);
            update.setTopExpireTime(null);
            postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Post>()
                    .set("top_type", null)
                    .set("top_expire_time", null)
                    .eq("id", post.getId()));
        }
        topRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TopRecord>()
                .set("status", "EXPIRED")
                .eq("status", "ACTIVE")
                .lt("end_time", LocalDateTime.now()));
        return expired.size();
    }

    // ------------------------------------------------------------------ 内部

    private void ensureCanTop(Post post) {
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.TOP_REJECTED);
        }
        if (post.getTopExpireTime() != null && post.getTopExpireTime().isAfter(LocalDateTime.now())) {
            throw new BizException(PostErrorCode.TOP_DUPLICATE);
        }
        long active = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .gt(Post::getTopExpireTime, LocalDateTime.now()));
        if (active >= MAX_TOP_SLOTS) {
            throw new BizException(PostErrorCode.TOP_SLOT_FULL);
        }
    }

    private void applyTop(long postId, String type, LocalDateTime endTime) {
        Post update = new Post();
        update.setId(postId);
        update.setTopType(type);
        update.setTopExpireTime(endTime);
        postMapper.updateById(update);
    }

    private void clearTopIfMatches(long postId) {
        Post post = postMapper.selectById(postId);
        if (post != null && post.getTopExpireTime() != null && post.getTopExpireTime().isAfter(LocalDateTime.now())) {
            postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Post>()
                    .set("top_type", null)
                    .set("top_expire_time", null)
                    .eq("id", postId));
        }
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private TopRecord requireRecord(long recordId) {
        TopRecord record = topRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "置顶记录不存在");
        }
        return record;
    }
}
