# `prd-workbench` Hermes Profile

## 已核对版本

- Hermes Agent `v0.16.0`，upstream `6cb88a08`
- Python `3.11.15`
- 模型 `deepseek-v4.1-flash`，provider `newapi`（Hermes PRD Workbench contributors-NewAPI）
- Profile `prd-workbench`
- API Server `127.0.0.1:8643`

版本和命令已于 2026-08-18 使用本机 `hermes --version`、`hermes profile --help`、`hermes gateway --help` 和 Hermes 自带 API Server 文档核对。

## 创建 Profile

推荐创建无预装技能的新 Profile，避免复制 default 的记忆和技能：

```bash
hermes profile create prd-workbench --no-skills
```

随后在该 Profile 中配置已经验证的 DeepSeek provider/model，并通过 Hermes 自身的 Secret/认证机制注入凭据。不要把 default 的 `memories/`、session、state.db 或个人 SOUL 复制进来。

把本目录中的 `config.yaml` 和 `SOUL.md` 安装到新 Profile。安装前备份目标文件，不能修改 default Profile。

## 插件

S1 实现 `integrations/hermes-plugin/__init__.py` 后，将整个插件目录安装到 `prd-workbench` Profile 自己的 `plugins/prd-workbench/`，不得放入 default Profile。插件只注册：

- `list_sources`
- `read_source`
- `submit_result`

Hermes `plugins install` 当前只接收 Git 标识符；本地开发插件使用 Profile 私有插件目录，不要把仓库路径误当成该命令参数。

## API Server

Hermes v0.16.0 的 API Server 由 gateway 启动。Profile 私有运行配置至少包含：

```text
API_SERVER_ENABLED=true
API_SERVER_HOST=127.0.0.1
API_SERVER_PORT=8643
API_SERVER_MODEL_NAME=prd-workbench
API_SERVER_KEY=<受控注入>
```

启动与检查：

```bash
hermes -p prd-workbench gateway run
hermes -p prd-workbench tools list --platform api_server
hermes -p prd-workbench doctor
```

控制面使用官方 Runs API：`POST /v1/runs`、`GET /v1/runs/{id}`；健康和能力使用 `/health/detailed`、`/v1/capabilities`。所有请求通过 `Authorization: Bearer ...` 访问，浏览器不直连 Hermes，因此不配置 CORS。

## 工具检查

验收时确认 API Server 只能使用 `prd_workbench` 工具集。terminal、file、browser、web、memory、skills、delegation 等均保持禁用。Hermes v0.16.0 的后台 Curator 默认开启且可启动辅助 Agent 修改技能，因此本 Profile 还必须显式设置 `curator.enabled: false`；仅禁用 API Server 的 `skills` 工具集不能替代该配置。

Profile 隔离不是完整沙箱，所以当前只承载本地 Spike 测试材料，不承载生产或敏感材料。
