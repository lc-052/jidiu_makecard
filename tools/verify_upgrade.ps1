param([string]$Adb = 'D:/sdk/platform-tools/adb.exe')
$ErrorActionPreference = 'Stop'
function Test-Upgrade([string]$Classes, [string]$Report) {
    $result = & $Adb shell am instrument -w -e class $Classes com.yike.ai.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Tee-Object -FilePath $Report
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw "升级验证失败，查看 $Report"
    }
}
& $Adb shell cmd connectivity airplane-mode enable
if ($LASTEXITCODE -ne 0 -or (& $Adb shell settings get global airplane_mode_on) -ne '1') {
    throw '飞行模式没有成功启用，不能把本轮标为离线验证'
}
try {
    Test-Upgrade 'com.yike.ai.AuthoringUpgradeUiTest,com.yike.ai.ProgressUiTest' 'tools/upgrade-final-offline-ui-tests.txt'
} finally {
    & $Adb shell cmd connectivity airplane-mode disable
}
Test-Upgrade 'com.yike.ai.GenerationPerformanceTest' 'tools/upgrade-final-network-tests.txt'
& $Adb pull /sdcard/Android/data/com.yike.ai/files/generation-benchmark.json tools/generation-benchmark.json
if ($LASTEXITCODE -ne 0) { throw '性能记录读取失败' }
