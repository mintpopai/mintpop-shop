package ai.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图片上传结果：自定义域名下的公开 URL，管理端直接填进商品图地址或插入富文本。
 */
@Data
@AllArgsConstructor
public class ImageUploadResponse {

    /** 公开访问地址，形如 https://shop-assets.mintpop.ai/products/2026/09/<uuid>.png */
    private String url;
}
