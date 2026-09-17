<#
    快递在哪儿 (kuaidi.qinghan.vip) —— 直接用 Android SDK 命令行工具构建 APK，
    不依赖 Gradle、不需要联网。

    流程：aapt2 compile -> aapt2 link(生成 R.java) -> aapt2 optimize(压缩资源路径)
          -> javac -> d8 -> 注入 classes.dex -> zipalign -> apksigner

    用法：
        powershell -ExecutionPolicy Bypass -File build.ps1
        powershell -ExecutionPolicy Bypass -File build.ps1 -BuildTools 34.0.0 -Platform android-34
#>
[CmdletBinding()]
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$BuildTools = "35.0.0",
    [string]$Platform = "android-35",
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$VersionCode = 3,
    [string]$VersionName = "1.0.2"
)

# 原生工具（javac/d8 等）会往 stderr 写提示，若用 Stop 会被当成致命错误中断，
# 因此统一用 Continue，并在每次调用后显式检查 $LASTEXITCODE。
$ErrorActionPreference = "Continue"

$root    = $PSScriptRoot
$srcDir  = Join-Path $root "app\src\main"
$apkName = "kuaidi-zaina-$VersionName.apk"

function Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Require($path, $what) { if (-not (Test-Path $path)) { throw "$what not found: $path" } }

if (-not $JavaHome) { throw "JAVA_HOME is not set; pass -JavaHome <path to JDK>" }

# 去掉 JVM 的 JAVA_TOOL_OPTIONS 提示，让构建输出干净。
$env:JAVA_TOOL_OPTIONS = $null

$btDir      = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"

Require $btDir      "build-tools directory"
Require $androidJar "android.jar"
Require (Join-Path $JavaHome "bin\javac.exe")   "javac"
Require (Join-Path $JavaHome "bin\keytool.exe") "keytool"

$aapt2     = Join-Path $btDir "aapt2.exe"
$d8        = Join-Path $btDir "d8.bat"
$zipalign  = Join-Path $btDir "zipalign.exe"
$apksigner = Join-Path $btDir "apksigner.bat"
$javac     = Join-Path $JavaHome "bin\javac.exe"
$keytool   = Join-Path $JavaHome "bin\keytool.exe"

foreach ($tool in @($aapt2, $d8, $zipalign, $apksigner)) { Require $tool "tool" }

# ---- 选择 ASCII 构建目录 ----------------------------------------------------
# aapt2.exe 是原生程序，无法枚举含非 ASCII 字符的目录，所以工程路径含中文时
# 先把源码暂存到 ASCII 临时目录再构建，最后把产物复制回 <工程>/build/。
if ($root -match '[^\x00-\x7F]') {
    $work = Join-Path $env:TEMP "kuaidi-zaina-build"
    Step "project path is not ASCII; staging sources to $work"
} else {
    $work = Join-Path $root "build"
}

$stage     = Join-Path $work "src"
$genDir    = Join-Path $work "gen"
$objDir    = Join-Path $work "obj"
$dexDir    = Join-Path $work "dex"
$resZip    = Join-Path $work "res.zip"
$linked    = Join-Path $work "app-linked.apk"
$unAligned = Join-Path $work "app-unsigned.apk"
$aligned   = Join-Path $work "app-aligned.apk"
$finalApk  = Join-Path $work $apkName
# keystore 放在工程根目录（而不是会被清空的 work 目录），保证多次构建签名一致，
# 否则每次构建都会生成新密钥，adb install -r 覆盖安装会报签名不匹配。
$keystore  = Join-Path $root "qinghan.keystore"

Step "cleaning work directory"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work, $genDir, $objDir, $dexDir | Out-Null
Copy-Item -Recurse -Force $srcDir $stage

$resDir   = Join-Path $stage "res"
$javaDir  = Join-Path $stage "java"
$manifest = Join-Path $stage "AndroidManifest.xml"

Step "aapt2 compile resources"
& $aapt2 compile --dir $resDir -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed ($LASTEXITCODE)" }

Step "aapt2 link (generate R.java)"
& $aapt2 link `
    -o $linked `
    -I $androidJar `
    --manifest $manifest `
    -R $resZip `
    --java $genDir `
    --min-sdk-version 23 `
    --target-sdk-version 35 `
    --version-code $VersionCode `
    --version-name $VersionName `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed ($LASTEXITCODE)" }

# 压缩 res/ 下的资源文件名（res/xx.xml），减小体积并轻度混淆。
Step "aapt2 optimize (shorten resource paths)"
& $aapt2 optimize --shorten-resource-paths -o $unAligned $linked
if ($LASTEXITCODE -ne 0) { throw "aapt2 optimize failed ($LASTEXITCODE)" }

Step "javac"
$sources  = @(Get-ChildItem -Recurse -File -Path $javaDir -Filter *.java | ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem -Recurse -File -Path $genDir  -Filter *.java | ForEach-Object { $_.FullName })
& $javac -source 8 -target 8 -encoding UTF-8 -nowarn -classpath $androidJar -d $objDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

Step "d8 (dex)"
$classes = @(Get-ChildItem -Recurse -File -Path $objDir -Filter *.class | ForEach-Object { $_.FullName })
& $d8 --release --min-api 23 --lib $androidJar --output $dexDir @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed ($LASTEXITCODE)" }

Step "packaging classes.dex into APK"
# 注意：PowerShell 5.1 无法在同一个脚本里用 [类型] 字面量引用脚本执行中才 Add-Type
# 进来的程序集，因此这里用 -PassThru 拿到 Type 对象再调用，避免字面量解析失败。
$zipHelper = @'
using System.IO.Compression;

public static class ApkPackage
{
    public static void Inject(string apkPath, string entryName, string sourcePath)
    {
        using (var zip = ZipFile.Open(apkPath, ZipArchiveMode.Update))
        {
            var existing = zip.GetEntry(entryName);
            if (existing != null) { existing.Delete(); }
            ZipFileExtensions.CreateEntryFromFile(
                zip, sourcePath, entryName, CompressionLevel.Optimal);
        }
    }

    public static bool Contains(string apkPath, string entryName)
    {
        using (var zip = ZipFile.OpenRead(apkPath))
        {
            return zip.GetEntry(entryName) != null;
        }
    }
}
'@
$apkPackage = (Add-Type -TypeDefinition $zipHelper `
        -ReferencedAssemblies "System.IO.Compression", "System.IO.Compression.FileSystem" `
        -PassThru)[0]

$apkPackage::Inject($unAligned, "classes.dex", (Join-Path $dexDir "classes.dex"))
if (-not $apkPackage::Contains($unAligned, "classes.dex")) {
    throw "classes.dex was not packaged into the APK"
}

Step "zipalign"
& $zipalign -f -p 4 $unAligned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

if (-not (Test-Path $keystore)) {
    Step "generating keystore"
    & $keytool -genkeypair -v `
        -keystore $keystore `
        -storepass qinghan -keypass qinghan `
        -alias qinghan `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=qinghan, O=qinghan, C=CN" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
}

Step "apksigner"
Remove-Item -Force $finalApk -ErrorAction SilentlyContinue
& $apksigner sign `
    --ks $keystore `
    --ks-pass pass:qinghan `
    --key-pass pass:qinghan `
    --ks-key-alias qinghan `
    --out $finalApk `
    $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed ($LASTEXITCODE)" }

Step "verify signature"
& $apksigner verify --print-certs $finalApk | Select-Object -First 2

# ---- 产物落地 ---------------------------------------------------------------
$outDir = Join-Path $root "build"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$delivered = Join-Path $outDir $apkName
Copy-Item -Force $finalApk $delivered

Write-Host ""
Write-Host ("APK: {0} ({1} bytes)" -f $delivered, (Get-Item $delivered).Length) -ForegroundColor Green
