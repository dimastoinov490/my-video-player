import json
import os
import sys

try:
    from llama_cpp import Llama
except ImportError as exc:
    print(f"[AI Engine] Модуль llama_cpp не установлен: {exc}")
    print("[AI Engine] Установите пакет: pip install llama-cpp-python")
    sys.exit(1)

# Читаем конфиг, созданный Kotlin-приложением при импорте
config_path = "/app_data/config.json"
context_size = 2048
n_threads = 4

if os.path.exists(config_path):
    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)
        model_path = config.get("active_model_path") or "/app_data/models/default.gguf"
        context_size = int(config.get("context_size", 2048))
        n_threads = int(config.get("n_threads", 4))
else:
    model_path = "/app_data/models/default.gguf"  # Запасной вариант

if not os.path.exists(model_path):
    print(f"[AI Engine] Файл модели не найден: {model_path}")
    print("[AI Engine] Импортируйте .gguf модель через кнопку в приложении.")
    sys.exit(1)

# Инициализация движка llama.cpp с импортированной моделью
llm = Llama(
    model_path=model_path,
    n_ctx=context_size,
    n_threads=n_threads,
    verbose=False,
)

print(f"[AI Engine] Успешно подключена модель из пути: {model_path}")
