# Migrate `kokoro`

Status: **new**.

Replaces concatenative clip TTS and xAI Ara for spoken files.

Old engine: `~/.ollama/disabled/clip-tts`.

Do not restore clip stitch as the default generator.

Locked speakers: Ava=`af_heart`, Bruce=`am_echo`, Carly=`af_nova`.
Skip WAV when `speakers.is_live` is false.
