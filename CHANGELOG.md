## 0.0.2

* 新增开机自启 API：`enableBootAutoStart()` / `disableBootAutoStart()` / `isBootAutoStartEnabled()`，默认关闭，需显式开启。
* 新增覆盖安装后自启动：内置 `MY_PACKAGE_REPLACED` 接收器，应用被覆盖安装后无条件拉起 `:daemon` 保活 Service 并恢复界面（全新安装不触发，属平台限制）。

## 0.0.1

* TODO: Describe initial release.
