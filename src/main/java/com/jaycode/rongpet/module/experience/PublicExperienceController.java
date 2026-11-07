package com.jaycode.rongpet.module.experience;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.post.constant.ChengduDistrict;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.module.feeder.service.FeederService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Tag(name = "App-公开配置与搜索")
@RestController @RequestMapping("/app/v1/public") @RequiredArgsConstructor
public class PublicExperienceController {
    private final SysConfigService config;
    private final PostService posts;
    private final FeederService feeders;

    @Operation(summary = "白名单公开运营配置")
    @GetMapping("/experience-config")
    public R<?> settings() {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("districts", ChengduDistrict.ALL);
        out.put("faq",config.get("help.faq","[]"));
        out.put("scenes",config.get("pet.room.scenes","SUNNY,GARDEN,NIGHT"));
        // 白名单公开配置，禁止返回商户凭证、内部风控配置或任意配置键。
        for(String key:List.of("reward.min.cents","reward.max.cents","reward.service.rate","reward.service.cap.cents",
            "reward.large.review.cents","reward.refund.objection.hours","pet.avatar.free.count","pet.avatar.price.cents",
            "top.price.LOST.cents","top.price.FOUND.cents","top.price.BREED.cents","top.days.LOST","top.days.FOUND","top.days.BREED"))
            out.put(key,config.get(key,""));
        return R.ok(out);
    }

    @Operation(summary = "公开帖子与喂养员搜索")
    @GetMapping("/search")
    public R<?> search(@RequestParam String keyword,@RequestParam(required=false) String district) {
        if(keyword.isBlank()||keyword.length()>100)return R.ok(Map.of("posts",List.of(),"feeders",List.of()));
        return R.ok(Map.of("posts",posts.list(null,district,"latest",keyword.trim(),1,10),
            "feeders",feeders.list(district,keyword.trim(),1,10)));
    }
}
