package com.jaycode.rongpet.module.feeding.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.feeding.dto.FeedingDisputeCreateRequest;
import com.jaycode.rongpet.module.feeding.dto.FeedingDisputeVO;
import com.jaycode.rongpet.module.feeding.entity.FeedingDispute;
import com.jaycode.rongpet.module.feeding.entity.FeedingRequest;
import com.jaycode.rongpet.module.feeding.error.FeedingErrorCode;
import com.jaycode.rongpet.module.feeding.mapper.FeedingDisputeMapper;
import com.jaycode.rongpet.module.feeding.mapper.FeedingRequestMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 喂养纠纷服务（PRD FR-7.5）。
 * 信息模式不涉及资金，纠纷以双方凭证留痕、管理员查看沟通材料并填写处理说明归档为主。
 */
@Service
@RequiredArgsConstructor
public class FeedingDisputeService {

    private final FeedingDisputeMapper disputeMapper;
    private final FeedingRequestMapper feedingMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    /** 小程序：请求双方提交纠纷凭证 */
    @Transactional
    public FeedingDisputeVO submit(long userId, FeedingDisputeCreateRequest request) {
        FeedingRequest feeding = feedingMapper.selectById(request.getFeedingId());
        if (feeding == null) {
            throw new BizException(FeedingErrorCode.NOT_FOUND);
        }
        boolean owner = feeding.getUserId() != null && feeding.getUserId() == userId;
        boolean feeder = feeding.getFeederId() != null && feeding.getFeederId() == userId;
        if (!owner && !feeder) {
            throw new BizException(FeedingErrorCode.ONLY_PARTICIPANT);
        }
        // 已完成或已关闭的请求才允许发起纠纷，进行中请先联系/关闭
        if ("PENDING".equals(feeding.getStatus()) || "CONTACTED".equals(feeding.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "请求尚未结束，请先完成或关闭后再提交纠纷");
        }

        FeedingDispute dispute = new FeedingDispute();
        dispute.setFeedingId(feeding.getId());
        dispute.setSubmitterId(userId);
        dispute.setSide(owner ? "USER" : "FEEDER");
        dispute.setReason(request.getReason().trim());
        dispute.setDescription(request.getDescription().trim());
        dispute.setEvidenceImages(request.getEvidenceImages() == null || request.getEvidenceImages().isEmpty()
                ? null : JSONUtil.toJsonStr(request.getEvidenceImages()));
        dispute.setStatus("PENDING");
        disputeMapper.insert(dispute);

        // 通知对方：有新的纠纷凭证待处理
        Long otherId = owner ? feeding.getFeederId() : feeding.getUserId();
        if (otherId != null) {
            messageService.send(otherId, MessageService.TYPE_SYSTEM, "收到喂养纠纷材料",
                    "喂养请求 " + feeding.getId() + " 有新的纠纷凭证提交，平台将在核实后给出处理说明",
                    "FEEDING", String.valueOf(feeding.getId()));
        }
        return toVO(dispute);
    }

    /** 小程序：双方查看同一请求下的全部纠纷记录 */
    public List<FeedingDisputeVO> listByFeeding(long viewerId, long feedingId) {
        FeedingRequest feeding = feedingMapper.selectById(feedingId);
        if (feeding == null) {
            throw new BizException(FeedingErrorCode.NOT_FOUND);
        }
        boolean owner = feeding.getUserId() != null && feeding.getUserId() == viewerId;
        boolean feeder = feeding.getFeederId() != null && feeding.getFeederId() == viewerId;
        if (!owner && !feeder) {
            throw new BizException(FeedingErrorCode.ONLY_PARTICIPANT);
        }
        return disputeMapper.selectList(new LambdaQueryWrapper<FeedingDispute>()
                        .eq(FeedingDispute::getFeedingId, feedingId)
                        .orderByAsc(FeedingDispute::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    /** 后台：纠纷队列分页，支持按状态与喂养请求编号过滤 */
    public PageResult<FeedingDisputeVO> adminPage(String status, Long feedingId, int pageNum, int pageSize) {
        Page<FeedingDispute> page = disputeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FeedingDispute>()
                        .eq(StringUtils.hasText(status), FeedingDispute::getStatus, status)
                        .eq(feedingId != null, FeedingDispute::getFeedingId, feedingId)
                        .orderByAsc(FeedingDispute::getStatus)
                        .orderByDesc(FeedingDispute::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 后台：填写处理说明并归档，处理结论通知双方 */
    @Transactional
    public void archive(long adminId, long disputeId, String remark) {
        FeedingDispute dispute = disputeMapper.selectById(disputeId);
        if (dispute == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "纠纷记录不存在");
        }
        if (!StringUtils.hasText(remark)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写处理说明");
        }
        FeedingDispute update = new FeedingDispute();
        update.setId(disputeId);
        update.setStatus("ARCHIVED");
        update.setHandleRemark(remark.trim());
        update.setHandleAdminId(adminId);
        update.setHandleTime(LocalDateTime.now());
        disputeMapper.updateById(update);

        FeedingRequest feeding = feedingMapper.selectById(dispute.getFeedingId());
        String content = "喂养请求 " + dispute.getFeedingId() + " 的纠纷已处理：" + remark.trim();
        messageService.send(dispute.getSubmitterId(), MessageService.TYPE_SYSTEM, "纠纷处理结果",
                content, "FEEDING", String.valueOf(dispute.getFeedingId()));
        if (feeding != null) {
            Long otherId = feeding.getUserId() != null && feeding.getUserId().equals(dispute.getSubmitterId())
                    ? feeding.getFeederId() : feeding.getUserId();
            if (otherId != null && !otherId.equals(dispute.getSubmitterId())) {
                messageService.send(otherId, MessageService.TYPE_SYSTEM, "纠纷处理结果",
                        content, "FEEDING", String.valueOf(dispute.getFeedingId()));
            }
        }
    }

    private FeedingDisputeVO toVO(FeedingDispute dispute) {
        FeedingDisputeVO vo = new FeedingDisputeVO();
        vo.setId(dispute.getId());
        vo.setFeedingId(dispute.getFeedingId());
        vo.setSubmitterId(dispute.getSubmitterId());
        User submitter = userMapper.selectById(dispute.getSubmitterId());
        vo.setSubmitterNickname(submitter == null || !StringUtils.hasText(submitter.getNickname())
                ? "宠友" : submitter.getNickname());
        vo.setSide(dispute.getSide());
        vo.setReason(dispute.getReason());
        vo.setDescription(dispute.getDescription());
        vo.setEvidenceImages(StringUtils.hasText(dispute.getEvidenceImages())
                ? JSONUtil.toList(dispute.getEvidenceImages(), String.class) : List.of());
        vo.setStatus(dispute.getStatus());
        vo.setHandleRemark(dispute.getHandleRemark());
        vo.setHandleTime(dispute.getHandleTime());
        vo.setCreateTime(dispute.getCreateTime());
        return vo;
    }
}
