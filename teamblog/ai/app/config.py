"""配置：全部来自环境变量，缺失时给出安全默认值。

为什么 provider 默认是 mock：没有 API key 时，代码要能跑、SSE 链路要能验。
真模型的差异被隔离在 provider.py 里——换 provider 不改任何调用方代码。

为什么分匿名/成员两档：访客只是"看看"，随便问几句就够；
成员天天用，值得给更强的模型和更多额度。分档也让成本可控——
被刷的时候，损失上限是匿名档的额度。
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_", env_file=".env", extra="ignore")

    # mock | openai | deepseek   —— 都走 OpenAI 兼容协议，只是 base_url 不同
    provider: str = "mock"
    api_key: str = ""
    base_url: str = "https://api.deepseek.com/v1"

    # 与 Spring 共享的 JWT 密钥：只用来验签、读出 sub，不做任何用户认证
    jwt_secret: str = ""

    # 匿名访客：便宜模型 + 少额度 + 短回答
    anon_model: str = "deepseek-flash"
    anon_daily_limit: int = 8
    anon_max_answer_chars: int = 300
    anon_max_tokens: int = 600

    # 登录成员：额度不限（<=0 表示不限），模型与访客同为 flash（速度优先、成本低）
    # 想要更强模型：改成 deepseek-v4-pro，并把 member_max_tokens 提到 2000+
    member_model: str = "deepseek-flash"
    member_daily_limit: int = 0
    member_max_answer_chars: int = 800
    # 成员档用的是思考模式模型：推理本身要烧 token，
    # 预算给太小会出现"一个字都没输出"（实测 I3 偶发空回答）
    member_max_tokens: int = 1500

    # 思考模式：默认开启（effort=high），会让首字延迟从 1s 涨到 9s。
    # 我们做的是"只依据资料回答"的检索式问答，思考带来的增益不明显，所以默认关掉。
    # 想要更高质量：改成 enabled，并把 max_tokens 提到 2000 以上。
    thinking: str = "disabled"

    # 项目资料少于这个长度时直接给固定提示、不调模型：
    # 资料是占位内容时，模型只能反复说"资料里没写"，既费 token 又显得蠢
    min_usable_content_chars: int = 20

    # 只读查询 v_projects_public；AI 服务不碰业务表
    database_url: str = "postgresql://blog:blog_dev_pw@db:5432/blog"

    max_question_chars: int = 500

    # 内部端点鉴权：/v1/summarize 只允许 Spring 带 HMAC 调用
    internal_hmac_secret: str = "dev-internal-secret"

    # 一键关停（S-12）：置 false 时两个端点都返回 503
    enabled: bool = True

    request_timeout_seconds: float = 20.0


@lru_cache
def get_settings() -> Settings:
    return Settings()
