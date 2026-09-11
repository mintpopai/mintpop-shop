package com.mintpop.shop.request;

import com.mintpop.shop.service.AdminProductService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端商品请求体的长度校验：名称、描述各有上限，恰好等于上限放行、超一字即拒。
 * 用 Validator 直接校验，不起 Spring 上下文。
 */
class AdminProductUpsertRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 一个各字段都合法的请求，测试里只改要测的那一项 */
    private static AdminProductUpsertRequest validRequest() {
        AdminProductUpsertRequest req = new AdminProductUpsertRequest();
        req.setGroupId(1L);
        req.setNameZh("薄荷精灵盲盒");
        req.setAccent("MINT");
        req.setPriceCents(6900L);
        req.setOnSale(true);
        return req;
    }

    private Set<String> violatedProperties(AdminProductUpsertRequest req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("名称与描述恰好等于上限时通过校验")
    void acceptsNameAndDescriptionAtMaxLength() {
        AdminProductUpsertRequest req = validRequest();
        req.setNameZh("名".repeat(AdminProductService.NAME_MAX_LENGTH));
        req.setNameEn("n".repeat(AdminProductService.NAME_MAX_LENGTH));
        req.setDescriptionZh("描".repeat(AdminProductService.DESCRIPTION_MAX_LENGTH));
        req.setDescriptionEn("d".repeat(AdminProductService.DESCRIPTION_MAX_LENGTH));

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("中英文名称超过上限一字即被拒")
    void rejectsNameOverMaxLength() {
        AdminProductUpsertRequest req = validRequest();
        req.setNameZh("名".repeat(AdminProductService.NAME_MAX_LENGTH + 1));
        req.setNameEn("n".repeat(AdminProductService.NAME_MAX_LENGTH + 1));

        assertThat(violatedProperties(req)).containsExactlyInAnyOrder("nameZh", "nameEn");
    }

    @Test
    @DisplayName("中英文描述超过上限一字即被拒")
    void rejectsDescriptionOverMaxLength() {
        AdminProductUpsertRequest req = validRequest();
        req.setDescriptionZh("描".repeat(AdminProductService.DESCRIPTION_MAX_LENGTH + 1));
        req.setDescriptionEn("d".repeat(AdminProductService.DESCRIPTION_MAX_LENGTH + 1));

        assertThat(violatedProperties(req)).containsExactlyInAnyOrder("descriptionZh", "descriptionEn");
    }

    @Test
    @DisplayName("描述留空不触发长度校验")
    void allowsNullDescription() {
        AdminProductUpsertRequest req = validRequest();
        req.setDescriptionZh(null);
        req.setDescriptionEn(null);

        assertThat(validator.validate(req)).isEmpty();
    }
}
