# OverDev

Navegador flutuante para desenvolvedores, em Material 3.

- sobrepõe qualquer app (permissão de sobreposição)
- janela arrastável pelo header · maximizar/restaurar
- minimizar numa bolinha arrastável — toque nela restaura
- barra de endereço completa: voltar/avançar/recarregar, busca integrada
- favoritos + histórico com biblioteca
- console do javascript capturado ao vivo (extra para devs)
- user-agent customizado com presets · modo desktop · bloqueio de imagens
- forçar modo escuro (Android 13+) · opacidade/tamanho configuráveis
- downloads desativados por design

## Build

    gradle :app:assembleDebug

ou Actions → "build apk" → Run workflow (nunca roda em commit).

## Como usar

1. abra o app e conceda a permissão de sobreposição
2. "abrir navegador flutuante" — a janela aparece sobre tudo
3. arraste pelo header, minimize na bolinha, maximize, configure no ⚙

## Limitações honestas

- modo escuro forçado só em Android 13+ (API do WebView)
- teclado: abre ao tocar na barra de endereço, fecha ao tocar fora
- alguns sites exigem login de novo dentro do overlay (cookies próprios)
