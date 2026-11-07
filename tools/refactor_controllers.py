from pathlib import Path
b=Path('src/main/java/com/jaycode/rongpet/module/experience')
(b/'dto'/'ExperienceRequests.java').write_text('''package com.jaycode.rongpet.module.experience.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class ExperienceRequests {
    private ExperienceRequests() {}
    public record Feedback(@NotBlank @Pattern(regexp="功能建议|使用问题|内容与服务|其他意见") String category, @NotBlank @Size(min=5,max=1000) String content) {}
    public record Scene(@NotBlank String scene) {}
    public record Text(@NotBlank @Size(max=1000) String content) {}
    public record Reply(@NotBlank @Size(max=2000) String content) {}
    public record Generate(@NotBlank String style, @NotBlank @Size(max=64) String requestKey) {}
    public record Evidence(@NotBlank @Size(min=10,max=2000) String content,
            @Schema(description="证据地址数组，不接受JSON字符串或嵌套数组") @NotEmpty @Size(max=9) List<@NotBlank @Size(max=1024) String> evidence) {}
    public record Found(@NotEmpty @Size(max=5) List<@NotNull Long> clueIds, @NotBlank @Size(min=10,max=500) String reason) {}
    public record Decision(@NotBlank @Pattern(regexp="REJECTED|UPHELD|REFUND_APPROVED") String decision, @NotBlank @Size(min=5,max=2000) String reason) {}
}
''',encoding='utf-8')
# 控制器从紧凑旧代码提取，替换请求DTO并补OpenAPI，保留路由。
for filename,tag in [('AppExperienceController.java','App-体验与反馈'),('AdminFeedbackController.java','Admin-用户反馈'),('ConversationController.java','App-站内会话'),('PetAvatarController.java','App-AI宠物形象'),('RewardDisputeController.java','App/Admin-悬赏争议')]:
 p=b/filename;s=p.read_text(encoding='utf-8')
 import re
 s=re.sub(r'\s*public record \w+\([^\n]*?\)\{\}', '',s)
 s=s.replace('import com.jaycode.rongpet.common.api.R;','import com.jaycode.rongpet.common.api.R;\nimport com.jaycode.rongpet.module.experience.dto.ExperienceRequests.*;\nimport io.swagger.v3.oas.annotations.tags.Tag;\nimport io.swagger.v3.oas.annotations.Operation;')
 s=s.replace('@RestController',f'@Tag(name = "{tag}")\n@RestController',1)
 summaries={'overview':'首页私人聚合','favorite':'收藏帖子','unfavorite':'取消收藏','favorites':'我的收藏','create':'创建申请','feedbacks':'反馈列表','feedback':'本人反馈详情','room':'读取宠物场景','scene':'保存宠物场景','list':'分页列表','detail':'详情','reply':'回复用户建议','open':'创建或获取帖子会话','history':'会话历史，每页30条','send':'发送站内文字消息','quota':'AI生成额度和价格','task':'生成任务状态','pay':'付款，同一任务最多重建一次支付单','select':'选用生成结果','submit':'提交找回争议，证据使用数组','confirmFound':'人工核验找回并选定有效线索','approveRefund':'人工核验未找回退款','mine':'本人争议列表','resolve':'处理争议并通知双方'}
 for method,summary in summaries.items():
  s=re.sub(r'(@(?:Get|Post|Put|Delete)Mapping(?:\([^\n]*?\))?)(\s+public R<\?> '+method+r'\()', lambda m:'@Operation(summary = "'+summary+'")\n '+m[1]+m[2],s)
 p.write_text(s,encoding='utf-8')
# 资金接口改为Mapper查询及专用VO。
(b/'AppFundHistoryController.java').write_text('''package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.fund.entity.FundFlow;
import com.jaycode.rongpet.module.fund.mapper.FundFlowMapper;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.FundItem;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "App-本人资金记录")
@RestController
@RequestMapping("/app/v1/funds")
@RequiredArgsConstructor
public class AppFundHistoryController {
    private final FundFlowMapper funds;

    @Operation(summary = "本人资金记录，不返回运营备注")
    @GetMapping
    public R<List<FundItem>> history(@RequestParam(defaultValue="1") int page) {
        ExperienceService.validatePage(page);
        List<FundItem> items = funds.selectPage(new Page<>(page, ExperienceService.PAGE_SIZE, false),
                new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getUserId, StpUserUtil.getLoginIdAsLong())
                        .orderByDesc(FundFlow::getCreateTime).orderByDesc(FundFlow::getId)).getRecords().stream()
                .map(f -> new FundItem(f.getFlowNo(), f.getBizType(), f.getBizNo(), f.getAmount(), f.getStatus(), f.getVoucherNo(),
                        "IN".equals(f.getDirection()) ? "EXPENSE" : "INCOME", f.getCreateTime(), f.getFinishTime())).toList();
        return R.ok(items);
    }
}
''',encoding='utf-8')
p=b/'PublicExperienceController.java';s=p.read_text(encoding='utf-8').replace('import lombok.RequiredArgsConstructor;','import lombok.RequiredArgsConstructor;\nimport io.swagger.v3.oas.annotations.tags.Tag;\nimport io.swagger.v3.oas.annotations.Operation;');s=s.replace('@RestController','@Tag(name = "App-公开配置与搜索")\n@RestController',1).replace('@GetMapping("/experience-config")','@Operation(summary = "白名单公开运营配置")\n    @GetMapping("/experience-config")').replace('@GetMapping("/search")','@Operation(summary = "公开帖子与喂养员搜索")\n    @GetMapping("/search")');p.write_text(s,encoding='utf-8')
# 清除支付服务中的新增JdbcTemplate，统一交由任务Mapper。
p=Path('src/main/java/com/jaycode/rongpet/module/pay/service/PayService.java');s=p.read_text(encoding='utf-8');s=s.replace('private final org.springframework.jdbc.core.JdbcTemplate jdbc;', 'private final com.jaycode.rongpet.module.experience.mapper.PetAvatarTaskMapper avatarTasks;')
s=s.replace('jdbc.update("UPDATE t_pet_avatar_task SET status=\'QUEUED\' WHERE order_no=? AND status=\'WAIT_PAY\'",order.getOutTradeNo())', 'avatarTasks.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq("order_no", order.getOutTradeNo()).eq("status", "WAIT_PAY").set("status", "QUEUED").set("update_time", LocalDateTime.now()).setSql("version=version+1"))')
s=s.replace('jdbc.update("UPDATE t_pet_avatar_task SET status=\'REFUNDED\' WHERE order_no=? AND status=\'FAILED\'",order.getOutTradeNo())', 'avatarTasks.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq("order_no", order.getOutTradeNo()).eq("status", "FAILED").set("status", "REFUNDED").set("update_time", LocalDateTime.now()).setSql("version=version+1"))')
s=s.replace('jdbc.queryForObject("SELECT COUNT(*) FROM t_pet_avatar_task WHERE order_no=? AND status=\'FAILED\'",Long.class,order.getOutTradeNo())','avatarTasks.selectCount(new LambdaQueryWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq(com.jaycode.rongpet.module.experience.entity.PetAvatarTask::getOrderNo, order.getOutTradeNo()).eq(com.jaycode.rongpet.module.experience.entity.PetAvatarTask::getStatus, "FAILED"))')
p.write_text(s,encoding='utf-8')
