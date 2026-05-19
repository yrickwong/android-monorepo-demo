# Proguard rules consumed by anyone depending on :foundations:assemblekit-compose.
# The base assemblekit module already keeps Mavericks state/VM constructors via
# its own consumer rules; nothing extra is needed for Compose itself because
# the Compose Gradle plugin ships its own keep rules with the runtime AAR.
