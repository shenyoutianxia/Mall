package com.atguigu.guilimall.thirdparty.controller;


import com.atguigu.guilimall.thirdparty.component.SmsComponent;
import com.atguigu.gulimall.common.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/sms")
@RestController
public class SmsSendController {

    @Autowired
    SmsComponent smsComponent;

    /**
     * 提供给别的服务调用
     * @param mobile
     * @param code
     * @return
     */
    @GetMapping("/sendcode")
    public R sendCode(@RequestParam("mobile") String mobile,@RequestParam("code") String code){

        smsComponent.sendSmsCode(mobile,code);
        return R.ok();
    }
}
