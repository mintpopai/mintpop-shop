package com.mintpop.shop.config;

import com.mintpop.shop.security.CookieOAuth2AuthorizationRequestRepository;
import com.mintpop.shop.security.OidcLoginSuccessHandler;
import com.mintpop.shop.security.PostLoginRedirectCookie;
import com.mintpop.shop.security.SessionCookieAuthFilter;
import com.mintpop.shop.service.SessionTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（BFF）：OIDC 只管登录握手，日常鉴权走自签会话 Cookie，全程无服务端 session。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final SessionTokenService sessionTokenService;
    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final PostLoginRedirectCookie postLoginRedirectCookie;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 会话 Cookie 为 SameSite=Lax：跨站 POST 不携带 Cookie，写接口天然免疫 CSRF。
                // 已知取舍：Lax 放行顶级 GET 导航，GET /auth/logout 可被第三方页面强制触发（仅骚扰级
                // 登出、无数据风险），接受该风险；后续接支付若引入 GET 写操作需重新评估。
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 登出走 AuthController 的 GET /auth/logout，禁用框架默认 POST /logout
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 白名单式：/api 下默认需登录，公开接口显式放行——新增接口忘登记时默认安全
                        .requestMatchers("/api/groups").permitAll()
                        // 商品详情页游客可看（只下发上架商品）
                        .requestMatchers(HttpMethod.GET, "/api/products/*").permitAll()
                        // Stripe webhook：无登录态，安全性由请求体验签保证
                        .requestMatchers("/api/v1/payment/webhook/stripe").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(login -> login
                        // 握手中间态存 Cookie（只对本浏览器有效），不落服务端 session
                        .authorizationEndpoint(ae -> ae
                                .authorizationRequestResolver(forceLoginAuthorizationRequestResolver())
                                .authorizationRequestRepository(
                                        new CookieOAuth2AuthorizationRequestRepository()))
                        // 回调路径按统一账号接入规范固定为 /auth/callback
                        .redirectionEndpoint(re -> re.baseUri("/auth/callback"))
                        .successHandler(oidcLoginSuccessHandler)
                        // 握手失败（用户取消/state 不符等）：记录原因后回前端带标记，由前端按语言提示；
                        // 同时清掉回跳路径 Cookie，避免这次没走完的登录污染下一次登录的落点
                        .failureHandler((request, response, exception) -> {
                            log.warn("OIDC 登录失败", exception);
                            postLoginRedirectCookie.expire(request, response);
                            response.sendRedirect(authProperties.getFrontendBaseUrl() + "?login_error=1");
                        }))
                // 未登录访问受保护接口：返回 401（规范允许鉴权中间件用原生状态码），不重定向登录页
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SessionCookieAuthFilter(sessionTokenService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * ID Token 验签算法对齐账号中心：Logto 用 ES384（JWKS 与发现文档的
     * id_token_signing_alg_values_supported 均只有 ES384），而 Spring Security 默认只认 RS256，
     * 不显式指定会在回调时报 "Another algorithm expected"。若账号中心轮换签名算法需同步调整。
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(registration -> SignatureAlgorithm.ES384);
        return factory;
    }

    /**
     * 授权请求追加 prompt=login（OIDC 标准参数）：点「登录」必须重新走账号中心登录页，
     * 不因账号中心已有会话而静默放行——产品决策：登录动作必须显式。
     */
    private OAuth2AuthorizationRequestResolver forceLoginAuthorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.additionalParameters(params -> params.put("prompt", "login")));
        return resolver;
    }
}
