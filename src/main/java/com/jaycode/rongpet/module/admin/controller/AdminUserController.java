package com.jaycode.rongpet.module.admin.controller;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.dto.UserAdminVO;
import com.jaycode.rongpet.module.admin.dto.UserRiskVO;
import com.jaycode.rongpet.module.admin.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台用户管理：列表、详情、处置与风控专题。
 */
@Tag(name = "Admin-用户")
@RestController
@RequestMapping("/admin/v1/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户分页", description = "支持按手机号/昵称/ID 搜索")
    @GetMapping
    public R<PageResult<UserAdminVO>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(adminUserService.page(status, keyword, pageNum, pageSize));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{userId}")
    public R<UserAdminVO> detail(@PathVariable long userId) {
        return R.ok(adminUserService.detail(userId));
    }

    @Operation(summary = "用户处置", description = "action: LIMIT_POST/LIMIT_TRADE/BAN/FROZEN/UNBAN")
    @PostMapping("/{userId}/action")
    public R<Void> action(@PathVariable long userId,
                          @RequestBody AdminActionRequest request) {
        adminUserService.action(userId, request.getAction(), request.getReason());
        return R.ok();
    }

    @Operation(summary = "风控专题", description = "同手机号/同证件多账号、单日批量发帖、多笔在途悬赏")
    @GetMapping("/risks")
    public R<List<UserRiskVO>> risks() {
        return R.ok(adminUserService.risks());
    }
}
