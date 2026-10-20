"""
请求级 BYOK 密钥注入助手。

引擎与适配器在构造客户端时通过本模块读取当前用户的密钥；
无请求上下文（CLI/后台线程）或无用户密钥时回退到全局 Config。
"""

from flask import g, has_app_context

from ...config import Config


def request_keys() -> dict:
    """返回当前请求用户的已解密密钥（无则空 dict）。"""
    if has_app_context():
        return getattr(g, "freya_keys", None) or {}
    return {}


def effective_llm():
    """返回 (api_key, base_url, model)：用户密钥优先，回退 Config。"""
    keys = request_keys()
    return (
        keys.get("llm_api_key") or Config.LLM_API_KEY,
        keys.get("llm_base_url") or Config.LLM_BASE_URL,
        keys.get("llm_model_name") or Config.LLM_MODEL_NAME,
    )


def effective_zep():
    """返回 zep_api_key：用户密钥优先，回退 Config。"""
    return request_keys().get("zep_api_key") or Config.ZEP_API_KEY


def effective_deepseek():
    """返回 deepseek_api_key：用户密钥优先，回退 Config。"""
    return request_keys().get("deepseek_api_key") or Config.DEEPSEEK_API_KEY
