package com.greatbee.product.controller.ty;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.greatbee.base.bean.DBException;
import com.greatbee.base.bean.Data;
import com.greatbee.base.util.CollectionUtil;
import com.greatbee.base.util.MD5Util;
import com.greatbee.base.util.StringUtil;
import com.greatbee.core.ExceptionCode;
import com.greatbee.core.bean.buzz.Code;
import com.greatbee.core.bean.client.Page;
import com.greatbee.core.bean.constant.CT;
import com.greatbee.core.bean.constant.IOFT;
import com.greatbee.core.bean.server.InputField;
import com.greatbee.core.bean.user.App;
import com.greatbee.core.bean.user.AppRole;
import com.greatbee.core.bean.user.UserAppRole;
import com.greatbee.core.bean.view.Condition;
import com.greatbee.core.bean.view.ConnectorTree;
import com.greatbee.core.bean.view.MultiCondition;
import com.greatbee.core.db.mysql.manager.MysqlDataManager;
import com.greatbee.core.lego.LegoException;
import com.greatbee.core.manager.TYDriver;
import com.greatbee.core.manager.utils.CustomBuildUtils;
import com.greatbee.procut.Response;
import com.greatbee.utils.SessionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

/**
 * Index
 * <p>
 * Created by CarlChen on 2017/5/27.
 */
@Controller
public class HomeController implements ExceptionCode {
    private static final Logger logger = Logger.getLogger(HomeController.class);

    @Autowired
    TYDriver tyDriver;

    @Autowired
    MysqlDataManager mysqlDataManager;

    private static final String PAGES = "pages";

    private static final String BUZZ = "buzz";

    @RequestMapping(value = "/ty/login", method = {RequestMethod.POST, RequestMethod.GET})
    public
    @ResponseBody
    Response login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Response tyResponse = new Response();
        HttpSession session = request.getSession();
        try {
            String username = request.getParameter("userName");
            String password = request.getParameter("password");

            if(StringUtil.isInvalid(username)){
                throw new LegoException("用户名必填",30000);
            }
            if(StringUtil.isInvalid(password)){
                throw new LegoException("密码必填",30001);
            }
            String md5Pwd = MD5Util.getMD5(password);

            List<InputField> ifs = new ArrayList<>();
            InputField userNameIf = new InputField();
            userNameIf.setCt(CT.EQ.getName());
            userNameIf.setIft(IOFT.Condition.getType());
            userNameIf.setFieldName("userName");
            userNameIf.setFieldValue(username);
            ifs.add(userNameIf);
            InputField pwdIf = new InputField();
            pwdIf.setCt(CT.EQ.getName());
            pwdIf.setIft(IOFT.Condition.getType());
            pwdIf.setFieldName("password");
            pwdIf.setFieldValue(md5Pwd);
            ifs.add(pwdIf);

            ConnectorTree conn = CustomBuildUtils.customBuildSelectConnectorTree(tyDriver, "ty_user", null, ifs);
            Data userinfo = mysqlDataManager.read(conn);
            if(userinfo==null||userinfo.size()<=0){
                throw new LegoException("账号或者密码错误",30002);
            }
            userinfo.put("name",userinfo.getString("xingMing"));
            //放到session
            session.setAttribute(TY_SESSION_CONFIG_USER, JSON.toJSONString(userinfo));

            //查询已开通的应用列表
            List<UserAppRole> userAppRoles = tyDriver.getUserAppRoleManager().list("userAlias", userinfo.getString("alias"));

            Set<String> appSet = new HashSet<>();
            for (UserAppRole uar : userAppRoles) {
                appSet.add(uar.getAppAlias());
            }

            StringBuilder appsStr = new StringBuilder();
            int k = 0;
            for (String item : appSet) {
                if (k > 0) {
                    appsStr.append(",");
                }
                appsStr.append(item);
                k++;
            }
            if (CollectionUtil.isInvalid(appSet)) {
                //设置一个不存在的app别名
                appsStr.append("9999999");
            }
            //取有效的应用
            MultiCondition condition = new MultiCondition();
            Condition aliasCon = new Condition();
            aliasCon.setConditionFieldName("alias");
            aliasCon.setCt(CT.IN.getName());
            aliasCon.setConditionFieldValue(appsStr.toString());
            condition.addCondition(aliasCon);
            Condition statusCon = new Condition();
            statusCon.setConditionFieldName("online");
            statusCon.setConditionFieldValue("1");
            condition.addCondition(statusCon);
            List<App> onlineApps = tyDriver.getAppManager().list(condition);

            if (CollectionUtil.isInvalid(onlineApps)) {
                return tyResponse;
            }
            //构造 存到session中的应用信息
            JSONArray apps = new JSONArray();

            for (App app : onlineApps) {
                JSONObject appJson = JSON.parseObject(JSONObject.toJSONString(app));
                String buzzCode = "";
                if(StringUtil.isValid(app.getBuzzAlias())){
                    Code code = tyDriver.getCodeManager().getCodeByAlias(app.getBuzzAlias());
                    if(code!=null){
                        buzzCode = code.getCode();
                    }
                }
                appJson.put(BUZZ,buzzCode);
                String roles = buildRoles(app.getAlias(), userAppRoles);
                if(StringUtil.isValid(roles)){
                    //查询所有权限并去重
                    MultiCondition con = new MultiCondition();
                    Condition con1 = new Condition();
                    con1.setConditionFieldName("alias");
                    con1.setCt(CT.IN.getName());
                    con1.setConditionFieldValue(roles);
                    con.addCondition(con1);
                    List<AppRole> appRoles = tyDriver.getAppRoleManager().list(con);
                    List<Page> pages = checkSuperRole(app.getAlias(), appRoles);
                    appJson.put(PAGES, pages);
                }
                apps.add(appJson);
            }
            logger.info("login user permitions json=" + JSONArray.toJSONString(apps));

            //将资源信息存到缓存中
            session.setAttribute(SessionUtils.E_SESSION_USER_OPEN_APP_RESOURCE_KEY, JSONArray.toJSONString(apps));

        } catch (Exception e) {
            logger.error("login user permitions error," + e.getMessage());
            logger.error("login user permitions error," + e);
            e.printStackTrace();
            tyResponse.setCode(500);
            tyResponse.setMessage(e.getMessage());
            tyResponse.setOk(false);
        }
        return tyResponse;
    }

    @RequestMapping(value = "/ty/openApps")
    public
    @ResponseBody
    Response openApps(HttpServletRequest request, HttpServletResponse response) {
        Response tyResponse = new Response();
        if(_checkLogin(request)){
            tyResponse.setOk(false);
            tyResponse.setCode(400);
            return  tyResponse;
        }
        String apps = SessionUtils.getStringValue(request, SessionUtils.E_SESSION_USER_OPEN_APP_RESOURCE_KEY);
        if(StringUtil.isValid(apps)){
            JSONArray appArray = JSONArray.parseArray(apps);
            for(int i=0;i<appArray.size();i++){
                JSONObject appObj = appArray.getJSONObject(i);
                if(appObj.containsKey(PAGES)){
                    //不需要返回这么多，pages去除
                    appObj.remove(PAGES);
                }
            }
            tyResponse.addData("apps",appArray);
        }
        return tyResponse;
    }

    @RequestMapping(value = "/ty/resources/{appAlias}")
    public
    @ResponseBody
    Response resources(HttpServletRequest request,@PathVariable String appAlias) throws LegoException {
        Response tyResponse = new Response();
        if(_checkLogin(request)){
            tyResponse.setOk(false);
            tyResponse.setCode(400);
            return  tyResponse;
        }
        if(StringUtil.isInvalid(appAlias)){
            throw new LegoException("参数无效",600001);
        }
        String apps = SessionUtils.getStringValue(request, SessionUtils.E_SESSION_USER_OPEN_APP_RESOURCE_KEY);
        JSONArray pages = new JSONArray();
        String buzz = "";
        if(StringUtil.isValid(apps)){
            JSONArray appArray = JSONArray.parseArray(apps);
            for(int i=0;i<appArray.size();i++){
                JSONObject appObj = appArray.getJSONObject(i);
                if(appAlias.equalsIgnoreCase(appObj.getString("alias"))){
                    pages=appObj.getJSONArray(PAGES);
                    buzz = appObj.getString(BUZZ);
                    break;
                }
            }
        }
        tyResponse.addData(PAGES, pages);
        tyResponse.addData(BUZZ, buzz);//返回buzz
        return tyResponse;
    }

    /**
     * 检查超级角色,并返回资源列表
     *
     * @param appAlias
     * @param appRoles
     * @return
     * @throws DBException
     */
    private List<Page> checkSuperRole(String appAlias, List<AppRole> appRoles) throws DBException {
        boolean isSuper = false;
        StringBuilder pageAlias = new StringBuilder();
        int k = 0;
        for (AppRole ar : appRoles) {
            if (ar.isSuperFlag()) {
                isSuper = true;
            }
            if (StringUtil.isValid(ar.getResources())) {
                if (k > 0) {
                    pageAlias.append(",");
                }
                pageAlias.append(ar.getResources());
                k++;
            }
        }
        if (StringUtil.isInvalid(pageAlias.toString())) {
            //如果没有权限就写一个不存在的资源别名
            pageAlias.append("9999999");
        }

        Condition con2 = new Condition();
        con2.setConditionFieldName("enable");
        con2.setConditionFieldValue("1");

        if (isSuper) {
            MultiCondition con = new MultiCondition();
            Condition con1 = new Condition();
            con1.setConditionFieldName("appAlias");
            con1.setConditionFieldValue(appAlias);
            con.addCondition(con1);
            con.addCondition(con2);
            return tyDriver.getPageManager().list(con,"sort",true);
        } else {
            //不是超级角色
            MultiCondition con = new MultiCondition();
            Condition con1 = new Condition();
            con1.setConditionFieldName("alias");
            con1.setCt(CT.IN.getName());
            con1.setConditionFieldValue(pageAlias.toString());
            con.addCondition(con1);
            con.addCondition(con2);
            return tyDriver.getPageManager().list(con,"sort",true);
        }
    }

    /**
     * 构建角色列表  去重
     *
     * @param appAlias
     * @param userAppRoles
     * @return
     */
    private String buildRoles(String appAlias, List<UserAppRole> userAppRoles) {
        StringBuilder result = new StringBuilder();
        int k = 0;
        for (UserAppRole uar : userAppRoles) {
            if (k > 0) {
                result.append(",");
            }
            if (appAlias.equalsIgnoreCase(uar.getAppAlias()) && StringUtil.isValid(uar.getRoles())) {
                result.append(uar.getRoles());
                k++;
            }
        }
        StringBuilder newResult = new StringBuilder();
        String[] allRoles = result.toString().split(",");
        Map<String, String> tmpMap = new HashMap<>();
        int m = 0;
        for (int i = 0; i < allRoles.length; i++) {
            String role = allRoles[i];
            if (tmpMap.containsKey(role)) {
                continue;
            } else {
                if (m > 0) {
                    newResult.append(",");
                }
                tmpMap.put(role, "" + i);
                newResult.append(role);
                m++;
            }
        }
        return newResult.toString();
    }

    /**
     * 判断是否登录
     * @param request
     * @return
     */
    private boolean _checkLogin(HttpServletRequest request){
        HttpSession session = request.getSession();
        return session != null || session.getAttribute(SessionUtils.TY_SESSION_CONFIG_USER) != null;
    }
}
