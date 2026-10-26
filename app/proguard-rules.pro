# Bindings JNI do sherpa-onnx: classes e campos são acessados por nome a partir
# do código nativo (JNI). Renomear/remover quebraria a chamada nativa.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# commons-compress: mantém avisos de R8 silenciosos quanto a módulos opcionais.
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.codec.**
