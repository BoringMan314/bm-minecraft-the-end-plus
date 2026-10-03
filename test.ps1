$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } |
    Select-Object -First 1
if (-not $jdk) { throw 'JDK 25 is required in .tools.' }
$testOutput = Join-Path $projectRoot 'target/regression-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
& (Join-Path $jdk.FullName 'bin/javac.exe') -encoding UTF-8 -d $testOutput `
    (Join-Path $projectRoot 'src/main/java/bm.minecraft.the.end.plus/BmMinecraftTheEndPlusResetFiles.java') `
    (Join-Path $projectRoot 'src/main/java/bm.minecraft.the.end.plus/BmMinecraftTheEndPlusGatewayLinks.java') `
    (Join-Path $projectRoot 'src/test/java/bm.minecraft.the.end.plus/ResetFilesRegression.java') `
    (Join-Path $projectRoot 'src/test/java/bm.minecraft.the.end.plus/GatewayLinksRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'Regression test compilation failed.' }
& (Join-Path $jdk.FullName 'bin/java.exe') -cp $testOutput bm.minecraft.the.end.plus.ResetFilesRegression
if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed.' }
& (Join-Path $jdk.FullName 'bin/java.exe') -cp $testOutput bm.minecraft.the.end.plus.GatewayLinksRegression
if ($LASTEXITCODE -ne 0) { throw 'Gateway link regression tests failed.' }
# Compile the Bukkit boundary test against the same dependencies as the plugin.
$env:JAVA_HOME = $jdk.FullName
& (Join-Path $projectRoot '.tools/apache-maven-3.9.11/bin/mvn.cmd') "-Dmaven.repo.local=$projectRoot/.tools/m2" `
    -f (Join-Path $projectRoot 'pom.xml') test-compile dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' -q
if ($LASTEXITCODE -ne 0) { throw 'Bukkit boundary test compilation failed.' }
$classpath = (Join-Path $projectRoot 'target/test-classes') + ';' + (Join-Path $projectRoot 'target/classes') + ';' + `
    (Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'target/test-classpath.txt')).Trim()
& (Join-Path $jdk.FullName 'bin/java.exe') -cp $classpath bm.minecraft.the.end.plus.GatewayReconnectRegression
if ($LASTEXITCODE -ne 0) { throw 'Gateway reconnect regression tests failed.' }
