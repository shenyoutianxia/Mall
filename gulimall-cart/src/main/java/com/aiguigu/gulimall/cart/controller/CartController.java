package com.aiguigu.gulimall.cart.controller;

import com.aiguigu.gulimall.cart.service.CartService;
import com.aiguigu.gulimall.cart.vo.CartItemVo;
import com.aiguigu.gulimall.cart.vo.CartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.concurrent.ExecutionException;

@Controller
public class CartController {

    @Autowired
    CartService cartService;


    @GetMapping("/deleteItem")
    public String deleteItem(@RequestParam("skuId") Long skuId){

        cartService.deleteItem(skuId);
        return "redirect:http://cart.gulimall.com/cart.html";
    }


    @GetMapping("/countItem")
    public String countItem(@RequestParam("skuId") Long skuId,@RequestParam("num") Integer num){

        cartService.changeItemCount(skuId,num);

        return "redirect:http://cart.gulimall.com/cart.html";
    }


    @GetMapping("/checkItem")
    public String checkItem(@RequestParam("skuId") Long skuId,@RequestParam("checked") Integer check){

        cartService.checkItem(skuId,check);

        return "redirect:http://cart.gulimall.com/cart.html";
    }


    /**
     * 浏览器有一个cookie；user-key：标识用户身份，一个月后过期；
     * 如果第一次使用jd的购物车功能，都会给一个临时的用户身份；
     * 浏览器以后保存的，每次访问都会带上这个cookie；
     *
     * 登录：session有
     * 没登录：按照cookie里面的user-key
     * 第一次：没有临时用户，帮忙创建一个临时用户（采用拦截器）
     * @param model
     * @return
     *
     * ThreadLocal-同一线程共享数据
     */
    @GetMapping("/cart.html")
    public String cartListPage(Model model) throws ExecutionException, InterruptedException {

        //1.快速得到用户的信息id,user-key
        //UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();

        CartVo cartVo = cartService.getCart();
        model.addAttribute("cart",cartVo);
        return "cartList";
    }

    /**
     * 添加商品到购物车
     *
     * RedirectAttributes  方法作用
     * redirectAttributes.addFlashAttribute();将数据放在session里面可以在页面取出，但是只能去一次
     * redirectAttributes.addAttribute();将数据放在url后面
     * @return
     */
    @GetMapping("/addCartItem")
    public String addToCart(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num, RedirectAttributes redirectAttributes) throws ExecutionException, InterruptedException {

        cartService.addToCart(skuId,num);
        redirectAttributes.addAttribute("skuId",skuId);

        return "redirect:http://cart.gulimall.com/addToCartSuccess.html";
    }

    /**
     * 跳转到成功页
     * @param skuId
     * @param model
     * @return
     */
    //防止重刷效果
    @GetMapping("/addToCartSuccess.html")
    public String addToCartSuccessPage(@RequestParam("skuId") Long skuId, Model model){

        //重定向到成功页面。再次查询购物车
        CartItemVo cartItem = cartService.getCartItem(skuId);
        model.addAttribute("cartItem",cartItem);

        return "success";
    }
}
