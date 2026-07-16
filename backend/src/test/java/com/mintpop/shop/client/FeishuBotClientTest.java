package com.mintpop.shop.client;

import com.mintpop.shop.config.NotifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FeishuBotClientTest {

    private static final String WEBHOOK =
            "https://open.feishu.cn/open-apis/bot/v2/hook/test-token";

    private NotifyProperties properties;
    private MockRestServiceServer server;
    private FeishuBotClient client;

    @BeforeEach
    void setUp() {
        properties = new NotifyProperties();
        properties.setWebhookUrl(WEBHOOK);
        properties.setSecret("test-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FeishuBotClient(builder.build(), properties);
    }

    private LinkedHashMap<String, String> fields() {
        LinkedHashMap<String, String> f = new LinkedHashMap<>();
        f.put("订单号", "MP20260716120000123456");
        f.put("金额", "$118.00");
        return f;
    }

    @Test
    @DisplayName("签名算法：已知向量（timestamp+换行+secret 为 HMAC 密钥、空串摘要、Base64）")
    void signKnownVector() {
        assertThat(FeishuBotClient.sign(1700000000L, "test-secret"))
                .isEqualTo("mbm4Y4oluIPQ00qlBIhX8vAZ0EKv3nw0LuTb91jPL84=");
    }

    @Test
    @DisplayName("发送卡片：POST interactive 卡片，带签名、绿色标题与字段内容")
    void sendCardPostsInteractiveCard() {
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.msg_type").value("interactive"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.sign").isNotEmpty())
                .andExpect(jsonPath("$.card.header.template").value("green"))
                .andExpect(jsonPath("$.card.header.title.content")
                        .value("MintPop Shop 新订单已支付"))
                .andExpect(jsonPath("$.card.elements[0].text.content")
                        .value("**订单号：**MP20260716120000123456\n**金额：**$118.00"))
                .andRespond(withSuccess("{\"code\":0,\"msg\":\"success\"}",
                        MediaType.APPLICATION_JSON));

        client.sendCard("MintPop Shop 新订单已支付", fields());
        server.verify();
    }

    @Test
    @DisplayName("未配置 secret：请求体不带 timestamp/sign 字段")
    void noSecretNoSign() {
        properties.setSecret(null);
        server.expect(requestTo(WEBHOOK))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.sign").doesNotExist())
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        client.sendCard("标题", fields());
        server.verify();
    }

    @Test
    @DisplayName("飞书返回非 0 code：抛 IllegalStateException（由上层消化）")
    void nonZeroCodeThrows() {
        server.expect(requestTo(WEBHOOK))
                .andRespond(withSuccess("{\"code\":19021,\"msg\":\"sign match fail\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendCard("标题", fields()))
                .isInstanceOf(IllegalStateException.class);
    }
}
