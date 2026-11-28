# 项目自定义混淆规则。

# ---- Shizuku 相关 ----
# SwipeUserService 由 Shizuku 在独立的 shell 进程中通过反射创建实例，
# 必须保留类本身、类名与无参构造函数，否则 Shizuku 方式会绑定失败。
-keep class com.example.swipetool.SwipeUserService {
    public <init>();
}

# AIDL 接口跨进程使用（App 进程 <-> Shizuku UserService 进程），
# 保留接口与 Stub / Proxy 的类名，避免两端解析不一致。
-keep interface com.example.swipetool.ISwipeService { *; }
-keep class com.example.swipetool.ISwipeService$* { *; }
