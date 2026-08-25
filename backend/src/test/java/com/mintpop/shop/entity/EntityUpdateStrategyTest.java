package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体更新策略守卫。
 *
 * <p>MyBatis-Plus 的 updateStrategy 默认是 NOT_NULL：值为 null 的属性根本不进 UPDATE 的 SET 子句。
 * 于是「整实体写回」模式下，把一个可空字段清空（归一成 null）保存，那一列会被静默跳过、保留原值——
 * 管理端表现为「清空不生效」。DDL 可空的列必须显式标 ALWAYS，让 null 真的写进去。
 *
 * <p>本测试同时是防漏网：新加的列必须在下面两个集合之一里登记，否则直接失败，
 * 逼作者按 DDL 的可空性表态，而不是默默继承 NOT_NULL 又踩一次同一个坑。
 */
class EntityUpdateStrategyTest {

    /** product 表中 DDL 为 NULL 的列：清空 = 写 null，必须 ALWAYS */
    private static final Set<String> PRODUCT_NULLABLE = Set.of(
            "description_zh", "description_en", "detail_zh", "detail_en",
            "badge_zh", "badge_en", "image_url");

    /**
     * product 表中 DDL 为 NOT NULL 的列：值永不为 null，NOT_NULL 策略对它们无害。
     * 注意 name_en 是 NOT NULL DEFAULT ''——它的「空」是空串而非 null，见 AdminProductService.apply。
     */
    private static final Set<String> PRODUCT_NOT_NULL = Set.of(
            "id", "group_id", "name_zh", "name_en", "accent", "price_cents", "on_sale",
            "created_at", "updated_at");

    /** product_group 表中 DDL 为 NULL 的列：暂无 */
    private static final Set<String> GROUP_NULLABLE = Set.of();

    /** product_group 表中 DDL 为 NOT NULL 的列（name_en 同为 NOT NULL DEFAULT ''） */
    private static final Set<String> GROUP_NOT_NULL = Set.of(
            "id", "name_zh", "name_en", "sort_order", "created_at", "updated_at");

    @Test
    @DisplayName("product: DDL 可空列的更新策略必须是 ALWAYS，否则清空字段保存会静默保留原值")
    void productNullableColumnsUpdateAlways() {
        assertNullableColumnsUpdateAlways(Product.class, PRODUCT_NULLABLE, PRODUCT_NOT_NULL);
    }

    @Test
    @DisplayName("product_group: DDL 可空列的更新策略必须是 ALWAYS，否则清空字段保存会静默保留原值")
    void groupNullableColumnsUpdateAlways() {
        assertNullableColumnsUpdateAlways(ProductGroup.class, GROUP_NULLABLE, GROUP_NOT_NULL);
    }

    private void assertNullableColumnsUpdateAlways(Class<?> entity, Set<String> nullable, Set<String> notNull) {
        List<TableFieldInfo> fields = tableInfo(entity).getFieldList();

        // 先确认没有漏登记的列——新加字段必须显式归类，不能靠默认策略蒙混过关
        Set<String> unregistered = new TreeSet<>();
        for (TableFieldInfo field : fields) {
            if (!nullable.contains(field.getColumn()) && !notNull.contains(field.getColumn())) {
                unregistered.add(field.getColumn());
            }
        }
        assertThat(unregistered)
                .as("%s 有未登记的列，请按 DDL 的可空性登记到本测试的 NULLABLE / NOT_NULL 集合", entity.getSimpleName())
                .isEmpty();

        Set<String> notAlways = new TreeSet<>();
        for (TableFieldInfo field : fields) {
            if (nullable.contains(field.getColumn()) && field.getUpdateStrategy() != FieldStrategy.ALWAYS) {
                notAlways.add(field.getColumn());
            }
        }
        assertThat(notAlways)
                .as("%s 的这些可空列未标 @TableField(updateStrategy = FieldStrategy.ALWAYS)，清空后不会被写回数据库",
                        entity.getSimpleName())
                .isEmpty();
    }

    /** 脱离数据库拿到 MyBatis-Plus 解析后的表元信息（策略的最终生效值，含全局配置的影响） */
    private TableInfo tableInfo(Class<?> entity) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        return TableInfoHelper.initTableInfo(assistant, entity);
    }
}
