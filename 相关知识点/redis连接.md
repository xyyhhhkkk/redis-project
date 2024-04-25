## 关于本机连redis的具体操作

#### 下载homebrew，通过homebrew安装redis
#### 首先输入 source ~/.zprofile 使得homebrew生效
#### 具体连接步骤——
- 第一步查看redis是不是已启动 ： 
  - ps -ef | grep redis
- 第二步启动：
  - 启动Redis服务：brew services start redis
  - 重启Redis服务：brew services restart redis
- 第三步关闭：
  - 关闭Redis服务：brew services stop redis
- 第四步开启客户端
  - redis-cli连上客户端
  - auth "xyywanan123"
- 第五步使用可视化工具
  - 在github上下载[AnotherRedisDesktopManager](https://gitee.com/qishibo/AnotherRedisDesktopManager/releases)redis可视化工具
  - 查看本机ip地址：ifconfig en0

## 关于使用nginx前端的具体操作

- 第一步查看nginx是否启动
  - ps -ef | grep nginx
- 第二步重启nginx
  - brew services restart nginx

## 关于使用jmeter多并发测试的具体操作

#### 通过homebrew安装jmeter非常简便
#### 直接在terminal里输入jmeter就启动jmeter
#### jmeter汉化方法

- 在Jmeter的安装目录下的bin目录中找到 jmeter.properties这个文件，用文本编辑器打开。 

- 大概在37行，找到：#language=en 

- 将其修改为：language=zh_CN