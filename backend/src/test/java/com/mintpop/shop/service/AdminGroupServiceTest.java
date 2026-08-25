package com.mintpop.shop.service;

import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.request.AdminGroupUpsertRequest;
import com.mintpop.shop.response.AdminGroupResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGroupServiceTest {

    @Mock
    private ProductGroupMapper productGroupMapper;
    @Mock
    private ProductMapper productMapper;
    @InjectMocks
    private AdminGroupService adminGroupService;

    private ProductGroup group(long id, String nameZh) {
        ProductGroup g = new ProductGroup();
        g.setId(id);
        g.setNameZh(nameZh);
        g.setSortOrder(1);
        return g;
    }

    @Test
    @DisplayName("列表附组内商品数，无商品的组计 0")
    void listAttachesProductCounts() {
        when(productGroupMapper.selectList(any())).thenReturn(List.of(group(1L, "盲盒"), group(2L, "手办")));
        when(productMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("groupId", 1L, "cnt", 3L)));

        List<AdminGroupResponse> groups = adminGroupService.listGroups();

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getProductCount()).isEqualTo(3L);
        assertThat(groups.get(1).getProductCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("删除非空分组抛 210004")
    void deleteNonEmptyGroupRejected() {
        when(productGroupMapper.selectById(1L)).thenReturn(group(1L, "盲盒"));
        when(productMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> adminGroupService.deleteGroup(1L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.GROUP_NOT_EMPTY);
        verify(productGroupMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除空分组成功")
    void deleteEmptyGroupSucceeds() {
        when(productGroupMapper.selectById(1L)).thenReturn(group(1L, "盲盒"));
        when(productMapper.selectCount(any())).thenReturn(0L);

        adminGroupService.deleteGroup(1L);

        verify(productGroupMapper).deleteById(1L);
    }

    @Test
    @DisplayName("编辑不存在的分组抛 210003")
    void updateMissingGroupRejected() {
        when(productGroupMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> adminGroupService.updateGroup(9L,
                new AdminGroupUpsertRequest("新名", null, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("新增分组：英文名空白归一为空串，因为 name_en 是 NOT NULL DEFAULT ''")
    void createNormalizesBlankEnglishName() {
        adminGroupService.createGroup(new AdminGroupUpsertRequest(" 盲盒 ", "  ", 5));

        verify(productGroupMapper).insert(org.mockito.ArgumentMatchers.<ProductGroup>argThat(g ->
                g.getNameZh().equals("盲盒") && g.getNameEn().isEmpty() && g.getSortOrder() == 5));
    }

    @Test
    @DisplayName("编辑分组：清空英文名落成空串，真的写回数据库")
    void updateBlankEnglishNameBecomesEmptyString() {
        ProductGroup existing = group(3L, "盲盒");
        existing.setNameEn("Blind Boxes");
        when(productGroupMapper.selectById(3L)).thenReturn(existing);
        when(productMapper.selectCount(any())).thenReturn(0L);

        adminGroupService.updateGroup(3L, new AdminGroupUpsertRequest("盲盒", "  ", 1));

        verify(productGroupMapper).updateById(org.mockito.ArgumentMatchers.<ProductGroup>argThat(g ->
                g.getNameEn().isEmpty()));
    }
}
