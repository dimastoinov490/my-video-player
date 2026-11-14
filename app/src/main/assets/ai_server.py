import http.server
import json
import os
from llama_cpp import Llama

PORT = 8080
CONFIG_PATH = "/app_data/config.json"
WEB_ROOT = os.path.realpath("/app_data/web")

# Ленивая загрузка модели (чтобы не занимать RAM до первого запроса)
_llm_instance = None
_loaded_model_path = None


def get_ai_model():
    global _llm_instance, _loaded_model_path

    # Читаем конфиг из Kotlin-приложения
    if not os.path.exists(CONFIG_PATH):
        return None

    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        config = json.load(f)

    if not config.get("ai_assistant_enabled", False):
        return None

    model_path = config.get("active_model_path", "/app_data/models/qwen1.5b.gguf")
    context_size = int(config.get("context_size", 2048))
    n_threads = int(config.get("n_threads", 4))

    if _llm_instance is not None and _loaded_model_path == model_path:
        return _llm_instance

    if not model_path or not os.path.exists(model_path):
        print(f"[AI] Файл модели не найден: {model_path}")
        return None

    print("[AI] Загрузка микромодели в память...")
    _llm_instance = Llama(
        model_path=model_path,
        n_ctx=context_size,
        n_threads=n_threads,
        verbose=False,
    )
    _loaded_model_path = model_path
    return _llm_instance


def _strip_markdown_fences(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        if lines:
            lines = lines[1:]
        if lines and lines[-1].strip().startswith("```"):
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()
    return cleaned


class AIHandler(http.server.BaseHTTPRequestHandler):
    def _send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _send_json(self, status_code: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._send_cors_headers()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_POST(self):
        if self.path != "/api/fix-ui":
            self._send_json(404, {"error": "Endpoint not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            post_data = self.read_json(content_length)
        except Exception:
            self._send_json(400, {"error": "Invalid JSON body"})
            return

        file_rel_path = str(post_data.get("file", "index.html")).lstrip("/\\")
        user_instruction = str(post_data.get("prompt", ""))

        full_file_path = os.path.realpath(os.path.join(WEB_ROOT, file_rel_path))
        if full_file_path != WEB_ROOT and not full_file_path.startswith(WEB_ROOT + os.sep):
            self._send_json(403, {"error": "Access outside /app_data/web is forbidden"})
            return

        if not os.path.exists(full_file_path) or not os.path.isfile(full_file_path):
            self._send_json(404, {"error": "File not found"})
            return

        llm = get_ai_model()
        if not llm:
            self._send_json(403, {"error": "AI Assistant is disabled in settings or model missing"})
            return

        # Читаем исходный код файла
        with open(full_file_path, "r", encoding="utf-8") as f:
            source_code = f.read()

        # Промпт для микромодели
        prompt = f"""System: Ты эксперт по веб-верстке. Исправь код по инструкции пользователя. Верни ТОЛЬКО готовый исправленный код файла, без лишних пояснений и markdown-оберток.

Инструкция: {user_instruction}

Исходный код:
{source_code}

Исправленный код:"""

        print(f"[AI] Обработка задачи: {user_instruction}")
        response = llm(prompt, max_tokens=1500, temperature=0.1)
        fixed_code = _strip_markdown_fences(response["choices"][0]["text"])

        if fixed_code:
            # Перезаписываем файл на лету
            with open(full_file_path, "w", encoding="utf-8") as f:
                f.write(fixed_code)

        self._send_json(200, {"status": "success", "message": "File updated"})

    def read_json(self, length: int):
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8"))


if __name__ == "__main__":
    os.makedirs(WEB_ROOT, exist_ok=True)
    server = http.server.HTTPServer(("127.0.0.1", PORT), AIHandler)
    print(f"[AI Server] Запущен на порту {PORT}")
    server.serve_forever()
