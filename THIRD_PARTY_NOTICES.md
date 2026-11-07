# 第三方材料与许可说明

项目原创代码采用根目录 LICENSE 中的 GNU AGPL v3，SPDX 标识为 `AGPL-3.0-only`。原有第三方版权和许可声明继续适用。

## Maven Wrapper

`backend/mvnw`、`backend/mvnw.cmd` 来自 Apache Maven Wrapper，文件中的 Apache Software Foundation 声明保留，适用 Apache License 2.0。许可副本见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)，上游声明见 [LICENSES/maven-wrapper-NOTICE.txt](LICENSES/maven-wrapper-NOTICE.txt)。

## 依赖和独立服务

前端与后端依赖分别由 `frontend/package-lock.json`、`backend/pom.xml` 声明，使用各自的上游许可证。依赖安装目录及构建产物不随本源码仓库提交。go-judge、GCC/G++、MySQL/MariaDB 等由运行环境单独安装，本仓库保留调用或部署配置。

## Tino 素材

`frontend/src/assets/tino-spritesheet.png` 为用户提供的既有角色素材，本次建仓未重新制作。项目 AGPL 代码许可不构成对第三方角色、美术或商标权利的重新授权。当前素材来源与接入记录见 [作答布局交接](docs/implementation/D/WORKSPACE_AI.md)；仓库未附独立的素材再分发许可证明。

## PTA 蓝本

`docs/implementation/C/PTA_*` 保存公开来源目录、链接及项目整理的蓝本，保留作者、单位和来源信息。原题及第三方材料的权利仍归其权利人；项目代码许可证不对原题重新授权。`examples/pta/function-insert.json` 是本项目独立改编的示例，不能冒充 PTA 官方答案或隐藏用例。

Windows 安装时从 Go 模块下载 go-judge v1.14.0（MIT，Copyright © 2019 Yang Gao），在临时副本中接入本项目的 AppContainer 适配。源码副本包含上游 LICENSE，编译后随程序保留该许可。项目专用工具链和可执行文件在安装时下载或构建。
