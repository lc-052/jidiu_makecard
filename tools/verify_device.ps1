param([string]$Adb = "adb")
$ErrorActionPreference = "Stop"
function Invoke-DeviceTest([string[]]$TestArguments) {
    $taskOutput = & $Adb shell am instrument -w @TestArguments com.yike.ai.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $taskOutput | Tee-Object -FilePath tools/device-tests.txt -Append
    if ($LASTEXITCODE -ne 0 -or ($taskOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw "设备测试失败，查看 tools/device-tests.txt"
    }
}
& $Adb install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw "Debug APK 安装失败" }
& $Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw "测试 APK 安装失败" }
Invoke-DeviceTest @('-e','class','com.yike.ai.AppFlowTest,com.yike.ai.WorkerAndPdfTest')
Invoke-DeviceTest @('-e','class','com.yike.ai.ProcessPersistenceTest','-e','persistenceStage','seed')
& $Adb shell am force-stop com.yike.ai
& $Adb shell am start -W -n com.yike.ai/.MainActivity
Invoke-DeviceTest @('-e','class','com.yike.ai.ProcessPersistenceTest','-e','persistenceStage','verify')
