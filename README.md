## Building Project Panama

Download pre-built LLVM binaries (version 10.0.0 used below) https://releases.llvm.org/download.html and install to `C:\` (spaces in path is an issue).

Make sure autoconf, zip (via sudo apt-get).

From WSL:

```sh
git clone https://github.com/openjdk/panama-foreign
git checkout foreign-jextract
bash configure --with-boot-jdk="/mnt/c/Program Files/Java/jdk-14" --with-libclang="/mnt/c/Program Files/LLVM" --with-clang-version=10.0.0
make
```

## Run jextract on d3d12.h

```powershell
$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
nal -Name jextract -Value "$jdk\bin\jextract.exe"
$I = "C:\Program Files (x86)\Windows Kits\10\Include\10.0.19041.0"
jextract -d out -t com.dx12 -I "$I\um" --filter "d3d12.h" -- "$I\um\d3d12.h"
```

## jextract Arguments

```
Option                         Description
------                         -----------
-?, -h, --help                 print help
-C <String>                    pass through argument for clang
-I <String>                    specify include files path
-d <String>                    specify where to place generated files
--filter <String>              header files to filter
-l <String>                    specify a library
--source                       generate java sources
-t, --target-package <String>  target package for specified header files
```