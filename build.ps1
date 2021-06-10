#$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
$jdk = "C:\Program Files\Java\jdk-17"
$lib = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\um\x64"
$lib2 = "C:\Windows\System32"
New-Alias -Name java -Value "$jdk\bin\java.exe" -Force
New-Alias -Name javac -Value "$jdk\bin\javac.exe" -Force
javac --add-modules jdk.incubator.foreign src\main\java\com\dx12\DX12.java src\main\java\com\dx12\WindowProc.java src\main\java\com\dx12\RuntimeHelper.java src\main\java\com\dx12\d3d12*.java src\main\java\com\dx12\dxgi*.java
java "-XX:+CreateCoredumpOnCrash" "-XX:+ShowMessageBoxOnError" -cp src/main/java "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign com.dx12.DX12
# javac src\main\java\com\dx12\Windows_h.java
# jar -cf dx12.jar -C com/ dx12
# javac -cp ".;dx12.jar" src\main\java\com\dx12\DX12.java
# java -cp ".;dx12.jar" "-XX:+ShowMessageBoxOnError" "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign com\dx12\DX12