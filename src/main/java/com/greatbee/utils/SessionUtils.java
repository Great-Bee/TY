package com.greatbee.utils;

import com.greatbee.base.util.DataUtil;
import com.greatbee.core.lego.LegoException;
import com.greatbee.core.utils.SessionUtil;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * SessionUtils
 *
 * @author xiaobc
 * @date 17/10/10
 */
public class SessionUtils extends SessionUtil {

    public static final String TY_SESSION_CONFIG_USER = "TY_SESSION_CONFIG_USER";

    private static Logger logger = Logger.getLogger(SessionUtils.class);

    /**
     * 获取session中的值
     *
     * @param req
     * @param key
     * @return
     */
    public static String getStringValue(HttpServletRequest req, String key) {
        HttpSession session = req.getSession(false);
        Object obj = session.getAttribute(key);
        return DataUtil.getString(obj, null);
    }

    /**
     * 获取session中的值
     *
     * @param req
     * @param key
     * @return
     */
    public static Object getValue(HttpServletRequest req, String key) throws LegoException {
        return getObjValue(req, key);
    }

    /**
     * 获取某个key中的属性值
     *
     * @param req
     * @param key
     * @param attrName
     * @return
     */
    public static String getAttrValue(HttpServletRequest req, String key, String attrName) throws LegoException {
        return getAttributeValue(req,key,attrName);
    }


}
