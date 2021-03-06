package cn.byteorchid.magnetv.controller;

import cn.byteorchid.magnetv.response.BaseResponse;
import cn.byteorchid.magnetv.response.MagnetPageConfig;
import cn.byteorchid.magnetv.service.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Field;

import javax.servlet.http.HttpServletResponse;

import cn.byteorchid.magnetv.config.ApplicationConfig;
import cn.byteorchid.magnetv.handler.PermissionHandler;

/**
 * created 2019/5/24 17:13
 */
@Controller
@RequestMapping("admin")
public class AdminController {
    @Autowired
    PermissionService permissionService;

    @Autowired
    MagnetRuleService ruleService;

    @Autowired
    MagnetService magnetService;

    @Autowired
    ReportService reportService;

    @Autowired
    ApplicationConfig config;

    Gson gson = new Gson();

    @RequestMapping(method = RequestMethod.GET)
    public String index(HttpServletResponse response, Model model, @RequestParam(value = "p") String password) throws Exception {
        BaseResponse permission = permissionService.runAsPermission(password, null, null);
        if (permission.isSuccess()) {
            model.addAttribute("config", gson.toJson(new MagnetPageConfig(config)));
            return "admin";
        } else {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("text/html;charset=utf-8");
            response.getWriter().write(permission.getMessage());
            return null;
        }
    }

    @ResponseBody
    @RequestMapping(value = "config")
    public BaseResponse config(@RequestParam(value = "p") String password) throws Exception {
        return permissionService.runAsPermission(password, null, new PermissionHandler() {
            @Override
            public Object onPermissionGranted() throws Exception {
                JsonObject newConfig = new JsonObject();
                Field[] fields = ApplicationConfig.class.getFields();
                for (Field field : fields) {
                    Object value = ApplicationConfig.class.getField(field.getName()).get(config);
                    newConfig.addProperty(field.getName(), value.toString());
                }
                return newConfig;
            }
        });
    }

    /**
     * ????????????
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "reload", method = RequestMethod.GET)
    public BaseResponse reload(@RequestParam(value = "p") String password) throws Exception {
        return permissionService.runAsPermission(password, "??????????????????", new PermissionHandler<Void>() {
            @Override
            public Void onPermissionGranted() {
                ruleService.reload();
                return null;
            }
        });
    }

    /**
     * ????????????
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "clear-cache", method = RequestMethod.GET)
    public BaseResponse clearCache(@RequestParam(value = "p") String password) throws Exception {
        return permissionService.runAsPermission(password, "??????????????????", new PermissionHandler<Void>() {
            @Override
            public Void onPermissionGranted() {
                magnetService.clearCache();
                return null;
            }
        });
    }

    /**
     * ??????????????????
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "report-reload", method = RequestMethod.GET)
    public BaseResponse reportReload(@RequestParam(value = "p") String password) throws Exception {
        BaseResponse permission = permissionService.runAsPermission(password, "????????????????????????", null);
        if (permission.isSuccess()) {
            reportService.reload();
        }
        return permission;
    }

    /**
     * ??????????????????
     *
     * @return
     * @throws Exception
     */
    @ResponseBody
    @RequestMapping(value = "report-delete", method = RequestMethod.DELETE)
    public BaseResponse reportDelete(@RequestParam(value = "p") String password, @RequestParam final String value) throws Exception {
        BaseResponse response = permissionService.runAsPermission(password, "????????????", null);
        if (response.isSuccess()) {
            reportService.deleteReport(value);
        }
        return response;
    }

}
