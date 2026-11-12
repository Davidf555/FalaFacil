# Fala Fácil

Aplicativo Android nativo de comunicação por voz, feito em Kotlin e Jetpack Compose. Inclui teclado alfabético e numérico, sugestões offline, frases rápidas configuráveis e leitura em voz alta. As configurações e o histórico ficam no aparelho; o app funciona offline e não usa anúncios, analytics ou rastreamento.

## Gerar o APK

**Android Studio**

1. Abra a pasta `FalaFacil` no Android Studio e aguarde a sincronização do Gradle. Use JDK 17 e instale o Android SDK Platform 37 se solicitado.
2. Selecione **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
3. O APK estará em `app/build/outputs/apk/debug/app-debug.apk`.

**GitHub Actions**

Envie o projeto para o GitHub. O workflow **Android Debug APK** compila em cada push e pull request para `main` ou `master`, e também pode ser iniciado manualmente. Baixe `fala-facil-debug-apk` na seção **Artifacts** da execução.

## Instalar no celular

Transfira `app-debug.apk` para o celular e abra o arquivo. Quando solicitado, permita a instalação de aplicativos dessa fonte nas configurações do Android. A fala usa o mecanismo de texto para fala do aparelho; instale uma voz pt-BR nas configurações de saída de texto para fala. Prefira uma voz offline.
