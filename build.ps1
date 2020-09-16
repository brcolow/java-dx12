$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
$lib = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\um\x64"
$lib2 = "C:\Windows\System32"
nal -Name java -Value "$jdk\bin\java.exe"
nal -Name javac -Value "$jdk\bin\javac.exe"
jar -cf dx12.jar -C com/ dx12
javac -cp ".;dx12.jar" -d build .\src\main\java\com\dx12\DX12.java
#java "-XX:+ShowMessageBoxOnError" "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign src\main\java\com\dx12\DX12.java