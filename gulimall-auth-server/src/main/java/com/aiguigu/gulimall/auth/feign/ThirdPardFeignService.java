package com.aiguigu.gulimall.auth.feign;

import com.atguigu.gulimall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("gulimall-third-party")
public interface ThirdPardFeignService {

    @GetMapping("/sms/sendcode")
    public R sendCode(@RequestParam("mobile") String mobile, @RequestParam("code") String code);
}
