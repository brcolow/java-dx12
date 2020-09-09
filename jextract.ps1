# http://hg.openjdk.java.net/panama/dev/raw-file/foreign/doc/panama_foreign.html#hello-world-c-header-helloworld.h
$inc ="C:\Program Files (x86)\Windows Kits\10\Include\10.0.17763.0"
$lib = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.17763.0"
$panamaHome = "C:\Program Files\Java\jdk-14-panama"
& $panamaHome\bin\jextract -J"-Djextract.log.cursors=true" -J"-Djextract.debug=true" -J-Xmx8G --log FINEST -L "C:\Windows\System32" -L "$lib\um\x64" -I "$inc\um" -l d3d12 -o d3d12.jar --record-library-path $inc\um\d3d12.h
