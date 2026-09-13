package ai.mintpop.shop.exception;

import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把业务异常与未预期异常统一收口成 ApiResponse（HTTP 200），
 * 文案经 MessageSource 按当前请求语言解析。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.fail(e.getBizCode(), resolve(e.getBizCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String msg = messageSource.getMessage("biz.param-invalid.detail",
                new Object[]{detail}, LocaleContextHolder.getLocale());
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null, msg);
    }

    /** multipart 超过 spring.servlet.multipart 上限：在进 controller 之前就被 Spring 拦下，转成图片过大 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传体积超限", e);
        return ApiResponse.fail(BizCodeEnum.IMAGE_TOO_LARGE, resolve(BizCodeEnum.IMAGE_TOO_LARGE));
    }

    /** multipart 缺必填 part（如上传接口没带 file 字段）：属于参数错误，不应落到「系统繁忙」 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ApiResponse<Void> handleMissingPart(MissingServletRequestPartException e) {
        return ApiResponse.fail(BizCodeEnum.PARAM_INVALID, resolve(BizCodeEnum.PARAM_INVALID));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.SYSTEM_ERROR, resolve(BizCodeEnum.SYSTEM_ERROR));
    }

    private String resolve(BizCodeEnum bizCode) {
        return messageSource.getMessage(bizCode.getMessageKey(), null, LocaleContextHolder.getLocale());
    }
}
