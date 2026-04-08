#!/usr/bin/env python3
"""
TechProject 幫手 Bot
====================
Discord + HTTP API 雙介面的 RAG 問答機器人。
讀取 wiki markdown 知識庫，用 Google Gemini Flash 回答科技插件相關問題。
"""

import asyncio
import glob
import hashlib
import json
import logging
import os
import pathlib
import re
import time
from functools import partial
from typing import Optional

import discord
import google.generativeai as genai
from aiohttp import web
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# ─────────────────────────────────────────────
#  設定
# ─────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("tech-helper")

def load_env(path: str = ".env"):
    """簡易 .env 載入，不覆蓋已設定的環境變數。"""
    env_path = pathlib.Path(__file__).parent / path
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if key and key not in os.environ:
            os.environ[key] = value

load_env()

DISCORD_TOKEN = os.environ.get("DISCORD_TOKEN", "")
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
WIKI_PATH = os.environ.get("WIKI_PATH", "../../docs/wiki")
API_PORT = int(os.environ.get("API_PORT", "18923"))
COMMAND_PREFIX = os.environ.get("COMMAND_PREFIX", "!幫手")
COOLDOWN_SECONDS = int(os.environ.get("COOLDOWN_SECONDS", "15"))
MAX_RESPONSE_CHARS = int(os.environ.get("MAX_RESPONSE_CHARS", "1800"))

# ─────────────────────────────────────────────
#  知識庫
# ─────────────────────────────────────────────
class KnowledgeBase:
    """載入 wiki markdown 並提供 TF-IDF 檢索。"""

    def __init__(self, wiki_dir: str):
        self.wiki_dir = pathlib.Path(__file__).parent / wiki_dir
        self.chunks: list[dict] = []  # {"title": ..., "path": ..., "content": ...}
        self.vectorizer: Optional[TfidfVectorizer] = None
        self.tfidf_matrix = None
        self._load()

    def _load(self):
        """讀取所有 .md 檔案，按標題拆分成段落。"""
        md_files = sorted(glob.glob(str(self.wiki_dir / "**" / "*.md"), recursive=True))
        if not md_files:
            log.warning("知識庫目錄 %s 中沒有 .md 檔案！", self.wiki_dir)
            return

        for filepath in md_files:
            rel = pathlib.Path(filepath).relative_to(self.wiki_dir)
            try:
                text = pathlib.Path(filepath).read_text(encoding="utf-8")
            except Exception as e:
                log.warning("讀取 %s 失敗: %s", filepath, e)
                continue

            # 按 ## 標題拆分段落
            sections = re.split(r"(?=^#{1,3}\s)", text, flags=re.MULTILINE)
            file_title = rel.stem.replace("-", " ").replace("_", " ").title()

            for section in sections:
                section = section.strip()
                if len(section) < 20:
                    continue
                # 取段落標題
                first_line = section.split("\n", 1)[0]
                title = re.sub(r"^#{1,3}\s*", "", first_line).strip() or file_title
                self.chunks.append({
                    "title": title,
                    "path": str(rel),
                    "content": section[:3000],  # 截斷過長段落
                })

        if not self.chunks:
            log.warning("知識庫為空！")
            return

        # 建立 TF-IDF 索引
        corpus = [c["title"] + "\n" + c["content"] for c in self.chunks]
        self.vectorizer = TfidfVectorizer(
            analyzer="char_wb", ngram_range=(2, 4), max_features=20000
        )
        self.tfidf_matrix = self.vectorizer.fit_transform(corpus)
        log.info("知識庫載入完成：%d 個段落，來自 %d 個檔案", len(self.chunks), len(md_files))

    def search(self, query: str, top_k: int = 8) -> list[dict]:
        """檢索最相關的段落。"""
        if self.vectorizer is None or self.tfidf_matrix is None:
            return []
        q_vec = self.vectorizer.transform([query])
        scores = cosine_similarity(q_vec, self.tfidf_matrix).flatten()
        top_indices = scores.argsort()[-top_k:][::-1]
        results = []
        for idx in top_indices:
            if scores[idx] > 0.01:
                results.append({**self.chunks[idx], "score": float(scores[idx])})
        return results

    def reload(self):
        """重新載入知識庫。"""
        self.chunks.clear()
        self.vectorizer = None
        self.tfidf_matrix = None
        self._load()


# ─────────────────────────────────────────────
#  Gemini LLM
# ─────────────────────────────────────────────
SYSTEM_PROMPT = """你是「科技幫手」，一個專門回答 Minecraft TechProject 科技插件問題的 AI 助理。

規則：
1. **只回答與 TechProject 科技插件相關的問題**。與插件無關的問題請禮貌拒絕。
2. 根據提供的知識庫內容回答，不要編造不存在的功能或配方。
3. 回答要簡潔實用，用繁體中文。
4. 如果知識庫中找不到答案，誠實說「目前文件中沒有這方面的資訊」。
5. 可以引用具體的配方、材料、機器名稱來幫助玩家。
6. 不要在回答中提及「知識庫」、「文件」等後台概念，用「wiki」或「指南」代替。
7. 回答長度控制在合理範圍內，避免過長。
8. **更新日誌 / changelog 相關問題**：知識庫中包含完整的更新日誌，請根據其中記錄回答「最近更新了什麼」、「修復了什麼」、「新功能」等問題。"""


class GeminiHelper:
    def __init__(self, api_key: str):
        genai.configure(api_key=api_key)
        self.model_candidates = [
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash",
        ]
        # 不做初始化測試請求（避免消耗免費配額），直接使用第一個模型
        self.current_model_idx = 0
        self.current_model_name = self.model_candidates[0]
        self.model = genai.GenerativeModel(
            self.current_model_name,
            system_instruction=SYSTEM_PROMPT,
        )
        log.info("Gemini 模型初始化完成：%s", self.current_model_name)

    def _switch_model(self) -> bool:
        """遇到 429 / 404 時切換到下一個模型（不做測試請求）。"""
        for offset in range(1, len(self.model_candidates)):
            idx = (self.current_model_idx + offset) % len(self.model_candidates)
            candidate = self.model_candidates[idx]
            self.current_model_idx = idx
            self.current_model_name = candidate
            self.model = genai.GenerativeModel(candidate, system_instruction=SYSTEM_PROMPT)
            log.info("切換模型至：%s", candidate)
            return True
        return False

    async def ask(self, question: str, context_chunks: list[dict]) -> str:
        """組合知識庫上下文 + 問題，呼叫 Gemini 回答。"""
        context_parts = []
        for chunk in context_chunks:
            context_parts.append(f"【{chunk['title']}】({chunk['path']})\n{chunk['content']}")
        context_text = "\n\n---\n\n".join(context_parts) if context_parts else "（沒有找到相關文件）"

        prompt = f"""以下是知識庫中與問題相關的內容：

{context_text}

---

玩家問題：{question}

請根據上述知識庫內容回答。"""

        for attempt in range(len(self.model_candidates)):
            try:
                response = await asyncio.to_thread(
                    self.model.generate_content, prompt
                )
                text = response.text.strip()
                if len(text) > MAX_RESPONSE_CHARS:
                    text = text[:MAX_RESPONSE_CHARS] + "…（回答過長已截斷）"
                return text
            except Exception as e:
                err_str = str(e)
                log.error("Gemini API 錯誤 (%s, attempt %d): %s",
                          self.current_model_name, attempt + 1, err_str)
                if "429" in err_str or "quota" in err_str.lower() or "404" in err_str:
                    if attempt + 1 < len(self.model_candidates) and self._switch_model():
                        continue
                return "⚠ AI 繁忙中，請稍後再試。"


# ─────────────────────────────────────────────
#  冷卻控制
# ─────────────────────────────────────────────
cooldowns: dict[str, float] = {}

def check_cooldown(user_id: str) -> Optional[int]:
    """回傳剩餘冷卻秒數，None 表示可以使用。"""
    now = time.time()
    last = cooldowns.get(user_id, 0)
    remaining = COOLDOWN_SECONDS - (now - last)
    if remaining > 0:
        return int(remaining) + 1
    cooldowns[user_id] = now
    return None


# ─────────────────────────────────────────────
#  Discord Bot
# ─────────────────────────────────────────────
intents = discord.Intents.default()
intents.message_content = True
bot = discord.Client(intents=intents)

kb: Optional[KnowledgeBase] = None
llm: Optional[GeminiHelper] = None


@bot.event
async def on_ready():
    log.info("Discord Bot 已上線: %s (ID: %s)", bot.user.name, bot.user.id)


@bot.event
async def on_message(message: discord.Message):
    if message.author.bot:
        return

    content = message.content.strip()

    # !幫手 重新載入
    if content == f"{COMMAND_PREFIX} 重新載入" or content == f"{COMMAND_PREFIX} reload":
        kb.reload()
        await message.reply("✅ 知識庫已重新載入！", mention_author=False)
        return

    # !幫手 <問題>
    if not content.startswith(COMMAND_PREFIX):
        return

    question = content[len(COMMAND_PREFIX):].strip()
    if not question:
        await message.reply(
            f"📖 **科技幫手** — 使用方式：`{COMMAND_PREFIX} <你的問題>`\n"
            f"例如：`{COMMAND_PREFIX} 壓縮機怎麼用？`",
            mention_author=False,
        )
        return

    # 冷卻
    cd = check_cooldown(f"discord-{message.author.id}")
    if cd is not None:
        await message.reply(f"⏳ 冷卻中，請等 {cd} 秒後再問。", mention_author=False)
        return

    # 回答
    async with message.channel.typing():
        chunks = kb.search(question, top_k=8)
        answer = await llm.ask(question, chunks)

    await message.reply(answer, mention_author=False)


# ─────────────────────────────────────────────
#  HTTP API（供遊戲內 /幫手 指令使用）
# ─────────────────────────────────────────────
def _json_dumps_utf8(obj):
    return json.dumps(obj, ensure_ascii=False)


async def handle_ask(request: web.Request) -> web.Response:
    """POST /ask  body: {"question": "...", "player": "..."}"""
    try:
        data = await request.json()
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400, dumps=_json_dumps_utf8)

    question = (data.get("question") or "").strip()
    player = (data.get("player") or "unknown").strip()

    if not question:
        return web.json_response({"error": "empty question"}, status=400, dumps=_json_dumps_utf8)
    if len(question) > 500:
        return web.json_response({"error": "question too long"}, status=400, dumps=_json_dumps_utf8)

    # 冷卻
    cd = check_cooldown(f"mc-{player}")
    if cd is not None:
        return web.json_response({"answer": f"⏳ 冷卻中，請等 {cd} 秒後再問。", "cooldown": cd}, dumps=_json_dumps_utf8)

    chunks = kb.search(question, top_k=8)
    answer = await llm.ask(question, chunks)
    return web.json_response({"answer": answer}, dumps=_json_dumps_utf8)


async def handle_health(request: web.Request) -> web.Response:
    return web.json_response({"status": "ok", "chunks": len(kb.chunks) if kb else 0}, dumps=_json_dumps_utf8)


async def handle_reload(request: web.Request) -> web.Response:
    kb.reload()
    return web.json_response({"status": "reloaded", "chunks": len(kb.chunks)}, dumps=_json_dumps_utf8)


# ─────────────────────────────────────────────
#  主程式
# ─────────────────────────────────────────────
async def main():
    global kb, llm

    if not GEMINI_API_KEY or GEMINI_API_KEY.startswith("你的"):
        log.error("請在 .env 中設定 GEMINI_API_KEY！取得方式：https://aistudio.google.com/apikey")
        return

    # 初始化知識庫 & LLM
    kb = KnowledgeBase(WIKI_PATH)
    llm = GeminiHelper(GEMINI_API_KEY)

    # 啟動 HTTP API
    app = web.Application()
    app.router.add_post("/ask", handle_ask)
    app.router.add_get("/health", handle_health)
    app.router.add_post("/reload", handle_reload)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "127.0.0.1", API_PORT)
    await site.start()
    log.info("HTTP API 啟動於 http://127.0.0.1:%d", API_PORT)

    # 啟動 Discord Bot
    if DISCORD_TOKEN and not DISCORD_TOKEN.startswith("你的"):
        try:
            await bot.start(DISCORD_TOKEN)
        except discord.LoginFailure:
            log.error("Discord Token 無效！請檢查 .env 中的 DISCORD_TOKEN")
    else:
        log.warning("未設定 DISCORD_TOKEN，僅啟動 HTTP API 模式（遊戲內可用）")
        # 保持運行
        await asyncio.Event().wait()


if __name__ == "__main__":
    asyncio.run(main())
