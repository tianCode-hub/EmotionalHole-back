package com.myk.emotionalHole.controller;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.dto.AddAdminRequest;
import com.myk.emotionalHole.dto.AdminLoginRequest;
import com.myk.emotionalHole.dto.AdminRefreshTokenRequest;
import com.myk.emotionalHole.dto.ChangePasswordRequest;
import com.myk.emotionalHole.dto.LoginResponse;
import com.myk.emotionalHole.dto.UpdateAdminPasswordRequest;
import com.myk.emotionalHole.entity.Admin;
import com.myk.emotionalHole.mapper.AdminMapper;
import com.myk.emotionalHole.service.AdminService;
import com.myk.emotionalHole.util.PageUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员登录和认证接口
 */
@RestController
@RequestMapping("admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @Resource
    private AdminMapper adminMapper;

    /**
     * 管理员登录接口
     *
     * @param request            登录请求，包含adminAccount和adminPassword参数
     * @param httpServletRequest HttpServletRequest对象，用于获取客户端IP
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<LoginResponse> adminLogin(@RequestBody @Valid AdminLoginRequest request, HttpServletRequest httpServletRequest) {
        logger.info("开始处理管理员登录请求，请求参数: {}", request);
        try {
            // 获取请求参数
            String adminAccount = request.getAdminAccount();
            String adminPassword = request.getAdminPassword();

            // 获取客户端IP
            String operateIp = getClientIp(httpServletRequest);
            logger.info("客户端IP: {}", operateIp);

            // 调用service层处理登录请求
            Result<LoginResponse> result = adminService.adminLogin(adminAccount, adminPassword, operateIp);
            logger.info("管理员登录请求处理完成，结果: {}", result);

            return result;
        } catch (Exception e) {
            logger.error("管理员登录请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 刷新令牌接口
     *
     * @param request 刷新令牌请求，包含refreshToken参数
     * @return 新的访问令牌
     */
    @PostMapping("/refreshToken")
    public Result<Map<String, String>> refreshToken(@RequestBody @Valid AdminRefreshTokenRequest request) {
        logger.info("开始处理管理员令牌刷新请求");
        try {
            // 获取刷新令牌
            String refreshToken = request.getRefreshToken();

            // 调用service层处理令牌刷新请求
            Result<Map<String, String>> result = adminService.refreshToken(refreshToken);
            logger.info("管理员令牌刷新请求处理完成，结果: {}", result);

            return result;
        } catch (Exception e) {
            logger.error("管理员令牌刷新请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 获取管理员信息
     *
     * @param request HttpServletRequest对象，用于获取认证信息
     * @return 管理员信息
     */
    @GetMapping("/profile")
    public Result<?> getAdminInfo(HttpServletRequest request) {
        logger.info("开始处理获取管理员信息请求");
        try {
            // 从请求中获取管理员ID（实际项目中应该从认证信息中获取）
            // 这里假设已经通过拦截器验证了管理员身份
            Result<?> result = adminService.getAdminInfo(request);
            logger.info("获取管理员信息请求处理完成，结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("获取管理员信息请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 修改管理员密码
     *
     * @param request            修改密码请求，包含oldPassword和newPassword参数
     * @param httpServletRequest HttpServletRequest对象，用于获取认证信息
     * @return 修改结果
     */
    @PostMapping("/profile/change-password")
    public Result<?> changePassword(@RequestBody @Valid ChangePasswordRequest request, HttpServletRequest httpServletRequest) {
        logger.info("开始处理修改管理员密码请求");
        try {
            // 获取请求参数
            String oldPassword = request.getOldPassword();
            String newPassword = request.getNewPassword();

            // 调用service层处理密码修改请求
            Result<?> result = adminService.changePassword(oldPassword, newPassword, httpServletRequest);
            logger.info("修改管理员密码请求处理完成，结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("修改管理员密码请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 获取用户统计信息（管理员专用）
     *
     * @param anonymousId 用户匿名ID
     * @return 用户统计信息
     */
    @GetMapping("/user/statistics")
    public Result<?> getUserStatistics(@RequestParam String anonymousId) {
        logger.info("开始处理管理员获取用户统计信息请求，anonymousId: {}", anonymousId);
        try {
            Result<?> result = adminService.getUserStatistics(anonymousId);
            logger.info("管理员获取用户统计信息请求处理完成");
            return result;
        } catch (Exception e) {
            logger.error("管理员获取用户统计信息请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 获取管理员列表（支持分页）
     *
     * @param page 页码
     * @param size 每页大小
     * @return 管理员列表
     */
    @GetMapping("/list")
    public Result<?> getAdminList(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        logger.info("开始处理获取管理员列表请求，page: {}, size: {}", page, size);
        try {
            // 分页参数边界统一处理：page>=1，size上限100
            PageUtils.PageParam pageParam = PageUtils.createPageParam(page, size);
            Result<?> result = adminService.getAdminList(pageParam.getPageNum(), pageParam.getPageSize());
            logger.info("获取管理员列表请求处理完成");
            return result;
        } catch (Exception e) {
            logger.error("获取管理员列表请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 添加管理员
     *
     * @param request 添加请求，包含adminAccount和adminPassword参数
     * @return 添加结果
     */
    @PostMapping("")
    public Result<?> addAdmin(@RequestBody @Valid AddAdminRequest request) {
        logger.info("开始处理添加管理员请求，参数: {}", request);
        try {
            String adminAccount = request.getUsername();
            String adminPassword = request.getPassword();
            Result<?> result = adminService.addAdmin(adminAccount, adminPassword);
            logger.info("添加管理员请求处理完成，结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("添加管理员请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 删除管理员
     *
     * @param id 管理员ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteAdmin(@PathVariable Long id) {
        logger.info("开始处理删除管理员请求，ID: {}", id);
        try {
            Result<?> result = adminService.deleteAdmin(id);
            logger.info("删除管理员请求处理完成，结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("删除管理员请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 修改管理员密码（管理员列表页面使用）
     *
     * @param id      管理员ID
     * @param request 修改请求，包含password参数
     * @return 修改结果
     */
    @PutMapping("/{id}")
    public Result<?> updateAdminPassword(@PathVariable Long id, @RequestBody @Valid UpdateAdminPasswordRequest request) {
        logger.info("开始处理修改管理员密码请求，ID: {}", id);
        try {
            String password = request.getPassword();
            Admin admin = adminMapper.selectById(id);
            if (admin == null) {
                return Result.error(404, "管理员不存在");
            }
            admin.setAdminPassword(password);
            adminMapper.updateById(admin);
            logger.info("修改管理员密码请求处理完成");
            return Result.success("修改成功");
        } catch (Exception e) {
            logger.error("修改管理员密码请求处理失败: {}", e.getMessage(), e);
            return Result.error(500, "系统异常，请重试");
        }
    }

    /**
     * 获取客户端IP地址
     *
     * @param request HttpServletRequest对象
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，取第一个非unknown的IP
        if (ip != null && ip.contains(",")) {
            String[] ips = ip.split(",");
            for (String s : ips) {
                if (!"unknown".equalsIgnoreCase(s)) {
                    ip = s.trim();
                    break;
                }
            }
        }
        return ip;
    }
}
