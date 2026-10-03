# [B.M] Minecraft 終界 PLUS

[![Paper](https://img.shields.io/badge/Paper-26.3-2D2D2D)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![GitHub](https://img.shields.io/badge/GitHub-bm--minecraft--the--end--plus-181717?logo=github)](https://github.com/BoringMan314/bm-minecraft-the-end-plus)
[![GitHub all releases](https://img.shields.io/github/downloads/BoringMan314/bm-minecraft-the-end-plus/total)](https://github.com/BoringMan314/bm-minecraft-the-end-plus/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

適用於 **Minecraft Paper 26.3** 的插件：以 `/the-end-reset` 強制重置終界中央四區與龍戰鬥，保留外島。

*适用于 **Minecraft Paper 26.3** 的插件：用 `/the-end-reset` 强制重置末地中央四区与龙战斗，保留外岛。*<br>
*Minecraft Paper 26.3 向け：`/the-end-reset` で中央4リージョンとドラゴン戦をリセットし、外島を保持します。*<br>
*A **Minecraft Paper 26.3** plugin that force-resets the central four End regions and dragon battle while preserving outer islands with `/the-end-reset`.*

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

- 玩家在終界使用 `/the-end-reset`，強制重置 **X、Z 各 -512～511、所有高度**的中央四區及龍戰鬥資料；範圍外的外島、終界城、船、建築與戰利品狀態保留。
- 龍還活著、正在死亡或水晶復活儀式進行中，都能執行重置；強制清龍不等待死亡動畫、不給擊殺獎勵。
- 重置前撤離玩家，暫時阻擋傳送／重生進入重置中的世界，儲存並卸載世界以保留外島最新變更。
- 成功後開始該世界共用的冷卻，預設 30 分鐘；OP、管理權限持有者與控制台可略過。原有使用權限及開關規則保留。
- 指令重置後首次擊殺由原版完整結算；用水晶復活後再次擊殺，預設恢復首次擊殺判定，獲得龍蛋及 12000 經驗（遵守 `mob_drops`），返回門與折躍門也由原版處理，不額外呼叫生成。折躍門仍遵守原版數量上限。
- 冷卻依世界名稱保存，重啟後仍有效。`admin-require-op: false` 會讓所有玩家具備管理資格並略過開關與冷卻限制。

### 重置檔案與失敗處理

使用 Paper 26.3 的 `World.getWorldPath()` 取得該維度實際目錄，並保留原世界名稱與 dimension key。只移除：

- `region`、`entities`、`poi` 內的 `r.-1.-1.mca`、`r.-1.0.mca`、`r.0.-1.mca`、`r.0.0.mca`。
- 上述範圍的外部區塊 `c.<x>.<z>.mcc`（這裡是區塊座標，各 -32～31）。
- `data/minecraft/ender_dragon_fight.dat` 及存在時的 `.dat_old`。

不刪除世界根目錄、世界設定或其他區域。若找不到 26.3 的龍戰鬥檔，拒絕刪除地形並嘗試重載原世界，不猜測舊版路徑。

刪除前先備份全部目標檔案至插件資料夾的 `reset-backups/<時間戳>-<UUID>/`。備份失敗不刪除；刪除或重載失敗會嘗試卸載失敗的新世界、還原目標檔案並重載原世界，不記錄成功冷卻。回復也失敗時保留阻擋狀態並在控制台列出備份位置。備份是在強制清龍、儲存卸載後建立，不包含已清除的龍實體。

備份中的 `dimension.txt` 記錄來源目錄，`status.txt` 記錄進度。備份不自動刪除；請依容量需求管理。程序崩潰或斷電不保證自動還原；若留下 `resetting` 狀態，需停服檢查並由備份恢復，不能直接當作已完成。檔案操作與世界載入在主執行緒執行，中央區域資料量大時可能短暫停頓。

中央折躍門與生成進度會隨指令重置，讓門體可以重新生成。首次原版擊殺、指令重置後擊殺，以及放置終界水晶復活後擊殺，仍由原版決定新門的位置與數量；插件不在殺龍時額外生成第二座門。

原版中央 20 個位置的往返配對會另外儲存在插件資料夾的 `gateway-links.properties`，不隨指令重置刪除。首次使用時記錄原版選出的外島門；重置前也會讀取中央舊門與已載入的外島門。再次進入門時，先接回原本目的地，避免原版另外搜尋並生成鄰近的外島門。有紀錄的配對若有門體或基岩框架損壞，會在原座標修復；從外島返回時也能修復尚未重建的中央配對門。修復不消耗原版的新門生成次數。

連結紀錄寫入失敗時不開始指令重置。安裝此版本之前就已遺失、且沒有任何可讀取對向門資料的連結，無法憑空還原；自訂位置或自訂單向傳送門不納入配對。請保留 `gateway-links.properties`，並在實服驗證往返、破壞後修復及重複重置。

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
| `/the-end-reset` | 強制重置目前終界中央四區（控制台處理所有已載入終界） | `bm-minecraft-the-end-plus.use` |
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
- `restore-first-experience`：水晶復活後再次擊殺龍時恢復首次擊殺判定與經驗；門、蛋、折躍門由原版結算。關閉時採原版重複擊殺規則。

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

檔案重置回歸測試：執行 `./test.ps1`。使用暫存檔驗證中央四區與 `.mcc` 邊界、外島與設定保持位元組一致、備份失敗不刪除、部分刪除失敗後還原，以及未知格式與不安全路徑拒絕處理。此測試不需要 Minecraft 伺服器，也不會讀取正式世界。

上線前仍需在 Paper 26.3 測試服驗證：

1. 龍存活、死亡動畫中、水晶復活儀式中，各執行一次重置；玩家撤離、舊龍消失、中央重建，外島新放置的方塊和容器保持原樣。
2. 重置後首次殺龍：返回門開啟、一顆龍蛋、原版首次經驗、只新增一座折躍門。
3. 用水晶復活再殺龍：再次有龍蛋與 12000 經驗，仍只新增一座折躍門（未達原版上限時）。
4. 關閉 `restore-first-experience` 時回到原版重複擊殺；關閉 `mob_drops` 時不發經驗。
5. 測試取消傳送／卸載的其他插件、冷卻與重新啟動，以及首次擊殺、指令重置後擊殺、水晶復活後擊殺的門。記錄雙端座標，確認重複重置仍回到同一外島門、破壞門體或框架後會原地修復，且重啟後配對仍有效。

---

## 技術概要

- **核心實作**：將終界重置流程與管理指令分開處理，並以本機狀態檔保存各世界的重置冷卻。
- **龍死亡結算**：依 Paper 的 [龍死亡實作](https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java.patch)，在死亡事件調整首次判定及經驗，移除手動重建門、放蛋、生成第二座折躍門的流程。
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

插件在 `state.yml` 儲存終界世界名稱與上次成功重置時間，也在 `reset-backups/` 保存被重置區域與龍戰鬥資料的備份（可能包含建築、容器及實體資料）。不另外記錄聊天內容或傳送資料至外部服務。

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
