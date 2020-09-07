## Current Status

Failing with error:

java.lang.RuntimeException: C:\Program Files (x86)\Windows Kits\10\Include\10.0.17763.0\um/d3d12sdklayers.h:2730:15: error: unknown type name 'D3D12_MESSAGE_ID_GPU_BASED_VALIDATION_UNSUPPORTED'

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