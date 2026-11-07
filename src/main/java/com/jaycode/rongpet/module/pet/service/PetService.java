package com.jaycode.rongpet.module.pet.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.pet.dto.PetRequest;
import com.jaycode.rongpet.module.pet.dto.PetVO;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 宠物档案服务。所有操作强制校验归属：只允许本人查看与维护。
 */
@Service
@RequiredArgsConstructor
public class PetService {

    private static final Set<String> PET_TYPES = Set.of("CAT", "DOG", "EXOTIC");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");
    private static final Set<String> STERILIZED = Set.of("YES", "NO", "UNKNOWN");
    private static final Set<String> VACCINE = Set.of("DONE", "PARTIAL", "NONE");

    private final PetMapper petMapper;

    public PetVO create(long userId, PetRequest request) {
        validate(request);
        Pet pet = new Pet();
        applyRequest(pet, request);
        pet.setUserId(userId);
        petMapper.insert(pet);
        return toVO(petMapper.selectById(pet.getId()));
    }

    public PetVO update(long userId, long petId, PetRequest request) {
        validate(request);
        Pet existing = requireOwned(userId, petId);
        applyRequest(existing, request);
        petMapper.updateById(existing);
        return toVO(requireOwned(userId, petId));
    }

    public void delete(long userId, long petId) {
        requireOwned(userId, petId);
        petMapper.deleteById(petId);
    }

    public PetVO detail(long userId, long petId) {
        return toVO(requireOwned(userId, petId));
    }

    public List<PetVO> list(long userId) {
        return petMapper.selectList(new LambdaQueryWrapper<Pet>()
                        .eq(Pet::getUserId, userId)
                        .orderByDesc(Pet::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    public Pet requireOwned(long userId, long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "宠物档案不存在");
        }
        return pet;
    }

    private void validate(PetRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getPetType()) || !StringUtils.hasText(request.getBreed())
                || !StringUtils.hasText(request.getGender()) || !StringUtils.hasText(request.getAvatarUrl())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请完整填写宠物基础信息");
        }
        if (!PET_TYPES.contains(request.getPetType())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "宠物类型不合法");
        }
        if (!GENDERS.contains(request.getGender())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "性别不合法");
        }
        if (StringUtils.hasText(request.getSterilized()) && !STERILIZED.contains(request.getSterilized())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "绝育状态不合法");
        }
        if (StringUtils.hasText(request.getVaccineStatus()) && !VACCINE.contains(request.getVaccineStatus())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "疫苗状态不合法");
        }
        if (request.getPhotos() != null && request.getPhotos().size() > 6) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "宠物照片最多6张");
        }
    }

    private void applyRequest(Pet pet, PetRequest request) {
        pet.setName(request.getName());
        pet.setPetType(request.getPetType());
        pet.setBreed(request.getBreed());
        pet.setGender(request.getGender());
        pet.setBirthDate(request.getBirthDate());
        pet.setWeight(request.getWeight());
        pet.setSterilized(StringUtils.hasText(request.getSterilized()) ? request.getSterilized() : "UNKNOWN");
        pet.setVaccineStatus(request.getVaccineStatus());
        pet.setChipFeature(request.getChipFeature());
        pet.setAvatarUrl(request.getAvatarUrl());
        pet.setPhotos(request.getPhotos() == null || request.getPhotos().isEmpty()
                ? null : JSONUtil.toJsonStr(request.getPhotos()));
        pet.setRemark(request.getRemark());
    }

    private PetVO toVO(Pet pet) {
        PetVO vo = new PetVO();
        vo.setId(pet.getId());
        vo.setName(pet.getName());
        vo.setPetType(pet.getPetType());
        vo.setBreed(pet.getBreed());
        vo.setGender(pet.getGender());
        vo.setBirthDate(pet.getBirthDate());
        vo.setWeight(pet.getWeight());
        vo.setSterilized(pet.getSterilized());
        vo.setVaccineStatus(pet.getVaccineStatus());
        vo.setChipFeature(pet.getChipFeature());
        vo.setAvatarUrl(pet.getAvatarUrl());
        vo.setAiAvatarUrl(pet.getAiAvatarUrl());
        vo.setPhotos(parsePhotos(pet.getPhotos()));
        vo.setRemark(pet.getRemark());
        vo.setCreateTime(pet.getCreateTime());
        return vo;
    }

    @SuppressWarnings("unchecked")
    public static List<String> parsePhotos(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
