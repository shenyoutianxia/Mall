package com.aiguigu.gulimall.auth.controller;

import com.aiguigu.gulimall.auth.feign.MemberFeignService;
import com.aiguigu.gulimall.auth.feign.ThirdPardFeignService;
import com.aiguigu.gulimall.auth.vo.UserLoginVo;
import com.aiguigu.gulimall.auth.vo.UserRegistVo;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.common.constant.AuthServerConstant;
import com.atguigu.gulimall.common.exception.BizCodeEnum;
import com.atguigu.gulimall.common.utils.R;
import com.atguigu.gulimall.common.vo.MemberResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.atguigu.gulimall.common.constant.AuthServerConstant.LOGIN_USER;

@Controller
public class LoginController {

    @Autowired
    ThirdPardFeignService thirdPardFeignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    MemberFeignService memberFeignService;


    @GetMapping("/login.html")
    public String loginPage(HttpSession session){

        Object attribute = session.getAttribute(LOGIN_USER);
        if (attribute==null){
            //未登录
            return "login";
        }else {
            return "redirect:http://gulimall.com";
        }
    }


    @PostMapping("/login")
    public String login(UserLoginVo vo, RedirectAttributes redirectAttributes, HttpSession session){

        //远程登录
        R login = memberFeignService.login(vo);
        if (login.getCode() == 0){
            //成功
            MemberResponseVo data = login.getData("data", new TypeReference<MemberResponseVo>() {
            });
            session.setAttribute(LOGIN_USER, data);
            return "redirect:http://gulimall.com";
        }else {
            Map<String,String> errors = new HashMap<>();
            errors.put("msg",login.getData("msg",new TypeReference<String>(){}));
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }
//
//    @GetMapping("/reg.html")
//    public String regPage(){
//
//        return "reg";
//    }

    @ResponseBody
    @GetMapping("/sms/sendcode")
    public R sendCode(@RequestParam("mobile") String mobile){

        String redisCode = redisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + mobile);
        if (!StringUtils.isEmpty(redisCode)){
            //验证码存入时间
            long time = Long.parseLong(redisCode.split("_")[1]);
            if (System.currentTimeMillis() - time < 60000){
                //60时秒内不能再发
                return R.error(BizCodeEnum.SMS_CODE_EXCEPTION.getCode(),BizCodeEnum.SMS_CODE_EXCEPTION.getMsg());
            }
        }

        //1、TODO 接口防刷

        //2、验证码的再次检验   使用redis  存key-mobile,value-code  以及存入的时间
        String code = UUID.randomUUID().toString().substring(0, 4);
        String substring = code + "_" + System.currentTimeMillis();
        //redis缓存验证码，防止用以手机号在60秒内再次发送验证码

        redisTemplate.opsForValue().set(AuthServerConstant.SMS_CODE_CACHE_PREFIX+mobile,substring,10, TimeUnit.MINUTES);

        thirdPardFeignService.sendCode(mobile,code);
        return R.ok();
    }



    //TODO 如何解决分布式下的session问题
    @PostMapping("/register")
    public String register(@Valid UserRegistVo user, BindingResult result, RedirectAttributes redirectAttributes){
        if (result.hasErrors()){

            Map<String,String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));

            redirectAttributes.addFlashAttribute("errors",errors);
            //校验出错，重定向注册页
            return "redirect:http://auth.gulimall.com/reg.html";
        }

        //真正注册，调用远程服务进行注册
        //1.校验验证码
        String code = user.getCode();
        String s = redisTemplate.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + user.getMobile());
        if (!StringUtils.isEmpty(s)){

            if (code.equals(s.split("_")[0])){
                //删除验证码  令牌机制
                redisTemplate.delete(AuthServerConstant.SMS_CODE_CACHE_PREFIX + user.getMobile());
                //验证码通过
                R r = memberFeignService.register(user);
                if (r.getCode()==0){
                    //成功
                    return "redirect:http://auth.gulimall.com/login.html";
                }else {
                    Map<String,String> errors = new HashMap<>();
                    errors.put("msg",r.getData("msg",new TypeReference<String>(){}));
                    redirectAttributes.addFlashAttribute("errors",errors);
                    return "redirect:http://auth.gulimall.com/reg.html";
                }
            }else {
                Map<String,String> errors = new HashMap<>();
                errors.put("code","验证码错误");
                redirectAttributes.addFlashAttribute("errors",errors);
                //校验出错，重定向注册页
                return "redirect:http://auth.gulimall.com/reg.html";
            }
        }else {
            Map<String,String> errors = new HashMap<>();
            errors.put("code","验证码错误");
            redirectAttributes.addFlashAttribute("errors",errors);
            //校验出错，重定向注册页
            return "redirect:http://auth.gulimall.com/reg.html";
        }
    }
}
