
## 项目名
电商商城

## 项目介绍

1.  该项目实现了手机号验证注册（采用阿里云发送短信）及登录功能.
2.  商品上架功能和搜索功能（ElasticSearch检索）.
3.  购物车功能（临时购物车、用户购物车）.

## 使用技术
1.  前后端分离.
2.  基于微服务开发、Spring Boot、Spring Cloud、redis等技术...
3.  通过Nginx反向代理网关，通过网关路由到每个微服务模块业务上.
4.  商品页面展示使用redis进行缓存(也可使用Spring Cache)、分布式锁（redisson）、线程池的异步编排.
5.  分布式session域数据共享（Spring Session扩大session域的范围）.

# 页面展示效果


#### Gitee 特征

1.  You can use Readme\_XXX.md to support different languages, such as Readme\_en.md, Readme\_zh.md
2.  Gitee blog [blog.gitee.com](https://blog.gitee.com)
3.  Explore open source project [https://gitee.com/explore](https://gitee.com/explore)
4.  The most valuable open source project [GVP](https://gitee.com/gvp)
5.  The manual of Gitee [https://gitee.com/help](https://gitee.com/help)
6.  The most popular members  [https://gitee.com/gitee-stars/](https://gitee.com/gitee-stars/)
