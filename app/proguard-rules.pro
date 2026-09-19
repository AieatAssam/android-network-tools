# Add project specific ProGuard rules here.

# P02: SNMP4J discovers protocol and transport implementations reflectively;
# keep the security model and its protocol classes through R8. The JNI engine
# also registers callbacks/native methods by name.
-keep class org.snmp4j.** { *; }
-dontwarn org.snmp4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-keep class me.impa.icmpenguin.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# dnsjava: Windows-specific JNA classes not present on Android
-dontwarn com.sun.jna.**
-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.sun.jna.ptr.**
-dontwarn com.sun.jna.win32.**

# dnsjava: JNDI classes not present on Android
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**

# dnsjava: Lombok compile-time annotations not present at runtime
-dontwarn lombok.**

# dnsjava: SLF4J static binder not used on Android (logback/slf4j-android handles this)
-dontwarn org.slf4j.impl.StaticLoggerBinder
