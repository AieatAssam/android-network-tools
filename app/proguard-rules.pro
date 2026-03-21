# Add project specific ProGuard rules here.

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
