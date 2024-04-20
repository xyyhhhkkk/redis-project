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
