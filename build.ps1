cp .\com\dx12\RuntimeHelper.java .\src\main\java\com\dx12
cp .\com\dx12\d3d12_h*.java .\src\main\java\com\dx12
cp .\com\dx12\d3d12sdklayers_h*.java .\src\main\java\com\dx12
cp .\com\dx12\dxgi*.java .\src\main\java\com\dx12
cp .\com\dx12\dxgi1_2_h*.java .\src\main\java\com\dx12
cp .\com\dx12\dxgi1_3_h*.java .\src\main\java\com\dx12
cp .\com\dx12\dxgi1_5_h*.java .\src\main\java\com\dx12
cp .\com\dx12\dxgi1_6_h*.java .\src\main\java\com\dx12
#$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
$jdk = "C:\Program Files\Java\openjdk-17-panama+3-167_windows-x64_bin\jdk-17"
$lib = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\um\x64"
$lib2 = "C:\Windows\System32"
New-Alias -Name java -Value "$jdk\bin\java.exe" -Force
New-Alias -Name javac -Value "$jdk\bin\javac.exe" -Force
New-Alias -Name jlink -Value "$jdk\bin\jlink.exe" -Force
javac --add-modules jdk.incubator.foreign src\main\java\com\dx12\DX12.java src\main\java\com\dx12\WindowProc.java src\main\java\com\dx12\RuntimeHelper.java src\main\java\com\dx12\d3d12*.java src\main\java\com\dx12\dxgi*.java
java "-XX:+CreateCoredumpOnCrash" "-XX:+ShowMessageBoxOnError" -cp src/main/java "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign com.dx12.DX12
# jlink --add-modules jdk.incubator.foreign --output .\build --module-path $jdk\jmods
# javac src\main\java\com\dx12\Windows_h.java
# jar -cf dx12.jar -C com/ dx12
# javac -cp ".;dx12.jar" src\main\java\com\dx12\DX12.java
# java -cp ".;dx12.jar" "-XX:+ShowMessageBoxOnError" "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign com\dx12\DX12