# Paho loads its network modules via ServiceLoader and reads NLS resource bundles by name.
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# Workers are instantiated reflectively by WorkManager.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
