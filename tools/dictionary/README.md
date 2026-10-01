# Dicionário local do Revisa

O APK inclui `app/src/main/assets/dictionaries/pt_br.rvd.gz`. Ele deriva do
dicionário VERO pt-BR do LibreOffice, na versão fixada em
`source/upstream-commit.txt`. Créditos, avisos e LGPLv3 acompanham o asset.
Distribua também esta pasta de fontes e conversão junto ao projeto.

Os arquivos originais em `tools/dictionary/source` não são empacotados pelo
Android. O usuário não precisa baixar arquivos após instalar o APK.

Para reproduzir e validar, na raiz do projeto:

```powershell
python tools/dictionary/build_dictionary.py
python tools/dictionary/test_builder.py
python tools/dictionary/validate_dictionary.py
```

Não há dependências Python adicionais nem acesso à rede nesses comandos.
O workflow Android usa o asset pronto; não precisa executar a conversão.

O conversor expande prefixos, sufixos, combinações permitidas e uma camada de
classes de continuação. Exporta palavras isoladas em minúsculas, até 32 letras;
nomes próprios e compostos com hífen ficam fora. Não substitui o motor completo
do Hunspell: suas regras de separação, compostos e sugestões ortográficas não
são executadas no Android.

O índice contém cerca de 2,71 milhões de formas, ocupa 3,81 MB comprimidos e
26,32 MB nas estruturas compactas carregadas, além da sobrecarga da aplicação.
Esses são tamanhos do índice, não uma medição do APK ou do processo inteiro.
Os valores e hashes exatos ficam em `manifest.json`.

A carga acontece em `Dispatchers.IO`; consultas em `Dispatchers.Default`.
Uma busca binária localiza o prefixo e examina no máximo 512 candidatos.
Formas verbais podem apontar para o gerúndio produzido pelas regras originais.
A ordenação é heurística: esta fonte não fornece frequência de uso nem modelo
de contexto. Não há aprendizado pessoal, rede ou substituição automática.

Somente a palavra extraída após a proteção de senha chega ao motor. Resultados
de consultas antigas são descartados quando o cursor, campo ou palavra muda.
