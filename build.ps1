$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
$lib = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\um\x64"
$lib2 = "C:\Windows\System32"
nal -Name java -Value "$jdk\bin\java.exe"
java "-Dforeign.restricted=permit" "-Djava.library.path=C:\Windows\System32" --add-modules jdk.incubator.foreign src\main\java\com\dx12\DX12.java