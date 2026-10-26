# Pauta — transcrição de voz 100% local (Fase 0: protótipo mínimo)

> **Download direto do APK:** [última release](https://github.com/mtsbatalha/pauta-android/releases/latest) · [v0.1.0 (debug, arm64-v8a)](https://github.com/mtsbatalha/pauta-android/releases/download/v0.1.0/pauta-v0.1.0-debug.apk)

App Android nativo (Kotlin + Jetpack Compose) que grava áudio pelo microfone e
transcreve **inteiramente no aparelho**, sem enviar nada para a nuvem.

- **ASR:** [Parakeet TDT 0.6B v3 (int8)](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)
  (NVIDIA, CC-BY-4.0) — 25 idiomas europeus, incluindo português, com detecção
  automática de idioma, pontuação e capitalização nativas.
- **Runtime:** [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) v1.13.8
  (Apache-2.0) — ONNX Runtime em CPU, com AAR pré-compilado.
- **VAD:** Silero VAD (distribuição oficial do sherpa-onnx) — segmenta a fala
  para transcrever ao vivo durante a gravação.

Este é o **protótipo da Fase 0** do plano de implementação: gravar → ver texto
ao vivo → medir desempenho (RTF). Persistência, importação de arquivos,
segundo plano robusto e aprimoramento opcional com Qwen vêm nas próximas fases.

## Requisitos

- Android Studio (qualquer versão recente) **ou** apenas JDK 17 + Android SDK
  (plataforma 36 + Build Tools) para compilar pela linha de comando.
- Um aparelho **arm64-v8a** (qualquer smartphone 64 bits atual) com **Android 8+
  (API 26)** e pelo menos **~2 GB de RAM livre** e **~2 GB de armazenamento**
  para os modelos.
- Conexão com a internet **apenas na primeira execução**, para baixar os
  modelos oficiais (com verificação SHA-256). Depois disso, o app funciona
  100% offline.

## Compilar

```bash
# na pasta do projeto
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

As bibliotecas nativas (`app/src/main/jniLibs/arm64-v8a/*.so`) já estão no
projeto. Para atualizá-las (opcional):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-native.ps1
```

## Instalar e executar

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

1. Abra o **Pauta** e conceda a permissão de microfone quando solicitado.
2. Na primeira tela, toque em **“Baixar e instalar modelos”** (Wi-Fi
   recomendado; ~487 MB de download, ~1,5 GB de espaço usado no total).
3. Após a instalação, toque em **Gravar**, fale (qualquer um dos 25 idiomas
   suportados) e veja o texto aparecer enquanto você fala.
4. Toque em **Parar**. A tela final mostra o texto, o caminho do áudio salvo
   (WAV) e o **benchmark local**: RTF médio, tempo de áudio, tempo de
   decodificação e número de segmentos.

**Validação da Fase 0 (checklist):**

- [ ] Gravar ~30 s em português e conferir se o texto sai pontuado e
      capitalizado.
- [ ] Conferir o **RTF médio** no cartão de estatísticas: valores < 1,0
      indicam transcrição mais rápida que o tempo real (bom para transcrição
      ao vivo). Registre o valor do seu aparelho — é o go/no-go de performance.
- [ ] Girar a tela durante a gravação: a gravação e o texto devem continuar.
- [ ] Repetir em outro idioma (ex.: inglês ou espanhol) para exercitar a
      detecção automática de idioma.
- [ ] Ativar o modo avião e gravar de novo: tudo deve funcionar offline.

## Testes

```bash
# Unitários (JVM): RtfTracker, WavWriter, TarBz2Extractor
./gradlew :app:testDebugUnitTest

# Instrumentado (requer aparelho/emulador conectado com os modelos instalados;
# é pulado automaticamente se os modelos não estiverem presentes)
./gradlew :app:connectedDebugAndroidTest
```

## Arquitetura (Fase 0)

```
ui/            Compose: tela de preparo dos modelos + gravador (ao vivo)
MainViewModel  Estados (StateFlow): Idle → LoadingModel → Recording → Finished
recorder/      RecordingController: captura + VAD + decodificação em paralelo
asr/           AsrEngine: sherpa-onnx (Parakeet int8) + Silero VAD, thread dedicada
audio/         AudioRecorder (AudioRecord 16 kHz mono) e WavWriter (WAV PCM16)
model/         ModelManager: download com SHA-256 + extração do tarball
benchmark/     RtfTracker: RTF = tempo de decodificação / tempo de áudio
```

Fluxo: `AudioRecord (100 ms) → WAV + VAD → segmento de fala → canal →
Parakeet (thread nativa) → texto ao vivo`. A captura nunca é bloqueada pela
decodificação; os dois rodam em coroutines separadas.

## Permissões

| Permissão | Motivo |
|---|---|
| `RECORD_AUDIO` | Capturar o microfone (núcleo do app). |
| `INTERNET` | **Apenas** para baixar os modelos oficiais no primeiro uso, com consentimento e verificação de checksum. Nenhuma outra chamada de rede existe. |

Sem permissões de armazenamento: modelos e gravações ficam no armazenamento
interno do app (`filesDir`), e `allowBackup=false` impede que transcrições
sejam levadas por backups de nuvem.

## Privacidade

Áudio e transcrições nunca saem do aparelho. Não há telemetria, contas nem
serviços remotos. O código-fonte e as dependências são abertos (licenças
abaixo).

## Limitações conhecidas da Fase 0

- A gravação está vinculada à tela: se o app for para segundo plano com a tela
  apagada por muito tempo, o sistema pode interrompê-la (o foreground service
  com tipo `microphone` chega na Fase 2).
- O texto ao vivo aparece por segmentos de fala (pausa de ~0,5 s fecha um
  segmento) — não palavra a palavra.
- Sem persistência em banco ainda (Fase 1): o áudio WAV fica em
  `filesDir/recordings/` e o texto da sessão é perdido ao iniciar nova gravação.
- Idiomas: 25 europeus (não cobre zh/ja/ko/ar etc.).
- O Parakeet v3 foi treinado majoritariamente com português europeu; em pt-BR
  o WER benchmark é bom (~4,8% no FLEURS), mas valide com o seu próprio áudio.

## Licenças e atribuições

- **sherpa-onnx** (runtime ASR + bindings Kotlin em
  `app/src/main/java/com/k2fsa/sherpa/onnx/`) — Apache-2.0, © Xiaomi
  Corporation / k2-fsa. Arquivos copiados sem modificação da tag v1.13.8.
- **Parakeet TDT 0.6B v3** (modelo) — CC-BY-4.0, © NVIDIA.
- **Silero VAD** (modelo) — distribuído pelo projeto sherpa-onnx (MIT).
- **Apache Commons Compress** (extração do tarball) — Apache-2.0.
- Este projeto (código do app) — use como quiser; sem garantias.
