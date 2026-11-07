package com.jaycode.rongpet.module.experience;

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
