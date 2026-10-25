"""\n配置管理\n统一从项目根目录的 .env 文件加载配置\n"""

import os
import secrets
import warnings
from dotenv import load_dotenv

# 加载项目根目录的 .env 文件
# 路径: MiroFish/.env (相对于 backend/app/config.py)
project_root_env = os.path.join(os.path.dirname(__file__), '../../.env')

if os.path.exists(project_root_env):
    load_dotenv(project_root_env, override=True)
else:
    # 如果根目录没有 .env，尝试加载环境变量（用于生产环境）
    load_dotenv(override=True)

# Local, git-ignored overrides (real keys and machine-specific URLs) win over .env.
project_root_env_local = os.path.join(os.path.dirname(__file__), '../../.env.local')
if os.path.exists(project_root_env_local):
    load_dotenv(project_root_env_local, override=True)


class Config:
    """Flask配置类"""
    
    # Flask配置
    SECRET_KEY = os.environ.get('SECRET_KEY', 'mirofish-secret-key')
    DEBUG = os.environ.get('FLASK_DEBUG', 'False').lower() == 'true'
    
    # JSON配置 - 禁用ASCII转义，让中文直接显示
    JSON_AS_ASCII = False
    
    # ============ FREYA 编排层配置 ============
    # 助手推理模型（DeepSeek，OpenAI 兼容格式）
    DEEPSEEK_API_KEY = os.environ.get('DEEPSEEK_API_KEY')
    DEEPSEEK_BASE_URL = os.environ.get('DEEPSEEK_BASE_URL', 'https://api.deepseek.com/v1')
    DEEPSEEK_MODEL = os.environ.get('DEEPSEEK_MODEL', 'deepseek-chat')
    
    # JWT 认证
    # 密钥来源：FREYA_JWT_SECRET > SECRET_KEY > 每进程随机值（仅开发/测试兜底）。
    # 随机值避免可预测的硬编码密钥，但 token 不跨重启存活；生产必须显式设置。
    _JWT_SECRET_EXPLICIT = bool(
        os.environ.get('FREYA_JWT_SECRET') or os.environ.get('SECRET_KEY')
    )
    FREYA_JWT_SECRET = (
        os.environ.get('FREYA_JWT_SECRET')
        or os.environ.get('SECRET_KEY')
        or secrets.token_hex(32)
    )
    FREYA_JWT_TTL_SECONDS = int(os.environ.get('FREYA_JWT_TTL_SECONDS', '604800'))
    
    # FREYA SQLite 数据库（认证/会话/记忆/任务/通知）
    FREYA_DB_PATH = os.environ.get(
        'FREYA_DB_PATH',
        os.path.join(os.path.dirname(__file__), '../data/freya.sqlite3'),
    )
    
    # 每用户聊天速率限制（每分钟）
    FREYA_RATE_LIMIT_PER_MINUTE = int(os.environ.get('FREYA_RATE_LIMIT_PER_MINUTE', '20'))
    
    # 现有引擎接口是否强制 JWT（§10）。
    # 默认关闭以保持与既有 MiroFish 引擎测试/前端的向后兼容；
    # 生产环境应设置为 "true" 启用按用户隔离。
    FREYA_REQUIRE_AUTH = os.environ.get('FREYA_REQUIRE_AUTH', 'false').lower() == 'true'
    
    # ============ Voicebox (local voice engine for FREYA's spoken replies) ============
    # Base URL of a Voicebox server you run (https://github.com/jamiepine/voicebox),
    # e.g. http://127.0.0.1:17493. Empty = disabled; the app then uses the phone's voice.
    VOICEBOX_URL = os.environ.get('VOICEBOX_URL', '').strip()
    # Voice profile name or id inside Voicebox (empty = Voicebox's default voice).
    VOICEBOX_VOICE = os.environ.get('VOICEBOX_VOICE', '').strip()
    # Engine or OpenAI alias; "tts-1" means "whatever engine the profile uses".
    VOICEBOX_MODEL = os.environ.get('VOICEBOX_MODEL', 'tts-1').strip() or 'tts-1'
    VOICEBOX_TIMEOUT_SECONDS = float(os.environ.get('VOICEBOX_TIMEOUT_SECONDS', '45'))

    # LLM配置（统一使用OpenAI格式）
    LLM_API_KEY = os.environ.get('LLM_API_KEY')
    LLM_BASE_URL = os.environ.get('LLM_BASE_URL', 'https://api.openai.com/v1')
    LLM_MODEL_NAME = os.environ.get('LLM_MODEL_NAME', 'gpt-4o-mini')
    
    # Zep配置
    ZEP_API_KEY = os.environ.get('ZEP_API_KEY')
    
    # 文件上传配置
    MAX_CONTENT_LENGTH = 50 * 1024 * 1024  # 50MB
    UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), '../uploads')
    ALLOWED_EXTENSIONS = {'pdf', 'md', 'txt', 'markdown'}
    
    # 文本处理配置
    DEFAULT_CHUNK_SIZE = 500  # 默认切块大小
    DEFAULT_CHUNK_OVERLAP = 50  # 默认重叠大小
    
    # OASIS模拟配置
    OASIS_DEFAULT_MAX_ROUNDS = int(os.environ.get('OASIS_DEFAULT_MAX_ROUNDS', '10'))
    OASIS_SIMULATION_DATA_DIR = os.path.join(os.path.dirname(__file__), '../uploads/simulations')
    
    # OASIS平台可用动作配置
    OASIS_TWITTER_ACTIONS = [
        'CREATE_POST', 'LIKE_POST', 'REPOST', 'FOLLOW', 'DO_NOTHING', 'QUOTE_POST'
    ]
    OASIS_REDDIT_ACTIONS = [
        'LIKE_POST', 'DISLIKE_POST', 'CREATE_POST', 'CREATE_COMMENT',
        'LIKE_COMMENT', 'DISLIKE_COMMENT', 'SEARCH_POSTS', 'SEARCH_USER',
        'TREND', 'REFRESH', 'DO_NOTHING', 'FOLLOW', 'MUTE'
    ]
    
    # Report Agent配置
    REPORT_AGENT_MAX_TOOL_CALLS = int(os.environ.get('REPORT_AGENT_MAX_TOOL_CALLS', '5'))
    REPORT_AGENT_MAX_REFLECTION_ROUNDS = int(os.environ.get('REPORT_AGENT_MAX_REFLECTION_ROUNDS', '2'))
    REPORT_AGENT_TEMPERATURE = float(os.environ.get('REPORT_AGENT_TEMPERATURE', '0.5'))
    
    @classmethod
    def validate(cls) -> list[str]:
        """验证必要配置"""
        errors: list[str] = []
        if not cls.LLM_API_KEY:
            errors.append("LLM_API_KEY 未配置")
        if not cls.ZEP_API_KEY:
            errors.append("ZEP_API_KEY 未配置")
        if os.environ.get("ZEP_API_URL"):
            errors.append("ZEP_API_URL 不受支持；MiroFish 仅连接 Zep Cloud")
        if cls.DEBUG:
            warnings.warn("Flask DEBUG mode is enabled. Do not use in production.", RuntimeWarning)
        if not cls._JWT_SECRET_EXPLICIT:
            warnings.warn(
                "FREYA_JWT_SECRET is not set; using a per-process random secret. "
                "Set FREYA_JWT_SECRET (>= 32 bytes) in production so tokens survive restarts.",
                RuntimeWarning,
            )
        return errors
