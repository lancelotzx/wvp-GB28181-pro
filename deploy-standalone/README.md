# wvp-GB28181-pro standalone docker 部署方案

本方案将 wvp 后端 jar 和前端 Vue dist 打包成单一 linux/amd64 镜像, 在服务器上以 docker compose + host network 运行, 通过 volume 外挂 yml 配置, 不依赖服务器本地编译环境。

## 适用场景

- 本地 Mac(ARM64) 构建跨平台 x86_64 镜像
- 服务器(公安专网/生产环境)无 maven/node 编译环境
- ZLM/MySQL/Redis 已在服务器上以其他方式(host/docker)运行, wvp 只通过 127.0.0.1 访问

## 镜像特点

- 单镜像: `wvp-pro-standalone:<TAG>`
- 架构: `linux/amd64`
- 网络: `network_mode: host` (SIP/Web 端口直接复用宿主机网络栈)
- 配置: 宿主机 `/opt/wvp/conf/application-dev.yml` volume 挂载到容器 `/app/conf`, 不烧录进镜像
- 日志: 宿主机 `/opt/wvp/logs` volume 挂载到容器 `/app/logs`

## 前置条件

- Mac 端: Docker Desktop + buildx
- 服务器: Docker + docker compose
- 服务器已准备 `/opt/wvp/conf/application-dev.yml`, 且 `media.sdp_ip` 等关键字段已按生产环境配置

## 一键流程 (3 步)

### 1. 本地构建 + 导出

```bash
cd /Users/lancororo/programming/java/wvp-GB28181-pro
bash deploy-standalone/build-and-export.sh 20260625
```

产物: `/tmp/wvp-pro-standalone-20260625.tar.gz`

### 2. scp 上传到服务器

```bash
scp /tmp/wvp-pro-standalone-20260625.tar.gz <user>@<server>:/opt/wvp/
scp deploy-standalone/deploy-on-server.sh <user>@<server>:/opt/wvp/
scp deploy-standalone/docker-compose.yml <user>@<server>:/opt/wvp/
```

### 3. 服务器部署

```bash
ssh <user>@<server>
cd /opt/wvp
bash deploy-on-server.sh 20260625
```

## 服务器目录约定

```
/opt/wvp/
├── docker-compose.yml
├── deploy-on-server.sh
├── wvp-pro-standalone-20260625.tar.gz
├── conf/
│   └── application-dev.yml   # 生产配置, 由用户维护
└── logs/                     # 容器运行日志
```

## 环境变量

在 `docker-compose.yml` 中可覆盖:

- `WVP_TAG`: 镜像 tag, 默认 `latest`
- `WVP_CONF_DIR`: 配置目录, 默认 `./conf`
- `WVP_LOG_DIR`: 日志目录, 默认 `./logs`
- `JAVA_OPTS`: JVM 参数, 默认 `-Xms512m -Xmx2g -Dfile.encoding=UTF-8`
- `WVP_CONF`: 容器内 yml 路径, 默认 `/app/conf/application-dev.yml`

## 网络说明

- wvp 容器使用 `network_mode: host`, 因此 `127.0.0.1` 就是宿主机本机
- 若 ZLM 也在 host 网络 docker 中运行, 则 wvp 与 ZLM 通过 `127.0.0.1` 通信
- MySQL/Redis 同样走 `127.0.0.1`

## 回滚步骤

如果 docker 部署异常, 先停止容器, 再恢复旧 nohup 方式:

```bash
cd /opt/wvp
docker compose down
nohup java -jar <旧 jar 路径> --spring.config.location=/opt/wvp/conf/application-dev.yml > /opt/wvp/logs/nohup.out 2>&1 &
```

## 常见问题

### `exec format error`

镜像不是 amd64。重新在 Mac 上执行 `build-and-export.sh`, 确认脚本中使用了 `--platform=linux/amd64 --load`。

### 启动后 SIP 端口没起来

查看日志:

```bash
docker compose logs wvp
```

常见原因:

- `application-dev.yml` 未挂载成功 (路径错误/权限)
- MySQL/Redis 连接失败
- `media.sdp_ip` 或 sip.ip 配置不正确

### 前端 404 或只有占位页面

说明 static/ 没正确打进 jar。检查 Stage 1 输出路径 `/src/main/resources/static` 是否对齐 Stage 2 的 COPY 目标。正常产物 `index.html` 应该大于 10KB, 而不是 vanilla 的 "111"。

## 注意事项

- 本镜像不内置任何 yml, 启动必须挂载外部配置
- 不内置 MySQL/Redis/ZLM, 这些服务需另行部署
- 不要修改 `docker/` 下已有文件, 本方案独立在 `deploy-standalone/` 中
