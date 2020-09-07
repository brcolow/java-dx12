# http://hg.openjdk.java.net/panama/dev/raw-file/841483f2887f/doc/panama_foreign.html#windows-notes
$inc ="C:\Program Files (x86)\Windows Kits\10\Include\10.0.17763.0"
$panamaHome = "C:\Program Files\Java\jdk-14-panama"
& $panamaHome\bin\jextract -J-Xmx8G -L C:\Windows\System32\ -I $inc\um -l d3d12 -o d3d12.jar --record-library-path $inc\um\d3d12.h
