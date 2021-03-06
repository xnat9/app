### 介绍
轻量级java应用框架模板. 基于 [enet](https://gitee.com/xnat/enet)

系统只一个公用线程池: 所有的执行都被抽象成Runnable加入到公用线程池中执行

所以系统性能只由线程池大小属性 sys.exec.corePoolSize=4, 和 jvm内存参数 -Xmx512m 控制

框架由一个AppContext容器装载用到的所有服务类,由事件中心(ep)串联所有服务类

服务基础类: ServerTpl, 中的方法 async(() -> ....) 自动把任务添加到公用线程池中执行

### 系统事件
* sys.inited:  应用始化完成(环境配置, 事件中心, 系统线程池)
* sys.starting: 通知所有服务启动. 一般为ServerTpl
* sys.started: 应用启动完成
* sys.stopping: 应用停止事件(kill pid)

### 安装教程
```
<dependency>
    <groupId>cn.xnatural.app</groupId>
    <artifactId>app</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 可搭配其它服务
[http](https://gitee.com/xnat/http), [jpa](https://gitee.com/xnat/jpa),
[sched](https://gitee.com/xnat/sched), [remoter](https://gitee.com/xnat/remoter)

### 初始化
```
AppContext app = new AppContext(); // 创建一个应用
app.addSource(new ServerTpl("server1") { // 添加服务 server1
    @EL(name = "sys.starting")
    void start() {
        log.info("{} start", name);
    }
});
app.addSource(TestService()); // 添加自定义service
app.start();
```

#### 添加http服务
web.hp=:8080
```
app.addSource(new ServerTpl("web") { //添加http服务
    HttpServer server;
    @EL(name = "sys.starting", async = true)
    void start() {
        server = new HttpServer(app().attrs(name), exec());
        server.buildChain(chain -> {
            chain.get("get", hCtx -> {
                hCtx.render("xxxxxxxxxxxx");
            });
        }).start();
    }
    @EL(name = "sys.stopping")
    void stop() {
        if (server != null) server.stop();
    }
});
```

#### 添加jpa
jpa_local.url=jdbc:mysql://localhost:3306/test?useSSL=false&user=root&password=root
```
app.addSource(new ServerTpl("jpa_local") { //数据库 jpa_local
    Repo repo;
    @EL(name = "sys.starting", async = true)
    void start() {
        repo = new Repo(attrs()).init();
        exposeBean(repo);
        ep.fire(name + ".started");
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { if (repo != null) repo.close(); }
});
```

#### 动态添加服务
```
@EL(name = "sys.inited")
void sysInited() {
    if (!app.attrs("redis").isEmpty()) { //根据配置是否有redis,创建redis客户端工具
        app.addSource(new RedisClient())
    }
}
```

#### bean注入
```
app.addSource(new ServerTpl() {
    @Named ServerTpl server1; //自动注入, 按类型和名字
    @Inject Repo repo;  //自动注入, 按类型

    @EL(name = "sys.starting")
    void start() {
        log.info("{} ========= {}", name, server1.getName());
    }
});
```

#### 动态bean获取
```
app.addSource(new ServerTpl() {
    @EL(name = "sys.started")
    void start() {
        log.info(bean(Repo).firstRow("select count(1) as total from test").get("total").toString());
    }
});
```

#### 环境配置
* 系统属性(-Dconfigdir): configdir 指定配置文件路径. 默认:类路径
* 系统属性(-Dconfigname): configname 指定配置文件名. 默认:app
* 系统属性(-Dprofile): profile 指定启用特定的配置
* 只读取properties文件. 按顺序读取app.properties, app-[profile].properties 两个配置文件
* 配置文件支持简单的 ${} 属性替换
* 系统属性: System.getProperties() 优先级最高

#### 对列执行器(Devourer)
当需要按顺序控制任务 一个一个, 两个两个... 的执行时

服务基础类(ServerTpl)提供方法: queue

```
// 初始化一个 save 对列执行器
queue("save")
    .failMaxKeep(10000) // 最多保留失败的任务个数, 默认不保留
    .parallel(2) // 最多同时执行任务数, 默认1(one-by-one)
    .errorHandle {ex, devourer ->
        // 当任务执行抛错时执行
    };
```
```
// 添加任务执行, 方法1
queue("save", () -> {
    // 执行任务
});
// 添加任务执行, 方法2
queue("save").offer(() -> {
    // 执行任务
});
```
```
// 暂停执行(下一个版本1.0.3), 一般用于发生错误时
// 注: 必须有新的任务入对, 重新触发继续执行
queue("save")
    .errorHandle {ex, devourer ->
        // 发生错误时, 让对列暂停执行(不影响新任务入对)
        devourer.suspend(Duration.ofSeconds(180));
    };
```


#### http客户端
```
// get
Utils.http().get("http://xnatural.cn:9090/test/cus?p2=2").param("p1", 1).debug().execute();
```
```
// post
Utils.http().post("http://xnatural.cn:9090/test/cus").debug().execute();
```
```
// post 表单
Utils.http().post("http://xnatural.cn:9090/test/form").param("p1", "p1").debug().execute();
```
```
// post 文件
Utils.http().post("http://xnatural.cn:9090/test/upload")
    .param("file", new File("d:/tmp/1.txt"))
    .debug().execute();
```
```
// post json
Utils.http().post("http://xnatural.cn:9090/test/json")
    .jsonBody(new JSONObject().fluentPut("p1", 1).toString())
    .debug().execute();
```
```
// post 文本
Utils.http().post("http://xnatural.cn:9090/test/string").debug()
        .textBody("xxxxxxxxxxxxxxxx")
        .execute();
```

#### Map构建器
```
// 把bean转换成map
Utils.toMapper(bean).build()
// 添加属性
Utils.toMapper(bean).add(属性名, 属性值).build()
// 忽略属性
Utils.toMapper(bean).ignore(属性名).build()
// 转换属性
Utils.toMapper(bean).addConverter(属性名, Function<原属性值, 转换后的属性值>).build()
// 忽略null属性
Utils.toMapper(bean).ignoreNull().build()
// 属性更名
Utils.toMapper(bean).aliasProp(原属性名, 新属性名).build()
// 排序map
Utils.toMapper(bean).sort().build()
// 显示class属性
Utils.toMapper(bean).showClassProp().build()
```

#### 应用例子
[AppTest](https://gitee.com/xnat/app/blob/master/src/test/main/java/AppTest.java)

[rule](https://gitee.com/xnat/rule)


#### 参与贡献

xnatural@msn.cn
