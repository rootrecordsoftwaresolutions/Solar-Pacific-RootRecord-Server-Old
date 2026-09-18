# One GGUF slot. While AVA Console is up, keep chat mapped (no 15m unload).
# Reports / vision / coder still send keep_alive=0. Do not drop Linux page cache.
# Vulkan = Radeon 840M. This does not put GGUF on the XDNA NPU.
export OLLAMA_VULKAN="${OLLAMA_VULKAN:-1}"
export OLLAMA_IGPU_ENABLE="${OLLAMA_IGPU_ENABLE:-1}"
export OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
export OLLAMA_KEEP_ALIVE="${OLLAMA_KEEP_ALIVE:--1}"
export AVA_OLLAMA_CHAT_KEEP_ALIVE="${AVA_OLLAMA_CHAT_KEEP_ALIVE:--1}"
export OLLAMA_MAX_LOADED_MODELS="${OLLAMA_MAX_LOADED_MODELS:-1}"
export OLLAMA_NUM_PARALLEL="${OLLAMA_NUM_PARALLEL:-1}"

ollama_warm_chat() {
  local host="${OLLAMA_HOST:-127.0.0.1:11434}"
  local model="${AVA_OLLAMA_MODEL:-llama3.2:3b-instruct-q4_K_M}"
  local keep="${AVA_OLLAMA_CHAT_KEEP_ALIVE:--1}"
  local flm="${AVA_FLM_URL:-http://127.0.0.1:52625}"
  # GGUF plus FastFlowLM OOMs 16 GB. Keep GGUF cold when NPU chat is the engine.
  case "${AVA_NPU_CHAT:-1}" in
    0|false|off|no) ;;
    *) return 0 ;;
  esac
  if curl -fsS -m 2 "${flm}/v1/models" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -m 180 -o /dev/null "http://${host}/api/generate" \
    -H "Content-Type: application/json" \
    -d "{\"model\":\"${model}\",\"keep_alive\":\"${keep}\"}" || true
}
