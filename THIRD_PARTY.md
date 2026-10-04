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

# Sugestões offline V1

O motor é Kotlin local, sem biblioteca adicional. Ele carrega somente o arquivo
do idioma ativo, faz busca de prefixo sobre formas alfabéticas e usa distância
de edição limitada a dois passos quando faltam candidatos de prefixo. As flags
de afixo do formato Hunspell são removidas durante a preparação; as formas
explicitamente presentes permanecem. Não há expansão morfológica automática.

Os dados vêm dos arquivos Hunspell distribuídos em
[`wooorm/dictionaries`](https://github.com/wooorm/dictionaries), derivados dos
projetos de dicionários que os mantêm. O texto original da licença de cada
conjunto acompanha o APK em `app/src/main/assets/licenses`:

| Idioma | Origem e arquivo | Licença dos dados |
|---|---|---|
| pt-BR | VERO, `dictionaries/pt/index.dic` | LGPL-3.0 e MPL; texto da licença preservado |
| en | SCOWL/en_US, `dictionaries/en/index.dic` | avisos BSD e de outros componentes de origem preservados; arquivo contém os termos completos |
| es | LibreOffice es_ES, `dictionaries/es/index.dic` | escolha MPL-1.1-or-later da licença tri-licenciada GPL-3.0-or-later/LGPL-3.0-or-later/MPL-1.1-or-later |

O código do mecanismo é original deste projeto; nenhum código GPL foi adicionado.
Os dados são distintos do código: pt-BR pode ser redistribuído sob LGPL/MPL,
es foi selecionado sob MPL, e os avisos completos de SCOWL para inglês foram
incluídos para preservar as atribuições e termos próprios da lista composta.

Os arquivos processados ocupam aproximadamente 4,16 MB antes da compressão do
APK (pt-BR 3,09 MB, en 480 KB, es 590 KB). Só a lista do idioma atual fica em
memória; a carga é preguiçosa e trocar idioma descarta a referência ao conjunto
anterior depois de terminar consultas já iniciadas. A lista inclui as formas
ortográficas armazenadas como entradas na fonte; o motor não deriva outras
formas a partir das regras de afixo Hunspell.

