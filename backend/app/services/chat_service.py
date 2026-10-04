from __future__ import annotations

import json
import os
from openai import AsyncOpenAI


SYSTEM_PROMPT = """你是 Phoenix AI 2.0 的比赛研究助手“二号凤凰”。
解释数据、指出不确定性、区分主路径与冷门尾部路径，并做赛后复盘。
不得声称保证盈利或保证命中。数据不足时必须说明，不能伪造首发、伤停、赔率或赛果。
"""


class PhoenixChat:
    async def reply(self, message: str, context: dict | None = None) -> str:
        key = os.getenv("OPENAI_API_KEY", "")
        if not key:
            return (
                "二号凤凰已启动，但服务器尚未配置 OPENAI_API_KEY。"
                "当前可以先使用本地预测引擎；配置密钥后启用完整对话分析。"
            )

        client = AsyncOpenAI(api_key=key)
        model = os.getenv("OPENAI_MODEL", "gpt-5.6")
        ctx = json.dumps(context or {}, ensure_ascii=False)
        response = await client.responses.create(
            model=model,
            instructions=SYSTEM_PROMPT,
            input=f"上下文：{ctx}\n\n用户：{message}",
        )
        return response.output_text


phoenix_chat = PhoenixChat()
