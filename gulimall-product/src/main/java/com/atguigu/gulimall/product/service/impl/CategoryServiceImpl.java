package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.common.utils.PageUtils;
import com.atguigu.gulimall.common.utils.Query;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalogs2Vo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.cache.annotation.CachePut;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {

        //1.查出所有分类

        List<CategoryEntity> entities = this.baseMapper.selectList(null);
        //2.组装成父子的树形结构
        //2.1找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter((categoryEntity) -> {
            return categoryEntity.getParentCid() == 0;
        }).map((menu)->{
            menu.setChildren(getChildrens(menu,entities));
            return menu;
        }).sorted((menu1,menu2)->{
            return (menu1.getSort()==null?0:menu1.getSort())-(menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());

        return level1Menus;
    }

    @Override
    public void removeMenusByIds(List<Long> asList) {
        //1.删除前检查当前的菜单是否被其他地方引用
        this.baseMapper.deleteBatchIds(asList);
    }

    @Override
    public Long[] findCategoryPath(Long catelogId) {

        List<Long> Paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, Paths);
        Collections.reverse(parentPath);
        return (Long[]) parentPath.toArray(new Long[parentPath.size()]) ;
    }

    /**
     * 级联更新所有关联的数据
     * @CacheEvict:失效模式  --清除缓存
     * @param category
     */

    /**
     *  使用Spring-cache的不足
     *  1）、读模式
     *      缓存穿透：查询一个null数据。    解决：缓存空数据 -- cache-null-values = true
     *      缓存击穿：大量并发进来同时访问一个正好过期的数据。   解决： 加锁 ？ （默认是无加锁的）  sync = true (加锁解决缓存击穿)
     *      缓存雪崩： 大量的key同时过期。   解决： 加随机时间。  加上过期时间。
     *  2）、写模式  （缓存与数据库一致）
     *       读写加锁。
     *       引入中间件（Canal）,感知Mysql的更新去更新数据库
     *       读多写多直接去数据库查询
     *
     * @param category
     */

    //@CachePut  双写模式
//    @Caching(evict = {
//            @CacheEvict(value = "category",key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category",key = "'getCategoryJson'")
//    })
    @CacheEvict(value = "category",allEntries = true)  //失效模式
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        //更新自己
        this.updateById(category);
        //更新其所关联的表
        categoryBrandRelationService.updateCascade(category.getCatId(),category.getName());

    }
    /**
     *CacheAutoConfiguration -> RedisCacheConfiguration -> 自动配置了RedisCacheManager -> 初始化缓存 ->
     * 如有redisCacheConfiguration有配置就用已有的，没有则使用默认配置 -> 想改缓存的配置，只需要给容器中放一个RedisCacheConfiguration
     * 就会用到当前RedisCacheManager管理的所有缓存分区中
     * @return
     */
    //每一个需要缓存的数据都需要指定放到哪个名字的缓存【缓存的分区（按照业务类型分）】
    @Cacheable(value = {"category"},key = "#root.methodName",sync = true)  //代表当前方法的结果需要缓存，如果缓存中有，方法不用调用。如果缓存中没有，会调用方法，最后将方法的结果放入缓存
    @Override
    public List<CategoryEntity> getLevel1Categorys() {

        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));

        return categoryEntities;
    }

    @Cacheable(value = "category",key = "#root.methodName")
    @Override
    public Map<String, List<Catalogs2Vo>> getCategoryJson(){
        /**
         * 优化三级分类
         * 1.将数据库的多次查询变为一次
         */
        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);
        //1、查询所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(categoryEntities,0L);
        //2、封装数据
        Map<String, List<Catalogs2Vo>> level1CategoryMap = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个的一级分类,查到这个一级分类的二级分类
            List<CategoryEntity> level2Categorys = getParent_cid(categoryEntities,v.getCatId());
            //2.封装上面的结果 categoryEntities
            List<Catalogs2Vo> catalogs2Vos = null;
            if (level2Categorys != null) {
                catalogs2Vos = level2Categorys.stream().map(l2 -> {
                    Catalogs2Vo catalogs2Vo = new Catalogs2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //1.找到当前2级分类的3级分类进行封装
                    List<CategoryEntity> level3Categorys = getParent_cid(categoryEntities,l2.getCatId());
                    if (level3Categorys!=null){
                        //2.封装成指定格式
                        List<Catalogs2Vo.Category3Vo> category3Vos = level3Categorys.stream().map(l3 -> {
                            Catalogs2Vo.Category3Vo category3Vo = new Catalogs2Vo.Category3Vo(l2.getCatId().toString(),l3.getCatId().toString(),l3.getName());
                            return category3Vo;
                        }).collect(Collectors.toList());
                        catalogs2Vo.setCatalog3List(category3Vos);
                    }
                    return catalogs2Vo;
                }).collect(Collectors.toList());
            }
            return catalogs2Vos;
        }));

        return level1CategoryMap;
    }



    public Map<String, List<Catalogs2Vo>> getCategoryJson2(){
        //给缓存中放json字符串，拿出的json字符串，还用逆转为能用的对象类型  【序列化与反序列化】
        /**
         * 高并发缓存产生的问题
         * 1. 空结果缓存   --解决缓存穿透问题
         * 2. 设置过期时间（加随机值） --解决缓存雪崩问题
         * 3. 加锁 --解决缓穿透问题
         */

        //1.入缓存逻辑，缓存中存的数据是json字符串
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)){
            //2.缓存中没有数据，从数据库中查询数据
            Map<String, List<Catalogs2Vo>> categoryJsonFromDb = getCategoryJsonFromDbWithRedissonLock();

            return categoryJsonFromDb;
        }
        Map<String, List<Catalogs2Vo>> result = JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catalogs2Vo>>>(){});
        return result;
    }
    /**
     * 缓存里面的数据如何和数据库的数据保持一致
     * 1）、双写模式
     * 2）、失效模式
     * @return
     */
    //从数据库查询并封装分类数据    -- 采用分布式锁
    public Map<String, List<Catalogs2Vo>> getCategoryJsonFromDbWithRedissonLock() {

        //占分布式锁  锁的名字要一致
        RLock lock = redissonClient.getLock("CategoryJson-lock");
        lock.lock();
        //加锁成功  --执行业务
        Map<String, List<Catalogs2Vo>> dataFromDb = null;
        try{
            dataFromDb = getDataFromDb();
        }finally {
            lock.unlock();
        }

        return dataFromDb;
    }

    private Map<String, List<Catalogs2Vo>> getDataFromDb() {
        //如果缓存中有就用缓存的
        String catalogJSON = redisTemplate.opsForValue().get("catalogJSON");
        if (!StringUtils.isEmpty(catalogJSON)){
            Map<String, List<Catalogs2Vo>> result = JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catalogs2Vo>>>(){});
            return result;
        }
        /**
         * 优化三级分类
         * 1.将数据库的多次查询变为一次
         */

        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);

        //1、查询所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(categoryEntities,0L);

        //2、封装数据
        Map<String, List<Catalogs2Vo>> level1CategoryMap = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个的一级分类,查到这个一级分类的二级分类
            List<CategoryEntity> level2Categorys = getParent_cid(categoryEntities,v.getCatId());
            //2.封装上面的结果 categoryEntities
            List<Catalogs2Vo> catalogs2Vos = null;
            if (level2Categorys != null) {
                catalogs2Vos = level2Categorys.stream().map(l2 -> {
                    Catalogs2Vo catalogs2Vo = new Catalogs2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //1.找到当前2级分类的3级分类进行封装
                    List<CategoryEntity> level3Categorys = getParent_cid(categoryEntities,l2.getCatId());
                    if (level3Categorys!=null){
                        //2.封装成指定格式
                        List<Catalogs2Vo.Category3Vo> category3Vos = level3Categorys.stream().map(l3 -> {
                            Catalogs2Vo.Category3Vo category3Vo = new Catalogs2Vo.Category3Vo(l2.getCatId().toString(),l3.getCatId().toString(),l3.getName());
                            return category3Vo;
                        }).collect(Collectors.toList());
                        catalogs2Vo.setCatalog3List(category3Vos);
                    }
                    return catalogs2Vo;
                }).collect(Collectors.toList());
            }
            return catalogs2Vos;
        }));

        //查到的数据在放入缓存中,将对象转为json放在缓存中
        String s = JSON.toJSONString(level1CategoryMap);
        redisTemplate.opsForValue().set("catalogJSON",s,1, TimeUnit.DAYS);

        return level1CategoryMap;
    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> categoryEntities, Long parentCid) {

        List<CategoryEntity> entityList = categoryEntities.stream().filter(item -> item.getParentCid() == parentCid).collect(Collectors.toList());
        //    return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
        return entityList;
    }

    private List<Long> findParentPath(Long catelogId,List<Long> Paths){
        //1.收集当前节点id
        Paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        //判断当前三级分类是否有父级
        if (byId.getParentCid()!=0){
            findParentPath(byId.getParentCid(),Paths);
        }
        return Paths;
    }

    //递归查找所有菜单的子菜单
    private List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all){
        List<CategoryEntity> children = all.stream().filter((categoryEntity) -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map((categoryEntity)->{
            //找子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity,all));
            return categoryEntity;
        }).sorted((menu1,menu2)->{
            //进行排序
            return (menu1.getSort()==null?0:menu1.getSort())-(menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());
        return children;
    }

}
