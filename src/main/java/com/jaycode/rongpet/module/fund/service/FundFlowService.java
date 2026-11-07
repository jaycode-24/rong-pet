package com.jaycode.rongpet.module.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.module.fund.entity.FundFlow;
import com.jaycode.rongpet.module.fund.mapper.FundFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 资金流水服务。所有资金动作必须落流水，便于对账与排查。
 */
@Service
@RequiredArgsConstructor
public class FundFlowService {

    private final FundFlowMapper fundFlowMapper;

    public FundFlow record(Long userId, String bizType, String bizNo, String direction, long amount,
                           String status, String voucherNo, String operator, String remark) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("F" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        flow.setUserId(userId);
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setStatus(status);
        flow.setVoucherNo(voucherNo);
        flow.setOperator(operator);
        flow.setRemark(remark);
        if ("SUCCESS".equals(status)) {
            flow.setFinishTime(LocalDateTime.now());
        }
        fundFlowMapper.insert(flow);
        return flow;
    }

    public PageResult<FundFlow> page(Long userId, String bizType, String direction, int pageNum, int pageSize) {
        Page<FundFlow> page = fundFlowMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FundFlow>()
                        .eq(userId != null, FundFlow::getUserId, userId)
                        .eq(bizType != null && !bizType.isBlank(), FundFlow::getBizType, bizType)
                        .eq(direction != null && !direction.isBlank(), FundFlow::getDirection, direction)
                        .orderByDesc(FundFlow::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** 对账导出：按时间区间与业务类型查询，最多导出 10000 条防止超大结果集 */
    public List<FundFlow> exportRange(LocalDateTime from, LocalDateTime to, String bizType) {
        return fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .ge(from != null, FundFlow::getCreateTime, from)
                .lt(to != null, FundFlow::getCreateTime, to)
                .eq(bizType != null && !bizType.isBlank(), FundFlow::getBizType, bizType)
                .orderByDesc(FundFlow::getCreateTime)
                .last("limit 10000"));
    }
}
