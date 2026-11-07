package com.jaycode.rongpet.module.pay.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台支付单管理：查询与退款（资金操作仅超级管理员）。
 */
@Tag(name = "Admin-支付单")
@RestController
@RequestMapping("/admin/v1/pays")
@RequiredArgsConstructor
public class AdminPayController {

    private final PayService payService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "支付单分页")
    @GetMapping
    public R<PageResult<PayOrder>> page(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String bizType,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(payService.adminPage(status, bizType, userId, pageNum, pageSize));
    }

    @Operation(summary = "支付单详情")
    @GetMapping("/{orderId}")
    public R<PayOrder> detail(@PathVariable long orderId) {
        return R.ok(payService.findById(orderId));
    }

    @Operation(summary = "原路退款", description = "仅超级管理员；退款后同步冲减业务状态")
    @PostMapping("/{orderId}/refund")
    public R<Void> refund(@PathVariable long orderId,
                          @RequestBody(required = false) AdminActionRequest request) {
        StpAdminUtil.checkRole("SUPER");
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写退款原因");
        }
        PayOrder order = payService.findById(orderId);
        payService.refund(order, reason, "ADMIN-" + StpAdminUtil.getLoginIdAsLong());
        adminAuditService.record("PAY_REFUND", "PAY_ORDER", orderId,
                "outTradeNo=" + order.getOutTradeNo() + ", reason=" + reason);
        return R.ok();
    }

    @Operation(summary = "退款异常队列", description = "退款失败待重试或连续3次失败转人工的支付单")
    @GetMapping("/refund-failures")
    public R<PageResult<PayOrder>> refundFailures(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(payService.refundFailedPage(pageNum, pageSize));
    }

    @Operation(summary = "人工重试退款", description = "仅超级管理员；重试成功后清零失败计数并冲减业务")
    @PostMapping("/{orderId}/retry-refund")
    public R<Void> retryRefund(@PathVariable long orderId) {
        StpAdminUtil.checkRole("SUPER");
        PayOrder order = payService.findById(orderId);
        payService.retryRefund(orderId, "ADMIN-" + StpAdminUtil.getLoginIdAsLong());
        adminAuditService.record("PAY_RETRY_REFUND", "PAY_ORDER", orderId,
                "outTradeNo=" + order.getOutTradeNo() + ", retryCount=" + order.getRefundRetryCount());
        return R.ok();
    }
}
