package cn.net.mxz.timeimprint.task.web.identity.controller;

import cn.net.mxz.timeimprint.task.domain.shared.response.ApiResponse;
import cn.net.mxz.timeimprint.task.domain.shared.response.ApiResponses;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityException;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityService;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityUser;
import cn.net.mxz.timeimprint.task.identity.account.service.LoginResult;
import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 账号公开接口 E14—E29。 */
@RestController
public class UserController {

    private final IdentityService identity;

    public UserController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/api/v1/users/sms")
    public ApiResponse<Void> sms(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.sendSms(body.get("phoneNumber"));
            return ApiResponses.ok("验证码已发送", null);
        });
    }

    @PostMapping("/api/v1/users/sms/verify")
    public ApiResponse<Void> verify(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.verifySms(body.get("phoneNumber"), body.get("verificationCode"));
            return ApiResponses.ok("验证码有效", null);
        });
    }

    @PostMapping("/api/v1/users/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return call(() -> ApiResponses.ok(
                "注册成功", loginData(identity.register(body.get("phoneNumber"), body.get("code"), body.get("password"), body.get("nickname")))));
    }

    @PostMapping("/api/v1/users/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return call(() -> ApiResponses.ok("登录已受理", loginData(identity.login(body.get("phoneNumber"), body.get("password")))));
    }

    @PostMapping("/api/v1/users/login-authorization")
    public ApiResponse<Map<String, Object>> authorize(@RequestBody Map<String, String> body) {
        return call(() -> ApiResponses.ok(
                "授权验证成功",
                loginData(identity.authorize(body.get("challenge"), body.get("verificationString")))));
    }

    @PostMapping("/api/v1/users/logout")
    public ApiResponse<Void> logout() {
        return call(() -> {
            identity.logout(current());
            return ApiResponses.ok("已退出登录", null);
        });
    }

    @GetMapping("/api/v1/users/me")
    public ApiResponse<Map<String, Object>> me() {
        return call(() -> ApiResponses.ok("已返回当前用户", userData(current())));
    }

    @PostMapping("/api/v1/users/change-password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.changePassword(current(), body.get("oldPassword"), body.get("password"));
            return ApiResponses.ok("密码修改成功", null);
        });
    }

    @PostMapping("/api/v1/users/change-nickname")
    public ApiResponse<Void> changeNickname(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.changeNickname(current(), body.get("nickname"));
            return ApiResponses.ok("昵称修改成功", null);
        });
    }

    @PostMapping("/api/v1/users/password-reset")
    public ApiResponse<Void> reset(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.resetPassword(body.get("phoneNumber"), body.get("code"), body.get("password"));
            return ApiResponses.ok("密码重置成功", null);
        });
    }

    @GetMapping("/api/v1/users")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return call(() -> {
            IdentityUser admin = current();
            var list = identity.listUsers(admin, keyword, page, pageSize).stream().map(this::userData).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", list);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("total", identity.countUsers(admin, keyword));
            return ApiResponses.ok("用户信息获取成功", data);
        });
    }

    @GetMapping("/api/v1/users/wechat/config")
    public ApiResponse<Map<String, Object>> wechatConfig(@RequestParam(defaultValue = "web") String appType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appType", appType);
        data.put("state", "capture-state");
        data.put("authUrl", "");
        return ApiResponses.ok("已返回微信登录配置", data);
    }

    @PostMapping("/api/v1/users/wechat/callback")
    public ApiResponse<Map<String, Object>> wechatCallback(@RequestBody Map<String, String> body) {
        return call(() -> ApiResponses.ok(
                "微信登录已受理",
                loginData(identity.wechatCallback(body.get("appType"), body.get("code"), body.get("state")))));
    }

    @PostMapping("/api/v1/users/wechat/bind")
    public ApiResponse<Void> bind(@RequestBody Map<String, String> body) {
        return call(() -> {
            identity.bindWeChat(current(), body.get("appType"), body.get("code"), body.get("state"));
            return ApiResponses.ok("微信绑定成功", null);
        });
    }

    @PostMapping("/api/v1/users/wechat/unbind")
    public ApiResponse<Void> unbind() {
        return call(() -> {
            identity.unbindWeChat(current());
            return ApiResponses.ok("微信解绑成功", null);
        });
    }

    @PostMapping("/api/v1/users/wechat/bind-phone")
    public ApiResponse<Map<String, Object>> bindPhone(@RequestBody Map<String, String> body) {
        return call(() -> ApiResponses.ok(
                "手机号绑定成功",
                loginData(identity.bindPhone(current(), body.get("phoneNumber"), body.get("code"), body.get("password")))));
    }

    private IdentityUser current() {
        String actor = TestActorContextHolder.get()
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "没有可信调用身份"))
                .principalId();
        String tenant = TestActorContextHolder.get().orElseThrow().tenantKey();
        return identity.requireActor(tenant, actor);
    }

    private Map<String, Object> loginData(LoginResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorizationRequired", result.authorizationRequired());
        data.put("challenge", result.challenge());
        data.put("token", result.token());
        data.put("actorKey", result.actorKey());
        data.put("nickname", result.nickname());
        data.put("phoneNumber", result.phoneNumber());
        data.put("admin", result.admin());
        return data;
    }

    private Map<String, Object> userData(IdentityUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorKey", user.actorKey());
        data.put("phoneNumber", user.phoneNumber());
        data.put("nickname", user.nickname());
        data.put("admin", user.admin());
        data.put("accountStatus", user.accountStatus());
        return data;
    }

    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IdentityException ex) {
            throw new ApplicationException(ex.errorCode(), ex.getMessage());
        }
    }
}
