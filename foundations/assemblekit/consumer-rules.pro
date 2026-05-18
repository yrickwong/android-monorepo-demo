# Proguard rules consumed by anyone depending on :foundations:assemblekit.
# Mavericks ViewModels are reflectively instantiated, keep their constructors.
-keepclassmembers class * extends com.airbnb.mvrx.MavericksViewModel {
    public <init>(...);
}
-keepclassmembers class * implements com.airbnb.mvrx.MavericksState {
    <init>(...);
}
