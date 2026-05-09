package com.poker.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;

/**
 * IP工具类 - 从请求头中提取真实客户端IP
 * 支持内网穿透场景下通过自定义请求头传递真实IP
 */
public class IpUtils {

    /**
     * 自定义真实IP请求头列表（按优先级排序）
     */
    private static final List<String> REAL_IP_HEADERS = Arrays.asList(
            "X-Real-IP",
            "X-Client-IP",
            "X-Forwarded-For",
            "X-Remote-Addr",
            "X-Public-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    /**
     * 从请求中获取真实客户端IP
     *
     * @param request HTTP请求
     * @return 真实IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 优先从自定义请求头获取
        for (String header : REAL_IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // 如果是 X-Forwarded-For，取第一个IP
                if ("X-Forwarded-For".equalsIgnoreCase(header) && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // 回退到 remoteAddr
        String remoteAddr = request.getRemoteAddr();
        return isValidIp(remoteAddr) ? remoteAddr : "unknown";
    }

    /**
     * 验证IP地址是否有效
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        ip = ip.trim();
        // 排除无效值
        if ("unknown".equalsIgnoreCase(ip) || "-".equals(ip)) {
            return false;
        }
        // 简单的IP格式验证
        String ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";
        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern);
    }
}
