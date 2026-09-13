package ai.mintpop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.shop.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper。库存加减一律是相对更新（stock = stock ± n）+ WHERE 条件，由数据库保证原子性，禁止读改写。
 */
public interface ProductMapper extends BaseMapper<Product> {

    /** 预占：余量够才扣。返回 0 行 = 库存不足（或商品此刻已被改成不限库存，调用方按不足处理即可） */
    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** 归还：只对限库存商品加回去；商品已改成不限则无处可还，0 行即可 */
    @Update("UPDATE product SET stock = stock + #{quantity} WHERE id = #{id} AND stock IS NOT NULL")
    int releaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** 补扣：入账时预占已被归还，钱已收必须成单，允许扣成负数 */
    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{id} AND stock IS NOT NULL")
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);
}
