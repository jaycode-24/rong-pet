package com.jaycode.rongpet.module.account.service;

import com.jaycode.rongpet.module.account.dto.UserVO;
import com.jaycode.rongpet.module.account.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户实体与视图转换。密文字段在解密后注入展示字段，由服务层负责。
 */
@Mapper(componentModel = "spring")
public interface AppUserVOAssembler {

    /** 密文不出现在 VO 中，maskedPhone 由服务层解密后填充 */
    @Mapping(target = "maskedPhone", ignore = true)
    UserVO toVO(User user);
}
