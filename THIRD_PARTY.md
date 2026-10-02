# Componentes do runtime

Os binários são subprocessos separados, não bibliotecas ligadas ao Kotlin.

| Componente | Versão/origem | Licença e preservação |
|---|---|---|
| Codex CLI | OpenAI, rust-v0.160.0, aarch64-unknown-linux-musl | Apache-2.0; LICENSE e NOTICE originais em assets/licenses |
| PRoot | termux/proot commit a179d3e8a4e045aaa1fb8cc3284f23509d96d353 | GPL-2.0; COPYING original e fonte correspondente incluídos na preparação |
| talloc estático dentro do PRoot | 2.1.14, compilado estaticamente pela receita deste projeto | LGPL-2.1-or-later; fonte, cabeçalho com copyright e licença completa incluídos |
| Alpine minirootfs ARM64 | 3.23.6 | Licenças por pacote; banco instalado preservado em Alpine-packages.txt |
| BusyBox no Alpine | versão extraída do banco APK | GPL-2.0; fonte correspondente e licença completa incluídos |
| musl | incluído no Alpine/Codex musl | MIT; COPYRIGHT preservado |

`tools/prepare_runtime.py` preserva licenças e arquivos de fontes dentro de
`app/src/main/assets/licenses`, sem retirar os arquivos presentes no Alpine.
As fontes de PRoot/talloc e seus scripts de build são incluídos. BusyBox tem
a fonte upstream preservada. Os demais pacotes e patches da distribuição são
identificados pelo banco APK e pelo repositório de build oficial:
https://gitlab.alpinelinux.org/alpine/aports/-/tree/3.23-stable.
Este pacote de projeto não contém um APK publicado nem redistribui o rootfs
Alpine ou o binário Codex; eles são obtidos pela etapa de preparação.
PRoot é recompilado pelo NDK 27.2.12479018. O script de build remove
o loader opcional para guest 32-bit e inclui string.h explicitamente em ashmem_memfd.c
para compilar com Clang; as fontes originais e a receita completa
(incluindo configuração talloc e probe) ficam em assets/licenses. Os checksums
das fontes e do executável compilado ficam em runtime/provenance.json.
Não redistribuímos binários do Acode nem copiamos seu editor, terminal visual,
Cordova ou servidor PTY. Usamos a mesma família upstream de PRoot e suas
opções de compatibilidade, com diretórios privados e binds reduzidos.
AndroidX, Kotlin e coroutines continuam sendo dependências Gradle da tela nativa.
