# [B.M] Minecraft 終界 PLUS

[![Paper](https://img.shields.io/badge/Paper-26.3-2D2D2D)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![GitHub](https://img.shields.io/badge/GitHub-bm--minecraft--the--end--plus-181717?logo=github)](https://github.com/BoringMan314/bm-minecraft-the-end-plus)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

適用於 **Minecraft Paper 26.3** 的插件：以 `/the-end-reset` 依世界種子重置整張終界。

*适用于 **Minecraft Paper 26.3** 的插件：用 `/the-end-reset` 按世界种子重置整张末地。*<br>
*Minecraft Paper 26.3 向け：`/the-end-reset` でジ・エンド全体をシードからリセットします。*<br>
*A **Minecraft Paper 26.3** plugin that resets the entire End from the world seed with `/the-end-reset`.*

> **說明**：僅支援 Paper 26.3 與 Java 25。

---

## 目錄

- [功能](#功能)
- [系統需求](#系統需求)
- [安裝方式](#安裝方式)
- [指令與權限](#指令與權限)
- [設定檔](#設定檔)
- [本機建置](#本機建置)
- [專案結構](#專案結構)
- [版本與多語系](#版本與多語系)
- [資料與隱私說明](#資料與隱私說明)
- [授權](#授權)
- [問題與建議](#問題與建議)

---

## 功能

- 玩家在終界使用 `/the-end-reset` 可依種子重置**整張終界**，包含中央島、外島、終界城、船、終界龍戰鬥狀態與既有建築。
- 重置前會把終界內的玩家傳送回床／重生點或主世界重生點。
- 成功重置後，依 `reset-cooldown-minutes` 限制一般玩家再次使用指令的時間；預設為 30 分鐘。OP 與控制台不受冷卻限制。
- 終界龍死亡後會重建返回傳送門、噴泉上的龍蛋，並生成一座終界折躍門。
- 冷卻時間依世界名稱儲存，伺服器重新啟動後仍然有效。
- 管理員可開關、重載、查看狀態與設定等待分鐘數。

---

## 系統需求

- **Paper 26.3** 伺服器。
- **Java 25**。

---

## 安裝方式

1. 從 [`dist/`](dist/) 選擇所需語系 JAR。
2. 將 JAR 放入 Paper 伺服器的 `plugins/` 資料夾。
3. 啟動伺服器後，設定檔建立於 `plugins/bm-minecraft-the-end-plus/`。

> 請勿同時安裝多個語系 JAR；它們是同一插件的不同預設語言版本。

---

## 指令與權限

| 指令 | 說明 | 權限 |
| --- | --- | --- |
| `/the-end-reset` | 依種子重置目前終界（控制台會重置所有終界世界） | `bm-minecraft-the-end-plus.use` |
| `/bm-minecraft-the-end-plus 0/1` | 關閉／開啟功能 | `.admin` |
| `/bm-minecraft-the-end-plus reload` | 重載設定與語系 | `.admin` |
| `/bm-minecraft-the-end-plus info` | 顯示版本 | `.admin` |
| `/bm-minecraft-the-end-plus status` | 顯示開關與等待時間 | `.admin` |
| `/bm-minecraft-the-end-plus set <分鐘>` | 設定重置指令冷卻分鐘數 | `.admin` |

`bm-minecraft-the-end-plus.use` 預設所有玩家可用；`.admin` 預設 OP 可用。

---

## 設定檔

```yml
enabled: true
reset-cooldown-minutes: 30
admin-require-op: true
restore-first-experience: true
```

- `enabled`：關閉時一般玩家不能重置終界，管理指令仍可用。
- `reset-cooldown-minutes`：成功使用 `/the-end-reset` 後的冷卻時間，範圍 `0` 至 `10080` 分鐘。
- `admin-require-op`：`true` 時遊戲內管理操作需 OP 或 `.admin` 權限；控制台可管理。
- `restore-first-experience`：終界龍死亡後重建返回傳送門、噴泉龍蛋與折躍門。

---

## 本機建置

執行 `build.bat`；預設會在結束時暫停。自動化環境使用：

```bat
build.bat --no-pause
```

會輸出 `zh_TW`、`zh_CN`、`ja_JP`、`en_US` 四個 JAR 至 `dist/`。

---

## 專案結構

```text
src/main/java/bm.minecraft.the.end.plus/
src/main/resources/lang/
src/main/resources/config.yml
src/main/resources/active-language.yml
src/main/resources/plugin.yml
```

---

## 版本與多語系

版本僅在 `pom.xml` 維護，目前為 `26.3_0.0.1`。建置時以 `active-language.yml` 選擇語系；四份語系檔使用完全相同的鍵與 placeholder，例如 `{minutes}`、`{version}`。

---

## 資料與隱私說明

插件只會在 `state.yml` 儲存終界世界名稱與上次成功使用重置指令的時間。不記錄玩家名稱、聊天內容或外部資料。

---

## 授權

本專案以 [MIT License](LICENSE) 授權。

---

## 問題與建議

歡迎透過 [GitHub Issues](https://github.com/BoringMan314/bm-minecraft-the-end-plus/issues) 回報錯誤或提出改善建議。回報時請一併提供 Paper 版本、Java 版本、設定檔與完整錯誤日誌。
