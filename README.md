## Panama Dev Jorn Vernee jextract

```powershell
$jdk = <path to built jdk>
nal -Name jextract -Value "$jdk\bin\jextract.exe"
$I = "C:\Program Files (x86)\Windows Kits\10\Include\10.0.18362.0"
jextract -d out -t org.jextract -I "$I\um" --filter "d3d12.h" -- "$I\um\d3d12.h"
```

## Current Status

Failing with error:

java.lang.RuntimeException: C:\Program Files (x86)\Windows Kits\10\Include\10.0.17763.0\um/d3d12sdklayers.h:2730:15: error: unknown type name 'D3D12_MESSAGE_ID_GPU_BASED_VALIDATION_UNSUPPORTED'

## Building Project Panama

From WSL:

Download pre-built LLVM binaries (version 10.0.0 used below) https://releases.llvm.org/download.html and install to `C:\` (spaces in path is an issue).

Make sure autoconf, zip (via sudo apt-get).

```sh
bash configure --with-boot-jdk="/mnt/c/Program Files/Java/jdk-14" --with-libclang="/mnt/c/Program Files/LLVM" --with-clang-version=10.0.0
make
```


## jextract Arguments

```
Option                         Description
------                         -----------
-?, -h, --help                 print help
-C <String>                    pass through argument for clang
-I <String>                    specify include files path
-L, --library-path <String>    specify library path
-d <String>                    specify where to place generated class files
--dry-run                      parse header files but do not generate output jar
--exclude-headers <String>     exclude the headers matching the given pattern
--exclude-symbols <String>     exclude the symbols matching the given pattern
--include-headers <String>     include the headers matching the given pattern.
                                 If both --include-headers and --exclude-
                                 headers are specified, --include-headers are
                                 considered first.
--include-symbols <String>     include the symbols matching the given pattern.
                                 If both --include-symbols and --exclude-
                                 symbols are specified, --include-symbols are
                                 considered first.
-l <String>                    specify a library
--log <String>                 specify log level in java.util.logging.Level name
--missing-symbols <String>     action on missing native symbols. --
                                 missing_symbols=error|exclude|ignore|warn
--no-locations                 do not generate native location information in
                                 the .class files
-o <String>                    specify output jar file or jmod file
--package-map <String>         specify package mapping as dir=pkg
--record-library-path          tells whether to record library search path in
                                 the .class files
--src-dump-dir <String>        specify output source dump directory
--static-forwarder <Boolean>   generate static forwarder class (default is true)
-t, --target-package <String>  target package for specified header files
```