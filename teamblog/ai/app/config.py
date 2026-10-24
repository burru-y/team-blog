"""配置：全部来自环境变量，缺失时给出安全默认值。

为什么 provider 默认是 mock：没有 API key 时，代码要能跑、SSE 链路要能验。
真模型的差异被隔离在 provider.py 里——换 provider 不改任何调用方代码。
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_", env_file=".env", extra="ignore")

    # mock | openai | deepseek   —— 都走 OpenAI 兼容协议，只是 base_url 不同
    provider: str = "mock"
    api_key: str = ""
    base_url: str = "https://api.deepseek.com/v1"
    model: str = "deepseek-flash"

    # 只读查询 v_projects_public；AI 服务不碰业务表
    database_url: str = "postgresql://blog:blog_dev_pw@db:5432/blog"

    # 成本熔断：每人每天的问句上限（按 IP + 日期）
    daily_limit: int = 20
    # 单次会话最多轮数（前端一次对话算一轮）
    max_question_chars: int = 500

    # 内部端点鉴权：/v1/summarize 只允许 Spring 带 HMAC 调用
    internal_hmac_secret: str = "dev-internal-secret"

    # 一键关停（S-12）：置 false 时两个端点都返回 503
    enabled: bool = True

    request_timeout_seconds: float = 20.0


@lru_cache
def get_settings() -> Settings:
    return Settings()
