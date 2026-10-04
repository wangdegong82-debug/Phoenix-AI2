from __future__ import annotations

import json
import os
from openai import AsyncOpenAI


SYSTEM_PROMPT = """你是 Phoenix AI 2.2 的比赛研究助手“二号凤凰”。

职责：
1. 解释比赛数据、模型概率、比分路径和尾部情景；
2. 明确区分“已取得的数据”和“推断”；
3. 数据不足时明确指出缺失项；
4. 支持赛后复盘，找出方向、净胜球、比分分布和数据源的问题；
5. 不得伪造首发、伤停、赔率、赛果或新闻；
6. 不得声称保证盈利、稳赚或保证命中。

回答优先给结论，再给理由，并提醒关键不确定性。
"""


class PhoenixChat:
    async def reply(self, message: str, context: dict | None = None) -> str:
        key = os.getenv("OPENAI_API_KEY", "").strip()
        if not key:
            return (
                "二号凤凰接口已经连通，但服务器尚未配置 OPENAI_API_KEY。"
                "比赛数据和 Phoenix 本地概率引擎仍可使用；配置服务器密钥后即可启用完整对话。"
            )

        client = AsyncOpenAI(api_key=key)
        model = os.getenv("OPENAI_MODEL", "gpt-5.6")
        ctx = json.dumps(context or {}, ensure_ascii=False)
        response = await client.responses.create(
            model=model,
            instructions=SYSTEM_PROMPT,
            input=f"Phoenix上下文：{ctx}\n\n用户：{message}",
        )
        return response.output_text


phoenix_chat = PhoenixChat()
