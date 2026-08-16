package com.example.agent.controller;

import com.example.agent.exception.AppException;
import com.example.agent.model.ao.RegisterUserAo;
import com.example.agent.model.dto.auth.LoginRequest;
import com.example.agent.model.dto.auth.RegisterRequest;
import com.example.agent.model.vo.auth.CsrfVo;
import com.example.agent.model.vo.auth.UserVo;
import com.example.agent.model.vo.common.ApiResponse;
import com.example.agent.security.AppUserPrincipal;
import com.example.agent.service.CaptchaService;
import com.example.agent.service.RegistrationService;
import com.example.agent.service.UserQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CaptchaService captchaService;
    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserQueryService userQueryService;
    private final TokenBasedRememberMeServices rememberMeServices;

    public AuthController(CaptchaService captchaService, RegistrationService registrationService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          UserQueryService userQueryService,
                          TokenBasedRememberMeServices rememberMeServices) {
        this.captchaService = captchaService;
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userQueryService = userQueryService;
        this.rememberMeServices = rememberMeServices;
    }

    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> captcha(HttpSession session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(captchaService.create(session));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfVo> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok("获取成功", new CsrfVo(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVo>> register(@Valid @RequestBody RegisterRequest request,
                                                        HttpSession session) {
        captchaService.verifyAndConsume(session, request.imageCaptcha());
        var command = new RegisterUserAo(request.username(), request.email(), request.phone(),
                request.password(), request.confirmPassword());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("注册成功，请登录", UserVo.from(registrationService.register(command))));
    }

    @PostMapping("/login")
    public ApiResponse<UserVo> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.account().trim(), request.password()));
            servletRequest.getSession(true);
            servletRequest.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);
            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            if (request.rememberMe()) {
                rememberMeServices.loginSuccess(servletRequest, servletResponse, authentication);
            } else {
                rememberMeServices.logout(servletRequest, servletResponse, authentication);
            }
            return ApiResponse.ok("登录成功", UserVo.from(userQueryService.getActiveUser(principal.id())));
        } catch (BadCredentialsException exception) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserVo> me(Authentication authentication) {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return ApiResponse.ok("获取成功", UserVo.from(userQueryService.getActiveUser(principal.id())));
    }
}
