package com.jaycode.rongpet.module.pet.controller;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.pet.dto.PetRequest;
import com.jaycode.rongpet.module.pet.dto.PetVO;
import com.jaycode.rongpet.module.pet.service.PetService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端宠物档案接口。
 */
@Tag(name = "App-宠物档案")
@RestController
@RequestMapping("/app/v1/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @Operation(summary = "我的宠物列表")
    @GetMapping
    public R<List<PetVO>> list() {
        return R.ok(petService.list(StpUserUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "新增宠物档案")
    @PostMapping
    public R<PetVO> create(@Valid @RequestBody PetRequest request) {
        return R.ok(petService.create(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "宠物档案详情")
    @GetMapping("/{petId}")
    public R<PetVO> detail(@PathVariable long petId) {
        return R.ok(petService.detail(StpUserUtil.getLoginIdAsLong(), petId));
    }

    @Operation(summary = "编辑宠物档案")
    @PutMapping("/{petId}")
    public R<PetVO> update(@PathVariable long petId, @Valid @RequestBody PetRequest request) {
        return R.ok(petService.update(StpUserUtil.getLoginIdAsLong(), petId, request));
    }

    @Operation(summary = "删除宠物档案", description = "删除不影响历史帖子快照")
    @DeleteMapping("/{petId}")
    public R<Void> delete(@PathVariable long petId) {
        petService.delete(StpUserUtil.getLoginIdAsLong(), petId);
        return R.ok();
    }
}
