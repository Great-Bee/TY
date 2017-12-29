package com.greatbee.product.config;

import com.alibaba.fastjson.JSON;
import com.greatbee.utils.SessionUtils;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * UserSecurityInterceptor
 *
 * @author xiaobc
 * @date 17/12/29
 */
public class UserSecurityInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession();
        if (session.getAttribute(SessionUtils.TY_SESSION_CONFIG_USER) != null)
            return true;
        this.writeJsonToResponse(request, response, "{\"code\":-401,\"message\":\"未登录\"}");
        return false;
    }

    private void writeJsonToResponse(HttpServletRequest request, HttpServletResponse response, Object object) {
        response.setContentType("application/json; charset=UTF-8");
        try {
            if (object instanceof String) {
                response.getWriter().write((String) object);
            } else {
                response.getWriter().write(JSON.toJSONString(object));
            }
            response.getWriter().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
