# [B.M] Minecraft 終界 PLUS

[![Paper](https://img.shields.io/badge/Paper-26.3-2D2D2D)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![GitHub](https://img.shields.io/badge/GitHub-bm--minecraft--the--end--plus-181717?logo=github)](https://github.com/BoringMan314/bm-minecraft-the-end-plus)
[![GitHub all releases](https://img.shields.io/github/downloads/BoringMan314/bm-minecraft-the-end-plus/total)](https://github.com/BoringMan314/bm-minecraft-the-end-plus/releases)
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
- [本機開發與測試](#本機開發與測試)
- [技術概要](#技術概要)
- [專案結構](#專案結構)
- [版本與多語系](#版本與多語系)
- [資料與隱私說明](#資料與隱私說明)
- [維護者：更新 GitHub 與發行版本](#維護者更新-github-與發行版本)
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

### 從 GitHub Releases 安裝

若 [GitHub Releases](https://github.com/BoringMan314/bm-minecraft-the-end-plus/releases) 已提供 JAR，請選擇所需語系下載；尚無發行檔時，可依下方步驟自行建置。

1. 停止伺服器，將所選 JAR 放入 `plugins/` 資料夾。
2. 啟動 **Paper 26.3** 伺服器，確認控制台顯示插件已啟用。
3. 在 `plugins/bm-minecraft-the-end-plus/` 調整設定；指令與設定項目請見下方說明。

> 同一插件只安裝一份語系 JAR；更新時請移除舊版 JAR。

### 從原始碼建置

1. 點選本頁綠色 **Code** → **Download ZIP** 解壓，或執行 `git clone https://github.com/BoringMan314/bm-minecraft-the-end-plus.git`。
2. 依 [本機開發與測試](#本機開發與測試) 準備 JDK 與 Maven，執行建置。
3. 從本機 `dist/` 選取 `bm-minecraft-the-end-plus_26.3_0.0.1-<語系>.jar`，依上方步驟安裝。

---

## 指令與權限

| 指令 | 說明 | 權限 |
|------|------|------|
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

## 本機開發與測試

**Windows / PowerShell：**

1. 準備 **JDK 25**：放在 `.tools/<JDK 資料夾>/`，或安裝至可由 Windows `JavaSoft\JDK\25` 登錄項目辨識的位置。
2. 準備 **Maven**：放在 `.tools/apache-maven-3.9.11/`，或讓 `mvn.cmd` 可從 `PATH` 執行。首次建置需連線下載依賴。
3. 在專案根目錄執行：

```powershell
.\build.bat --no-pause
```

建置完成後，`dist/` 會產生 `zh_TW`、`zh_CN`、`ja_JP`、`en_US` 四份語系 JAR。直接執行 `build.bat` 會在結束時暫停；`--no-pause` 適合終端與自動化使用。

修改 [`src/main/java/`](src/main/java/) 或 [`src/main/resources/`](src/main/resources/) 後，重新建置並更換測試伺服器的 JAR，重新啟動伺服器，驗證 [功能](#功能) 及 [指令與權限](#指令與權限) 中的操作。`dist/`、`target/` 與 `.tools/` 由 [`.gitignore`](.gitignore) 排除，不會隨原始碼上傳。

---

## 技術概要

- **核心實作**：將終界重置流程與管理指令分開處理，並以本機狀態檔保存各世界的重置冷卻。
- **指令與權限**：由 [`plugin.yml`](src/main/resources/plugin.yml) 宣告，實際管理限制由指令處理程式與設定共同決定。
- **建置與語系**：使用 [`pom.xml`](pom.xml) 定義依賴，由 [`build.bat`](build.bat) 依語系逐次執行 Maven 建置。

---

## 專案結構

| 路徑 | 說明 |
|------|------|
| [`pom.xml`](pom.xml) | 插件版本、Java 版本、Paper API 依賴與 Maven 建置設定 |
| [`build.bat`](build.bat) | Windows 四語系 JAR 建置腳本 |
| [`src/main/java/bm.minecraft.the.end.plus/`](src/main/java/bm.minecraft.the.end.plus/) | 插件主類別與功能實作 |
| [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml) | 插件資訊、指令與權限宣告 |
| [`src/main/resources/config.yml`](src/main/resources/config.yml) | 功能與管理設定 |
| [`src/main/resources/active-language.yml`](src/main/resources/active-language.yml) | 建置時套用的預設語系 |
| [`src/main/resources/lang/`](src/main/resources/lang/) | 四種語系的訊息 |
| [`.gitignore`](.gitignore) | 本機工具、暫存與建置產物的排除規則 |

---

## 版本與多語系

- **插件版本**：目前為 `26.3_0.0.1`，建置設定見 [`pom.xml`](pom.xml)。
- **目標 API**：Paper `26.3`；實際依賴版本見 `pom.xml` 的 `paper.version`。
- **預設語系**：`zh_TW`，由 Maven 的 `default.language` 設定。
- **內建語系**：`zh_TW`、`zh_CN`、`ja_JP`、`en_US`（路徑為 `src/main/resources/lang/<語系>.yml`）。
- **語系選擇**：建置腳本將不同預設語系分別打包為 JAR；執行時依 `active-language.yml` 載入對應語系。

---

## 資料與隱私說明

插件只會在 `state.yml` 儲存終界世界名稱與上次成功使用重置指令的時間。不記錄玩家名稱、聊天內容或外部資料。

---

## 維護者：更新 GitHub 與發行版本

### 更新至 GitHub

在專案根目錄執行：

```powershell
git add README.md
git commit -m "V26.3_0.0.1"
git push origin main
```

### 準備發行檔

1. 確認 [`pom.xml`](pom.xml)、[`build.bat`](build.bat) 與 [`plugin.yml`](src/main/resources/plugin.yml) 中的版本設定一致。
2. 執行 `build.bat --no-pause`，並在測試伺服器驗證功能及語系顯示。
3. 在 [GitHub Releases](https://github.com/BoringMan314/bm-minecraft-the-end-plus/releases) 建立對應版本，附上本機 `dist/` 中的四份語系 JAR 與更新說明。

---

## 授權

本專案以 [MIT License](LICENSE) 授權。

---

## 問題與建議

歡迎透過 [GitHub Issues](https://github.com/BoringMan314/bm-minecraft-the-end-plus/issues) 回報錯誤或提出改善建議。回報時請一併提供 Paper 版本、Java 版本、插件版本、**語系**及重現步驟；若有錯誤，請附上相關設定與錯誤日誌。
